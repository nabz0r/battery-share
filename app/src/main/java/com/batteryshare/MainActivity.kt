package com.batteryshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.batteryshare.model.ConnectionStatus
import com.batteryshare.ui.screens.HomeScreen
import com.batteryshare.ui.theme.BatteryShareTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var viewModelRef: BatteryShareViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModelRef?.initP2P()
        } else {
            viewModelRef?.setPermissionsDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BatteryShareTheme {
                val vm: BatteryShareViewModel = viewModel()
                viewModelRef = vm

                val state by vm.state.collectAsStateWithLifecycle()
                val peers by vm.p2pManager.peers.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Keep screen on during connection
                LaunchedEffect(state.connectionStatus) {
                    if (state.connectionStatus == ConnectionStatus.CONNECTED ||
                        state.connectionStatus == ConnectionStatus.TRANSFERRING
                    ) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Snackbar from errors
                LaunchedEffect(Unit) {
                    vm.snackbar.collect { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }

                // Request permissions on first composition
                LaunchedEffect(Unit) {
                    checkPermissionsAndInit(vm)
                }

                HomeScreen(
                    state = state,
                    peers = peers,
                    snackbarHostState = snackbarHostState,
                    onRoleSelected = { vm.setRole(it) },
                    onScanClicked = { vm.p2pManager.discoverPeers() },
                    onPeerSelected = { vm.p2pManager.connectToPeer(it) },
                    onDisconnect = { vm.disconnect() },
                    onDismissError = { vm.dismissError() }
                )
            }
        }
    }

    private fun checkPermissionsAndInit(vm: BatteryShareViewModel) {
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
            vm.initP2P()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
