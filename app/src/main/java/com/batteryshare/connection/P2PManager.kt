package com.batteryshare.connection

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.batteryshare.model.ConnectionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

@SuppressLint("MissingPermission")
class P2PManager(private val context: Context) {

    companion object {
        private const val TAG = "P2PManager"
        private const val PORT = 8923
        private const val MSG_BATTERY = 1
        private const val MSG_PING = 2
        private const val PING_INTERVAL_MS = 5_000L
    }

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status

    private val _peerBattery = MutableStateFlow(-1)
    val peerBattery: StateFlow<Int> = _peerBattery

    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var outputStream: DataOutputStream? = null
    private var isGroupOwner = false
    private var receiver: BroadcastReceiver? = null
    private var pingJob: Job? = null
    private var discoveryJob: Job? = null

    fun initialize() {
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
        registerReceiver()
    }

    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        )
                        if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            _status.value = ConnectionStatus.DISCONNECTED
                            _errors.tryEmit("Wi-Fi est d\u00e9sactiv\u00e9")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager?.requestPeers(channel) { peerList ->
                            _peers.value = peerList.deviceList.toList()
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager?.requestConnectionInfo(channel) { info ->
                            handleConnectionInfo(info)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun discoverPeers() {
        _status.value = ConnectionStatus.SEARCHING
        _peers.value = emptyList()

        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
                discoveryJob?.cancel()
                discoveryJob = scope.launch {
                    delay(15_000)
                    if (_status.value == ConnectionStatus.SEARCHING) {
                        withContext(Dispatchers.Main) { discoverPeers() }
                    }
                }
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
                _status.value = ConnectionStatus.DISCONNECTED
                val msg = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct non support\u00e9"
                    WifiP2pManager.BUSY -> "Wi-Fi Direct occup\u00e9, r\u00e9essayez"
                    WifiP2pManager.ERROR -> "Erreur Wi-Fi Direct"
                    else -> "Recherche \u00e9chou\u00e9e"
                }
                _errors.tryEmit(msg)
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        discoveryJob?.cancel()
        _status.value = ConnectionStatus.CONNECTING
        _peerName.value = device.deviceName.ifEmpty { "Appareil" }

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed: $reason")
                _status.value = ConnectionStatus.DISCONNECTED
                _errors.tryEmit("Connexion \u00e9chou\u00e9e, r\u00e9essayez")
            }
        })
    }

    private fun handleConnectionInfo(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return

        isGroupOwner = info.isGroupOwner
        _status.value = ConnectionStatus.CONNECTED

        if (isGroupOwner) {
            startServer()
        } else {
            val hostAddress = info.groupOwnerAddress?.hostAddress ?: return
            connectToServer(hostAddress)
        }
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(PORT).apply { reuseAddress = true }
                serverSocket?.soTimeout = 30_000
                val client = serverSocket?.accept() ?: return@launch
                socket = client
                setupStreams(client)
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                _status.value = ConnectionStatus.DISCONNECTED
                _errors.tryEmit("Erreur de connexion serveur")
            }
        }
    }

    private fun connectToServer(host: String) {
        scope.launch {
            var attempt = 0
            while (attempt < 3) {
                try {
                    val client = Socket()
                    client.connect(InetSocketAddress(host, PORT), 10_000)
                    socket = client
                    setupStreams(client)
                    return@launch
                } catch (e: Exception) {
                    attempt++
                    Log.e(TAG, "Client connect attempt $attempt failed", e)
                    if (attempt < 3) delay(1000L * attempt) else {
                        _status.value = ConnectionStatus.DISCONNECTED
                        _errors.tryEmit("Impossible de se connecter")
                    }
                }
            }
        }
    }

    private fun setupStreams(socket: Socket) {
        outputStream = DataOutputStream(socket.getOutputStream())
        _status.value = ConnectionStatus.CONNECTED

        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                try {
                    outputStream?.writeInt(MSG_PING)
                    outputStream?.flush()
                } catch (_: Exception) { break }
            }
        }

        scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (isActive) {
                    val msgType = input.readInt()
                    when (msgType) {
                        MSG_BATTERY -> {
                            val level = input.readInt()
                            _peerBattery.value = level
                            if (_status.value == ConnectionStatus.CONNECTED) {
                                _status.value = ConnectionStatus.TRANSFERRING
                            }
                        }
                        MSG_PING -> { /* keepalive */ }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                _status.value = ConnectionStatus.DISCONNECTED
                _peerBattery.value = -1
                _errors.tryEmit("Connexion perdue")
            }
        }
    }

    fun sendBatteryLevel(level: Int) {
        scope.launch {
            try {
                outputStream?.writeInt(MSG_BATTERY)
                outputStream?.writeInt(level)
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Send error", e)
            }
        }
    }

    fun disconnect() {
        pingJob?.cancel()
        discoveryJob?.cancel()

        scope.launch {
            try {
                outputStream?.close()
                socket?.close()
                serverSocket?.close()
            } catch (_: Exception) {}

            outputStream = null
            socket = null
            serverSocket = null
        }

        manager?.removeGroup(channel, null)
        manager?.stopPeerDiscovery(channel, null)
        _status.value = ConnectionStatus.DISCONNECTED
        _peerBattery.value = -1
        _peerName.value = ""
        _peers.value = emptyList()
    }

    fun destroy() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        disconnect()
        scope.cancel()
    }
}
