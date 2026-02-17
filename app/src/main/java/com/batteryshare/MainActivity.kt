package com.batteryshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryshare.battery.BatteryMonitor
import com.batteryshare.connection.P2PManager
import com.batteryshare.model.ConnectionStatus
import com.batteryshare.model.DeviceState
import com.batteryshare.model.Role
import com.batteryshare.ui.screens.HomeScreen
import com.batteryshare.ui.theme.BatteryShareTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var p2pManager: P2PManager
    private lateinit var batteryMonitor: BatteryMonitor
    private val deviceState = MutableStateFlow(DeviceState())
    private var batteryJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            initP2P()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        batteryMonitor = BatteryMonitor(this)
        p2pManager = P2PManager(this)

        // Start battery monitoring
        batteryJob = MainScope().launch {
            batteryMonitor.observeBattery().collect { info ->
                deviceState.value = deviceState.value.copy(
                    batteryLevel = info.level,
                    isCharging = info.isCharging
                )
                // Send battery level to peer if connected
                if (deviceState.value.connectionStatus == ConnectionStatus.CONNECTED ||
                    deviceState.value.connectionStatus == ConnectionStatus.TRANSFERRING
                ) {
                    p2pManager.sendBatteryLevel(info.level)
                }
            }
        }

        // Collect P2P state
        MainScope().launch {
            launch {
                p2pManager.status.collect { status ->
                    deviceState.value = deviceState.value.copy(connectionStatus = status)
                }
            }
            launch {
                p2pManager.peerBattery.collect { level ->
                    deviceState.value = deviceState.value.copy(peerBatteryLevel = level)
                }
            }
            launch {
                p2pManager.peerName.collect { name ->
                    deviceState.value = deviceState.value.copy(peerDeviceName = name)
                }
            }
        }

        checkPermissionsAndInit()

        setContent {
            BatteryShareTheme {
                val state by deviceState.collectAsStateWithLifecycle()
                val peers by p2pManager.peers.collectAsStateWithLifecycle()

                HomeScreen(
                    state = state,
                    peers = peers,
                    onRoleSelected = { role ->
                        deviceState.value = deviceState.value.copy(role = role)
                    },
                    onScanClicked = {
                        p2pManager.discoverPeers()
                    },
                    onPeerSelected = { device ->
                        p2pManager.connectToPeer(device)
                    },
                    onDisconnect = {
                        p2pManager.disconnect()
                        deviceState.value = deviceState.value.copy(
                            role = Role.NONE,
                            peerBatteryLevel = -1,
                            peerDeviceName = ""
                        )
                    }
                )
            }
        }
    }

    private fun checkPermissionsAndInit() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            initP2P()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun initP2P() {
        p2pManager.initialize()
        // Set initial battery
        val info = batteryMonitor.getBatteryInfo()
        deviceState.value = deviceState.value.copy(
            batteryLevel = info.level,
            isCharging = info.isCharging
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryJob?.cancel()
        p2pManager.destroy()
    }
}
