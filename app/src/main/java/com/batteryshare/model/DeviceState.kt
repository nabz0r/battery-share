package com.batteryshare.model

enum class Role {
    NONE,
    SENDER,
    RECEIVER
}

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    TRANSFERRING
}

data class DeviceState(
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val role: Role = Role.NONE,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val peerBatteryLevel: Int = -1,
    val peerDeviceName: String = "",
    val errorMessage: String? = null,
    val permissionsDenied: Boolean = false,
    val connectedSince: Long = 0L
)
