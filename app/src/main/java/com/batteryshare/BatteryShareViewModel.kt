package com.batteryshare

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteryshare.battery.BatteryMonitor
import com.batteryshare.connection.P2PManager
import com.batteryshare.model.ConnectionStatus
import com.batteryshare.model.DeviceState
import com.batteryshare.model.Role
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BatteryShareViewModel(app: Application) : AndroidViewModel(app) {

    val p2pManager = P2PManager(app)
    private val batteryMonitor = BatteryMonitor(app)

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbar: SharedFlow<String> = _snackbar

    private var p2pInitialized = false

    init {
        // Battery monitoring
        viewModelScope.launch {
            batteryMonitor.observeBattery().collect { info ->
                _state.update { it.copy(batteryLevel = info.level, isCharging = info.isCharging) }
                val s = _state.value
                if (s.connectionStatus == ConnectionStatus.CONNECTED ||
                    s.connectionStatus == ConnectionStatus.TRANSFERRING
                ) {
                    p2pManager.sendBatteryLevel(info.level)
                }
            }
        }

        // P2P status
        viewModelScope.launch {
            p2pManager.status.collect { status ->
                _state.update { prev ->
                    val since = if (status == ConnectionStatus.CONNECTED && prev.connectionStatus != ConnectionStatus.CONNECTED && prev.connectionStatus != ConnectionStatus.TRANSFERRING) {
                        SystemClock.elapsedRealtime()
                    } else prev.connectedSince
                    prev.copy(connectionStatus = status, connectedSince = since)
                }
            }
        }
        viewModelScope.launch {
            p2pManager.peerBattery.collect { level ->
                _state.update { it.copy(peerBatteryLevel = level) }
            }
        }
        viewModelScope.launch {
            p2pManager.peerName.collect { name ->
                _state.update { it.copy(peerDeviceName = name) }
            }
        }

        // P2P errors → snackbar
        viewModelScope.launch {
            p2pManager.errors.collect { msg ->
                _state.update { it.copy(errorMessage = msg) }
                _snackbar.tryEmit(msg)
            }
        }

        // Initial battery
        val info = batteryMonitor.getBatteryInfo()
        _state.update { it.copy(batteryLevel = info.level, isCharging = info.isCharging) }
    }

    fun initP2P() {
        if (!p2pInitialized) {
            p2pManager.initialize()
            p2pInitialized = true
        }
    }

    fun setRole(role: Role) {
        _state.update { it.copy(role = role) }
        // Auto-scan when a role is selected
        if (role != Role.NONE && p2pInitialized) {
            p2pManager.discoverPeers()
        }
    }

    fun setPermissionsDenied() {
        _state.update { it.copy(permissionsDenied = true) }
        _snackbar.tryEmit("Permissions requises pour Wi-Fi Direct")
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun disconnect() {
        p2pManager.disconnect()
        _state.update {
            it.copy(
                role = Role.NONE,
                peerBatteryLevel = -1,
                peerDeviceName = "",
                connectedSince = 0L
            )
        }
    }

    override fun onCleared() {
        p2pManager.destroy()
    }
}
