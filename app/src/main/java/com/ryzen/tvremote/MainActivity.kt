package com.ryzen.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.MainViewModel
import com.ryzen.tvremote.ui.components.ActionButtonsRow
import com.ryzen.tvremote.ui.components.DevicePickerDialog
import com.ryzen.tvremote.ui.components.KeyboardDialog
import com.ryzen.tvremote.ui.components.PairingPinDialog
import com.ryzen.tvremote.ui.components.TopBar
import com.ryzen.tvremote.ui.components.Trackpad
import com.ryzen.tvremote.ui.components.VolumePillRow
import com.ryzen.tvremote.ui.theme.RemoteTheme
import com.ryzen.tvremote.ui.theme.TVRemoteTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isAmoledMode by viewModel.isAmoledMode.collectAsState()
            TVRemoteTheme(isAmoled = isAmoledMode) {
                TVRemoteScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }
}

@Composable
fun TVRemoteScreen(viewModel: MainViewModel) {
    val status by viewModel.connectionStatus.collectAsState()
    val volumeState by viewModel.volumeState.collectAsState()
    val deviceName by viewModel.currentName.collectAsState()
    val currentIp by viewModel.currentIp.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isAdbMode by viewModel.isAdbMode.collectAsState()
    val isAmoledMode by viewModel.isAmoledMode.collectAsState()

    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val showDevicePickerDialog by viewModel.showDevicePickerDialog.collectAsState()
    val showKeyboardDialog by viewModel.showKeyboardDialog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RemoteTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(
            status = status,
            deviceName = deviceName,
            onDevicePickerClick = { viewModel.showDevicePickerDialog.value = true },
            onInputClick = { viewModel.sendKey(RemoteKey.TV_INPUT) },
            onKeyboardClick = { viewModel.showKeyboardDialog.value = true },
            onPowerClick = { viewModel.sendKey(RemoteKey.POWER) },
            onRePairClick = { viewModel.rePair() },
            onProtocolSwitchClick = { viewModel.setAdbMode(!isAdbMode) },
            isAdbMode = isAdbMode,
            isAmoledMode = isAmoledMode,
            onToggleAmoled = { viewModel.toggleAmoledMode() }
        )

        Trackpad(
            onKey = { key -> viewModel.sendKey(key) },
            modifier = Modifier.weight(1f)
        )

        ActionButtonsRow(
            onKey = { key -> viewModel.sendKey(key) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        VolumePillRow(
            isMuted = volumeState.isMuted,
            onKey = { key -> viewModel.sendKey(key) }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Modal Dialogs
    if (showPinDialog) {
        PairingPinDialog(
            onConfirm = { pin -> viewModel.finishPairing(pin) },
            onDismiss = { viewModel.showPinDialog.value = false }
        )
    }

    if (showDevicePickerDialog) {
        DevicePickerDialog(
            currentIp = currentIp,
            discoveredDevices = discoveredDevices,
            isScanning = isScanning,
            isAdbMode = isAdbMode,
            onScanClick = { viewModel.scanDevices() },
            onProtocolChange = { adb -> viewModel.setAdbMode(adb) },
            onConnect = { ip, name -> viewModel.connectToTv(ip, name) },
            onDismiss = { viewModel.showDevicePickerDialog.value = false }
        )
    }

    if (showKeyboardDialog) {
        KeyboardDialog(
            onSendText = { text -> viewModel.sendText(text) },
            onKey = { key -> viewModel.sendKey(key) },
            onDismiss = { viewModel.showKeyboardDialog.value = false }
        )
    }
}
