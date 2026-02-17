package com.batteryshare.ui.screens

import android.net.wifi.p2p.WifiP2pDevice
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryshare.model.ConnectionStatus
import com.batteryshare.model.DeviceState
import com.batteryshare.model.Role
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HomeScreen(
    state: DeviceState,
    onRoleSelected: (Role) -> Unit,
    onScanClicked: () -> Unit,
    onPeerSelected: (WifiP2pDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDismissError: () -> Unit,
    peers: List<WifiP2pDevice>,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2A2A2A),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Title with subtle animation
            Text(
                text = "Battery Share",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(8.dp))

            // Version tag
            Text(
                text = "v1.1",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(32.dp))

            // Battery circle with particles
            BatteryCircle(
                level = state.batteryLevel,
                isTransferring = state.connectionStatus == ConnectionStatus.TRANSFERRING,
                role = state.role
            )

            Spacer(Modifier.height(12.dp))

            StatusText(state)

            // Connection timer
            if (state.connectedSince > 0L &&
                (state.connectionStatus == ConnectionStatus.CONNECTED ||
                 state.connectionStatus == ConnectionStatus.TRANSFERRING)
            ) {
                Spacer(Modifier.height(4.dp))
                ConnectionTimer(since = state.connectedSince)
            }

            Spacer(Modifier.height(28.dp))

            // Permission denied state
            if (state.permissionsDenied) {
                PermissionDeniedView()
            } else {
                AnimatedContent(
                    targetState = screenKey(state),
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "screenTransition"
                ) { key ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (key) {
                            "role" -> RoleSelector(onRoleSelected)
                            "scan" -> ScanSection(
                                status = state.connectionStatus,
                                role = state.role,
                                peers = peers,
                                onScanClicked = onScanClicked,
                                onPeerSelected = onPeerSelected,
                                onBack = { onRoleSelected(Role.NONE) }
                            )
                            "connecting" -> ConnectingView()
                            "connected" -> ConnectedView(state = state, onDisconnect = onDisconnect)
                        }
                    }
                }
            }
        }
    }
}

private fun screenKey(state: DeviceState): String = when {
    state.connectionStatus == ConnectionStatus.DISCONNECTED && state.role == Role.NONE -> "role"
    state.connectionStatus == ConnectionStatus.DISCONNECTED ||
    state.connectionStatus == ConnectionStatus.SEARCHING -> "scan"
    state.connectionStatus == ConnectionStatus.CONNECTING -> "connecting"
    else -> "connected"
}

// ─── Battery Circle ──────────────────────────────────────────────

