package com.batteryshare.ui.screens

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryshare.model.ConnectionStatus
import com.batteryshare.model.DeviceState
import com.batteryshare.model.Role

@Composable
fun HomeScreen(
    state: DeviceState,
    onRoleSelected: (Role) -> Unit,
    onScanClicked: () -> Unit,
    onPeerSelected: (WifiP2pDevice) -> Unit,
    onDisconnect: () -> Unit,
    peers: List<WifiP2pDevice>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Title
        Text(
            text = "Battery Share",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(40.dp))

        // Battery circle
        BatteryCircle(
            level = state.batteryLevel,
            isTransferring = state.connectionStatus == ConnectionStatus.TRANSFERRING,
            role = state.role
        )

        Spacer(Modifier.height(12.dp))

        // Status text
        StatusText(state)

        Spacer(Modifier.height(32.dp))

        when {
            // Not connected - show role selection
            state.connectionStatus == ConnectionStatus.DISCONNECTED && state.role == Role.NONE -> {
                RoleSelector(onRoleSelected)
            }
            // Role selected, searching or idle
            state.connectionStatus == ConnectionStatus.DISCONNECTED ||
            state.connectionStatus == ConnectionStatus.SEARCHING -> {
                ScanSection(
                    status = state.connectionStatus,
                    role = state.role,
                    peers = peers,
                    onScanClicked = onScanClicked,
                    onPeerSelected = onPeerSelected,
                    onBack = { onRoleSelected(Role.NONE) }
                )
            }
            // Connecting
            state.connectionStatus == ConnectionStatus.CONNECTING -> {
                ConnectingView()
            }
            // Connected or transferring
            state.connectionStatus == ConnectionStatus.CONNECTED ||
            state.connectionStatus == ConnectionStatus.TRANSFERRING -> {
                ConnectedView(
                    state = state,
                    onDisconnect = onDisconnect
                )
            }
        }
    }
}

@Composable
private fun BatteryCircle(level: Int, isTransferring: Boolean, role: Role) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val batteryColor = when {
        level > 60 -> Color(0xFF00E676)
        level > 20 -> Color(0xFFFFD600)
        else -> Color(0xFFFF5252)
    }

    val glowColor = if (isTransferring) {
        when (role) {
            Role.SENDER -> Color(0xFFFF5252)
            Role.RECEIVER -> Color(0xFF00E676)
            Role.NONE -> batteryColor
        }
    } else batteryColor

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val sweep = 360f * (level / 100f)
            val strokeWidth = 12.dp.toPx()

            // Background arc
            drawArc(
                color = Color(0xFF2A2A2A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth)
            )

            // Battery arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(glowColor.copy(alpha = 0.6f), glowColor)
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth)
            )

            // Glow effect when transferring
            if (isTransferring) {
                drawArc(
                    color = glowColor.copy(alpha = pulseAlpha * 0.3f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2 - 4.dp.toPx(), strokeWidth / 2 - 4.dp.toPx()),
                    size = Size(
                        size.width - strokeWidth + 8.dp.toPx(),
                        size.height - strokeWidth + 8.dp.toPx()
                    )
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$level%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (isTransferring) {
                val arrow = if (role == Role.SENDER) "ENVOI" else "RECHARGE"
                Text(
                    text = arrow,
                    fontSize = 12.sp,
                    color = glowColor.copy(alpha = pulseAlpha),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun StatusText(state: DeviceState) {
    val text = when (state.connectionStatus) {
        ConnectionStatus.DISCONNECTED -> {
            if (state.isCharging) "En charge" else "Sur batterie"
        }
        ConnectionStatus.SEARCHING -> "Recherche d'appareils..."
        ConnectionStatus.CONNECTING -> "Connexion..."
        ConnectionStatus.CONNECTED -> "Connect\u00e9 \u00e0 ${state.peerDeviceName}"
        ConnectionStatus.TRANSFERRING -> {
            val peer = if (state.peerBatteryLevel >= 0) " (${state.peerBatteryLevel}%)" else ""
            when (state.role) {
                Role.SENDER -> "Envoi vers ${state.peerDeviceName}$peer"
                Role.RECEIVER -> "Re\u00e7oit de ${state.peerDeviceName}$peer"
                Role.NONE -> "Connect\u00e9"
            }
        }
    }

    val color by animateColorAsState(
        targetValue = when (state.connectionStatus) {
            ConnectionStatus.TRANSFERRING -> MaterialTheme.colorScheme.primary
            ConnectionStatus.CONNECTED -> Color(0xFF69F0AE)
            ConnectionStatus.SEARCHING, ConnectionStatus.CONNECTING -> Color(0xFFFFD600)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "statusColor"
    )

    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RoleSelector(onRoleSelected: (Role) -> Unit) {
    Text(
        text = "Que voulez-vous faire ?",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
    )

    Spacer(Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Give charge button
        Button(
            onClick = { onRoleSelected(Role.SENDER) },
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DONNER", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                Text("ma charge", fontSize = 11.sp, color = Color(0xFF999999))
            }
        }

        // Receive charge button
        Button(
            onClick = { onRoleSelected(Role.RECEIVER) },
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RECEVOIR", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                Text("de la charge", fontSize = 11.sp, color = Color(0xFF999999))
            }
        }
    }
}

@Composable
private fun ScanSection(
    status: ConnectionStatus,
    role: Role,
    peers: List<WifiP2pDevice>,
    onScanClicked: () -> Unit,
    onPeerSelected: (WifiP2pDevice) -> Unit,
    onBack: () -> Unit
) {
    val roleLabel = if (role == Role.SENDER) "Donneur" else "Receveur"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("Retour", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Text(
            text = "Mode: $roleLabel",
            color = if (role == Role.SENDER) Color(0xFFFF5252) else Color(0xFF00E676),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = onScanClicked,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp),
        enabled = status != ConnectionStatus.SEARCHING
    ) {
        Text(
            text = if (status == ConnectionStatus.SEARCHING) "Recherche..." else "Scanner",
            color = Color.Black,
            fontWeight = FontWeight.Bold
        )
    }

    if (peers.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Appareils trouv\u00e9s",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(peers) { device ->
                PeerItem(device = device, onClick = { onPeerSelected(device) })
            }
        }
    } else if (status == ConnectionStatus.SEARCHING) {
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun PeerItem(device: WifiP2pDevice, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676))
            )
            Text(
                text = device.deviceName.ifEmpty { device.deviceAddress },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ConnectingView() {
    Spacer(Modifier.height(24.dp))
    CircularProgressIndicator(
        modifier = Modifier.size(32.dp),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 3.dp
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Connexion en cours...",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
    )
}

@Composable
private fun ConnectedView(state: DeviceState, onDisconnect: () -> Unit) {
    if (state.peerBatteryLevel >= 0) {
        Spacer(Modifier.height(8.dp))

        // Peer battery mini indicator
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = state.peerDeviceName.ifEmpty { "Pair" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Batterie distante",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "${state.peerBatteryLevel}%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        state.peerBatteryLevel > 60 -> Color(0xFF00E676)
                        state.peerBatteryLevel > 20 -> Color(0xFFFFD600)
                        else -> Color(0xFFFF5252)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Transfer direction indicator
        val arrow = if (state.role == Role.SENDER) "Vous  -->  ${state.peerDeviceName}" else "${state.peerDeviceName}  -->  Vous"
        Text(
            text = arrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFFFF5252)
        )
    ) {
        Text("D\u00e9connecter", fontWeight = FontWeight.Medium)
    }
}
