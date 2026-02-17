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
import kotlinx.coroutines.flow.MutableStateFlow
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

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var outputStream: DataOutputStream? = null
    private var isGroupOwner = false
    private var receiver: BroadcastReceiver? = null

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
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
                _status.value = ConnectionStatus.DISCONNECTED
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
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
                serverSocket = ServerSocket(PORT)
                serverSocket?.soTimeout = 30_000
                val client = serverSocket?.accept() ?: return@launch
                socket = client
                setupStreams(client)
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                _status.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun connectToServer(host: String) {
        scope.launch {
            try {
                val client = Socket()
                client.connect(InetSocketAddress(host, PORT), 10_000)
                socket = client
                setupStreams(client)
            } catch (e: Exception) {
                Log.e(TAG, "Client error", e)
                _status.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun setupStreams(socket: Socket) {
        outputStream = DataOutputStream(socket.getOutputStream())
        _status.value = ConnectionStatus.CONNECTED

        // Read loop
        scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (isActive) {
                    val msgType = input.readInt()
                    if (msgType == MSG_BATTERY) {
                        val level = input.readInt()
                        _peerBattery.value = level
                        if (_status.value == ConnectionStatus.CONNECTED) {
                            _status.value = ConnectionStatus.TRANSFERRING
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                _status.value = ConnectionStatus.DISCONNECTED
                _peerBattery.value = -1
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