@Composable
private fun BatteryCircle(level: Int, isTransferring: Boolean, role: Role) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
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

    val animatedLevel by animateFloatAsState(
        targetValue = level.toFloat(),
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "level"
    )

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val sweep = 360f * (animatedLevel / 100f)
            val strokeWidth = 14.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val arcTopLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background arc
            drawArc(
                color = Color(0xFF1E1E1E),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = arcTopLeft,
                size = arcSize
            )

            // Battery arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.4f),
                        glowColor.copy(alpha = 0.7f),
                        glowColor
                    )
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = arcTopLeft,
                size = arcSize
            )

            // Glow effect when transferring
            if (isTransferring) {
                drawArc(
                    color = glowColor.copy(alpha = pulseAlpha * 0.25f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 12.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(arcTopLeft.x - 6.dp.toPx(), arcTopLeft.y - 6.dp.toPx()),
                    size = Size(arcSize.width + 12.dp.toPx(), arcSize.height + 12.dp.toPx())
                )

                // Energy particles orbiting
                val cx = size.width / 2
                val cy = size.height / 2
                val radius = (size.width - strokeWidth) / 2
                for (i in 0..5) {
                    val angle = Math.toRadians((rotationAngle + i * 60.0).toDouble())
                    val px = cx + radius * cos(angle).toFloat()
                    val py = cy + radius * sin(angle).toFloat()
                    drawCircle(
                        color = glowColor.copy(alpha = pulseAlpha * 0.6f),
                        radius = 3.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$level",
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-2).sp
            )
            Text(
                text = "%",
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.offset(y = (-8).dp)
            )
            if (isTransferring) {
                Spacer(Modifier.height(2.dp))
                val label = if (role == Role.SENDER) "ENVOI" else "RECHARGE"
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = glowColor.copy(alpha = pulseAlpha),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}

// ─── Status ──────────────────────────────────────────────────────

@Composable
private fun StatusText(state: DeviceState) {
    val text = when (state.connectionStatus) {
        ConnectionStatus.DISCONNECTED -> {
            if (state.isCharging) "En charge" else "Sur batterie"
        }
        ConnectionStatus.SEARCHING -> "Recherche d'appareils..."
        ConnectionStatus.CONNECTING -> "Connexion en cours..."
        ConnectionStatus.CONNECTED -> "Connect\u00e9 \u00e0 ${state.peerDeviceName}"
        ConnectionStatus.TRANSFERRING -> {
            val peer = if (state.peerBatteryLevel >= 0) " \u2022 ${state.peerBatteryLevel}%" else ""
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
        animationSpec = tween(300),
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
private fun ConnectionTimer(since: Long) {
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(since) {
        while (true) {
            elapsed = (SystemClock.elapsedRealtime() - since) / 1000
            kotlinx.coroutines.delay(1000)
        }
    }

    val mins = elapsed / 60
    val secs = elapsed % 60
    Text(
        text = "Connect\u00e9 depuis %d:%02d".format(mins, secs),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

// ─── Permission Denied ──────────────────────────────────────────

@Composable
private fun PermissionDeniedView() {
    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Permissions requises",
                color = Color(0xFFFF5252),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "L'application a besoin de la permission de localisation pour d\u00e9couvrir les appareils via Wi-Fi Direct.\n\nAllez dans Param\u00e8tres > Applications > Battery Share > Permissions pour les activer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ─── Role Selector ──────────────────────────────────────────────

@Composable
private fun RoleSelector(onRoleSelected: (Role) -> Unit) {
    val view = LocalView.current

    Text(
        text = "Que voulez-vous faire ?",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
    )

    Spacer(Modifier.height(20.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        RoleButton(
            label = "DONNER",
            subtitle = "ma charge",
            color = Color(0xFFFF5252),
            modifier = Modifier.weight(1f),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onRoleSelected(Role.SENDER)
            }
        )
        RoleButton(
            label = "RECEVOIR",
            subtitle = "de la charge",
            color = Color(0xFF00E676),
            modifier = Modifier.weight(1f),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onRoleSelected(Role.RECEIVER)
            }
        )
    }

    Spacer(Modifier.height(32.dp))

    // How it works hint
    Surface(
        color = Color(0xFF151515),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Comment \u00e7a marche",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            HintStep("1", "Lancez l'app sur les 2 t\u00e9l\u00e9phones")
            HintStep("2", "Choisissez donneur / receveur")
            HintStep("3", "Scannez et connectez-vous")
            HintStep("4", "Branchez le c\u00e2ble USB-C entre les 2")
        }
    }
}

@Composable
private fun HintStep(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF252525)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
        }
        Text(text, fontSize = 12.sp, color = Color(0xFF888888))
    }
}

@Composable
private fun RoleButton(
    label: String,
    subtitle: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF777777))
        }
    }
}

// ─── Scan Section ───────────────────────────────────────────────

