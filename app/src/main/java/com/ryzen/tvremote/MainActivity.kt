package com.ryzen.tvremote

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.ControlMode
import com.ryzen.tvremote.ui.MainViewModel
import com.ryzen.tvremote.ui.components.AddAppSheet
import com.ryzen.tvremote.ui.components.AppStrip
import com.ryzen.tvremote.ui.components.ConnectionStage
import com.ryzen.tvremote.ui.components.ControlDeck
import com.ryzen.tvremote.ui.components.DPad
import com.ryzen.tvremote.ui.components.DevicePickerSheet
import com.ryzen.tvremote.ui.components.LocalHapticsEnabled
import com.ryzen.tvremote.ui.components.ModeSwitch
import com.ryzen.tvremote.ui.components.PairingSheet
import com.ryzen.tvremote.ui.components.SettingsSheet
import com.ryzen.tvremote.ui.components.SwipePad
import com.ryzen.tvremote.ui.components.TopBar
import com.ryzen.tvremote.ui.components.TutorialSheet
import com.ryzen.tvremote.ui.components.TypePanel
import com.ryzen.tvremote.ui.components.UpdateDialog
import com.ryzen.tvremote.ui.theme.Motion
import com.ryzen.tvremote.ui.theme.TVRemoteTheme
import com.ryzen.tvremote.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDebugExtras(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val pureBlack by viewModel.isAmoledMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            val haptics by viewModel.hapticsEnabled.collectAsState()

            // Status and navigation bar icons follow the app's theme, not the system's.
            val dark = isDarkTheme(themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            TVRemoteTheme(themeMode = themeMode, pureBlack = pureBlack, dynamicColor = dynamicColor) {
                CompositionLocalProvider(LocalHapticsEnabled provides haptics) {
                    TVRemoteScreen(viewModel)
                }
            }
        }
    }

    private fun handleDebugExtras(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        viewModel.setDemo(intent.getStringExtra("demo"))
        viewModel.debugOpen(intent.getStringExtra("open"))
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
    val themeMode by viewModel.themeMode.collectAsState()
    val pureBlack by viewModel.isAmoledMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
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
    val scheme = MaterialTheme.colorScheme
    val connected = status is ConnectionStatus.Connected
    val hasTv = currentIp.isNotEmpty()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = isTyping) { viewModel.isTyping.value = false }

    // Typing only makes sense while connected.
    LaunchedEffect(connected) { if (!connected) viewModel.isTyping.value = false }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(
            status = status,
            deviceName = deviceName,
            hasTv = hasTv,
            onDevicePickerClick = { viewModel.showDevicePicker.value = true },
            onPowerClick = { viewModel.sendKey(RemoteKey.POWER) },
            onSettingsClick = { viewModel.showSettings.value = true }
        )

        // Shortcuts step aside while typing so the pad keeps its room above the keyboard.
        AnimatedVisibility(
            visible = !isTyping,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            AppStrip(
                shortcuts = shortcuts,
                enabled = connected,
                onLaunch = { viewModel.launchApp(it) },
                onAdd = { editingApp = null; showAddApp = true },
                onEdit = { editingApp = it; showAddApp = true },
                onMove = { app, delta -> viewModel.moveShortcut(app.id, delta) },
                onRemove = { viewModel.removeShortcut(it.id) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AnimatedContent(
                targetState = connected,
                transitionSpec = {
                    (fadeIn(Motion.effects()) + scaleIn(Motion.spatial(), initialScale = 0.92f)) togetherWith
                        fadeOut(Motion.effects())
                },
                label = "stage"
            ) { isConnected ->
                if (isConnected) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ModeSwitch(mode = controlMode, onModeChange = { viewModel.setControlMode(it) })
                        Spacer(Modifier.height(12.dp))
                        AnimatedContent(
                            targetState = controlMode,
                            transitionSpec = {
                                (fadeIn(Motion.effects()) + scaleIn(Motion.spatial(), initialScale = 0.9f)) togetherWith
                                    (fadeOut(Motion.effects()) + scaleOut(Motion.effects(), targetScale = 0.96f))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            label = "controlMode"
                        ) { mode ->
                            when (mode) {
                                ControlMode.SWIPE -> SwipePad(
                                    onKey = { key, direction -> viewModel.sendKey(key, direction) },
                                    modifier = Modifier.fillMaxSize()
                                )
                                ControlMode.DPAD -> BoxWithConstraints(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DPad(
                                        onKey = { key, direction -> viewModel.sendKey(key, direction) },
                                        modifier = Modifier.size(minOf(maxWidth, maxHeight, 340.dp))
                                    )
                                }
                            }
                        }
                    }
                } else {
                    ConnectionStage(
                        status = status,
                        deviceName = deviceName,
                        hasTv = hasTv,
                        isScanning = isScanning,
                        onRetry = { viewModel.reconnect() },
                        onChooseTv = { viewModel.showDevicePicker.value = true },
                        onRePair = { viewModel.rePair() },
                        onEnterCode = { viewModel.showPinDialog.value = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) { data ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.inverseSurface)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = data.visuals.message,
                        color = scheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (isTyping) {
            // Docked above the keyboard; the pad above stays usable.
            TypePanel(
                onSendText = { viewModel.sendText(it) },
                onKey = { viewModel.sendKey(it) },
                onClose = { viewModel.isTyping.value = false },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            )
        } else {
            ControlDeck(
                isMuted = volumeState.isMuted,
                volumeLevel = volumeState.level,
                volumeMax = volumeState.max,
                enabled = connected,
                onKey = { key -> viewModel.sendKey(key) },
                onKeyboardClick = { viewModel.isTyping.value = true },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }

    // ---- Sheets & dialogs --------------------------------------------------------------------

    if (showPinDialog) {
        PairingSheet(
            deviceName = deviceName,
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
            themeMode = themeMode,
            isDark = isDarkTheme(themeMode),
            pureBlack = pureBlack,
            dynamicColor = dynamicColor,
            hapticsEnabled = hapticsEnabled,
            volumeKeysControlTv = volumeKeys,
            versionName = viewModel.getAppVersion(),
            onControlModeChange = { viewModel.setControlMode(it) },
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onPureBlackChange = { viewModel.setAmoledMode(it) },
            onDynamicColorChange = { viewModel.setDynamicColor(it) },
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
        TutorialSheet(onDismiss = { viewModel.dismissTutorial() })
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
                    viewModel.notify("Couldn't open the browser")
                }
                viewModel.dismissUpdate(ignoreForNow = false)
            },
            onDismiss = { viewModel.dismissUpdate(ignoreForNow = true) }
        )
    }
}
