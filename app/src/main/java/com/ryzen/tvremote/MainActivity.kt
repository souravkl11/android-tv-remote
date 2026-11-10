package com.ryzen.tvremote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.ControlMode
import com.ryzen.tvremote.ui.MainViewModel
import com.ryzen.tvremote.ui.components.ActionButtonsRow
import com.ryzen.tvremote.ui.components.AddAppSheet
import com.ryzen.tvremote.ui.components.AppStrip
import com.ryzen.tvremote.ui.components.ConnectionBanner
import com.ryzen.tvremote.ui.components.DPad
import com.ryzen.tvremote.ui.components.DevicePickerSheet
import com.ryzen.tvremote.ui.components.LocalHapticsEnabled
import com.ryzen.tvremote.ui.components.ModeSwitch
import com.ryzen.tvremote.ui.components.PairingPinDialog
import com.ryzen.tvremote.ui.components.RoundButton
import com.ryzen.tvremote.ui.components.SettingsSheet
import com.ryzen.tvremote.ui.components.SwipePad
import com.ryzen.tvremote.ui.components.TopBar
import com.ryzen.tvremote.ui.components.TypePanel
import com.ryzen.tvremote.ui.components.TutorialDialog
import com.ryzen.tvremote.ui.components.UpdateDialog
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
            val haptics by viewModel.hapticsEnabled.collectAsState()
            TVRemoteTheme(isAmoled = isAmoledMode) {
                CompositionLocalProvider(LocalHapticsEnabled provides haptics) {
                    TVRemoteScreen(viewModel)
                }
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

    /** Phone volume buttons drive the TV volume while connected (can be turned off in Settings). */
    private fun volumeKeyFor(keyCode: Int): RemoteKey? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VOLUME_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VOLUME_DOWN
        else -> null
    }

    private fun interceptsVolume(): Boolean =
        viewModel.volumeKeysControlTv.value && viewModel.connectionStatus.value is ConnectionStatus.Connected

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val key = volumeKeyFor(keyCode)
        if (key != null && interceptsVolume()) {
            viewModel.sendKey(key)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyFor(keyCode) != null && interceptsVolume()) return true
        return super.onKeyUp(keyCode, event)
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
    val isAmoledMode by viewModel.isAmoledMode.collectAsState()
    val controlMode by viewModel.controlMode.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val volumeKeys by viewModel.volumeKeysControlTv.collectAsState()
    val shortcuts by viewModel.appShortcuts.collectAsState()

    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val showDevicePicker by viewModel.showDevicePicker.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val showTutorial by viewModel.showTutorial.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()

    var showAddApp by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<AppShortcut?>(null) }

    val context = LocalContext.current
    val connected = status is ConnectionStatus.Connected
    val controlsAlpha = if (connected) 1f else 0.45f

    BackHandler(enabled = isTyping) { viewModel.isTyping.value = false }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RemoteTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(
            status = status,
            deviceName = deviceName,
            onDevicePickerClick = { viewModel.showDevicePicker.value = true },
            onPowerClick = { viewModel.sendKey(RemoteKey.POWER) },
            onSettingsClick = { viewModel.showSettings.value = true }
        )

        AnimatedVisibility(
            visible = !connected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ConnectionBanner(
                status = status,
                onRetry = { viewModel.reconnect() },
                onChooseTv = { viewModel.showDevicePicker.value = true },
                onRePair = { viewModel.rePair() },
                onEnterCode = { viewModel.showPinDialog.value = true }
            )
        }

            Spacer(Modifier.height(8.dp))

            AppStrip(
                shortcuts = shortcuts,
                onLaunch = { viewModel.launchApp(it) },
                onAdd = { editingApp = null; showAddApp = true },
                onEdit = { editingApp = it; showAddApp = true },
                onMove = { app, delta -> viewModel.moveShortcut(app.id, delta) },
                onRemove = { viewModel.removeShortcut(it.id) },
                modifier = Modifier.alpha(controlsAlpha)
            )

            Spacer(Modifier.height(14.dp))

            ModeSwitch(
                mode = controlMode,
                onModeChange = { viewModel.setControlMode(it) },
                modifier = Modifier.alpha(controlsAlpha)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .alpha(controlsAlpha),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = controlMode, label = "controlMode") { mode ->
                    when (mode) {
                        ControlMode.SWIPE -> SwipePad(
                            onKey = { key, direction -> viewModel.sendKey(key, direction) },
                            modifier = Modifier.fillMaxSize()
                        )
                        ControlMode.DPAD -> {
                            val diameter = minOf(maxWidth, maxHeight, 300.dp)
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                DPad(
                                    onKey = { key, direction -> viewModel.sendKey(key, direction) },
                                    modifier = Modifier.size(diameter)
                                )
                            }
                        }
                    }
                }
            }

        if (isTyping) {
            // Docked above the keyboard; the pad above stays usable.
            TypePanel(
                onSendText = { viewModel.sendText(it) },
                onKey = { viewModel.sendKey(it) },
                onClose = { viewModel.isTyping.value = false },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
            )
        } else {
            ActionButtonsRow(
                onKey = { key -> viewModel.sendKey(key) },
                onKeyboardClick = { viewModel.isTyping.value = true },
                modifier = Modifier.alpha(controlsAlpha)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .alpha(controlsAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                VolumePillRow(
                    isMuted = volumeState.isMuted,
                    level = volumeState.level,
                    max = volumeState.max,
                    onKey = { key -> viewModel.sendKey(key) },
                    modifier = Modifier.weight(1f)
                )
                RoundButton(
                    icon = Icons.Default.PlayArrow,
                    description = "Play or pause",
                    onClick = { viewModel.sendKey(RemoteKey.MEDIA_PLAY_PAUSE) },
                    size = 64.dp
                )
            }
        }
    }

    // ---- Sheets & dialogs --------------------------------------------------------------------

    if (showPinDialog) {
        PairingPinDialog(
            onConfirm = { pin -> viewModel.finishPairing(pin) },
            onDismiss = { viewModel.showPinDialog.value = false }
        )
    }

    if (showDevicePicker) {
        DevicePickerSheet(
            currentIp = currentIp,
            connectedIp = (status as? ConnectionStatus.Connected)?.host,
            discoveredDevices = discoveredDevices,
            isScanning = isScanning,
            onScanClick = { viewModel.scanDevices() },
            onConnect = { ip, name -> viewModel.connectToTv(ip, name) },
            onDismiss = { viewModel.showDevicePicker.value = false }
        )
    }

    if (showSettings) {
        SettingsSheet(
            controlMode = controlMode,
            isAmoled = isAmoledMode,
            hapticsEnabled = hapticsEnabled,
            volumeKeysControlTv = volumeKeys,
            versionName = viewModel.getAppVersion(),
            onControlModeChange = { viewModel.setControlMode(it) },
            onAmoledChange = { viewModel.setAmoledMode(it) },
            onHapticsChange = { viewModel.setHapticsEnabled(it) },
            onVolumeKeysChange = { viewModel.setVolumeKeysControlTv(it) },
            onAddApp = {
                viewModel.showSettings.value = false
                editingApp = null
                showAddApp = true
            },
            onSwitchTv = {
                viewModel.showSettings.value = false
                viewModel.showDevicePicker.value = true
            },
            onRePair = {
                viewModel.showSettings.value = false
                viewModel.rePair()
            },
            onTutorial = {
                viewModel.showSettings.value = false
                viewModel.openTutorial()
            },
            onCheckUpdate = {
                viewModel.showSettings.value = false
                viewModel.checkForUpdates(manual = true)
            },
            onDismiss = { viewModel.showSettings.value = false }
        )
    }

    if (showAddApp) {
        AddAppSheet(
            editing = editingApp,
            existing = shortcuts,
            onAddPreset = { viewModel.addShortcut(it.toShortcut()) },
            onSave = { app ->
                if (editingApp != null) viewModel.updateShortcut(app) else viewModel.addShortcut(app)
                showAddApp = false
                editingApp = null
            },
            onDismiss = {
                showAddApp = false
                editingApp = null
            }
        )
    }

    if (showTutorial) {
        TutorialDialog(onDismiss = { viewModel.dismissTutorial() })
    }

    availableUpdate?.let { update ->
        UpdateDialog(
            updateInfo = update,
            currentVersion = viewModel.getAppVersion(),
            onDownload = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                viewModel.dismissUpdate(ignoreForNow = false)
            },
            onDismiss = { viewModel.dismissUpdate(ignoreForNow = true) }
        )
    }
}