@Composable
private fun ScanSection(
    status: ConnectionStatus,
    role: Role,
    peers: List<WifiP2pDevice>,
    onScanClicked: () -> Unit,
    onPeerSelected: (WifiP2pDevice) -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val roleLabel = if (role == Role.SENDER) "Donneur" else "Receveur"
    val roleColor = if (role == Role.SENDER) Color(0xFFFF5252) else Color(0xFF00E676)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onBack()
        }) {
            Text("\u2190 Retour", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }

        Surface(
            color = roleColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = roleLabel,
                color = roleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onScanClicked()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp),
        enabled = status != ConnectionStatus.SEARCHING
    ) {
        if (status == ConnectionStatus.SEARCHING) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.Black,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("Recherche...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
            Text("Scanner les appareils", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    AnimatedVisibility(
        visible = peers.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Appareils trouv\u00e9s",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${peers.size}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(peers, key = { it.deviceAddress }) { device ->
                    PeerItem(device = device, onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onPeerSelected(device)
                    })
                }
            }
        }
    }

    // Empty state while searching
    if (peers.isEmpty() && status == ConnectionStatus.SEARCHING) {
        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Assurez-vous que l'autre appareil a aussi l'app ouverte",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
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
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF252525)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676))
                )
            }
            Column {
                Text(
                    text = device.deviceName.ifEmpty { "Appareil" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (device.deviceName.isNotEmpty()) {
                    Text(
                        text = device.deviceAddress,
                        color = Color(0xFF555555),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ─── Connecting ─────────────────────────────────────────────────

@Composable
private fun ConnectingView() {
    Spacer(Modifier.height(32.dp))

    val infiniteTransition = rememberInfiniteTransition(label = "connecting")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size((32 * scale).dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp
        )
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Connexion en cours...",
        color = Color(0xFFFFD600),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Acceptez la demande sur l'autre appareil",
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        fontSize = 11.sp
    )
}

// ─── Connected View ─────────────────────────────────────────────

@Composable
private fun ConnectedView(state: DeviceState, onDisconnect: () -> Unit) {
    val view = LocalView.current

    if (state.peerBatteryLevel >= 0) {
        Spacer(Modifier.height(4.dp))

        // Transfer direction
        EnergyFlowIndicator(
            role = state.role,
            peerName = state.peerDeviceName.ifEmpty { "Pair" }
        )

        Spacer(Modifier.height(12.dp))

        // Peer battery card
        Surface(
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = state.peerDeviceName.ifEmpty { "Pair" },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Batterie distante",
                        color = Color(0xFF666666),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${state.peerBatteryLevel}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            state.peerBatteryLevel > 60 -> Color(0xFF00E676)
                            state.peerBatteryLevel > 20 -> Color(0xFFFFD600)
                            else -> Color(0xFFFF5252)
                        },
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "%",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onDisconnect()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFFF5252).copy(alpha = 0.3f), Color(0xFFFF5252).copy(alpha = 0.3f))
            )
        )
    ) {
        Text("D\u00e9connecter", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun EnergyFlowIndicator(role: Role, peerName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowOffset"
    )

    val isSending = role == Role.SENDER
    val color = if (isSending) Color(0xFFFF5252) else Color(0xFF00E676)
    val leftLabel = if (isSending) "Vous" else peerName
    val rightLabel = if (isSending) peerName else "Vous"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(leftLabel, fontSize = 12.sp, color = Color(0xFF888888), fontWeight = FontWeight.Medium)

        Spacer(Modifier.width(8.dp))

        // Animated dots
        Canvas(modifier = Modifier
            .width(80.dp)
            .height(12.dp)
        ) {
            val dotCount = 5
            val totalWidth = size.width
            for (i in 0 until dotCount) {
                val basePos = (i.toFloat() / dotCount)
                val pos = if (isSending) {
                    (basePos + offset) % 1f
                } else {
                    (1f - basePos - offset + 1f) % 1f
                }
                val x = pos * totalWidth
                val alpha = 1f - (pos - 0.5f).let { it * it } * 2f
                drawCircle(
                    color = color.copy(alpha = alpha.coerceIn(0.2f, 0.9f)),
                    radius = 2.5.dp.toPx(),
                    center = Offset(x, size.height / 2)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(rightLabel, fontSize = 12.sp, color = Color(0xFF888888), fontWeight = FontWeight.Medium)
    }
}

private val EaseInOutSine: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
private val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
