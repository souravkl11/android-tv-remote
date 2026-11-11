package com.ryzen.tvremote.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryzen.tvremote.BuildConfig
import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.apps.AppShortcutRepository
import com.ryzen.tvremote.protocol.AndroidTvRemoteV2Client
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.protocol.DiscoveredDevice
import com.ryzen.tvremote.protocol.DiscoveryManager
import com.ryzen.tvremote.protocol.ImeField
import com.ryzen.tvremote.protocol.KeyDirection
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.protocol.VolumeState
import com.ryzen.tvremote.update.AppUpdateInfo
import com.ryzen.tvremote.update.UpdateChecker
import com.ryzen.tvremote.voice.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val PREFS_NAME = "tv_remote_prefs"
        private const val KEY_LAST_IP = "last_tv_ip"
        private const val KEY_LAST_NAME = "last_tv_name"
        private const val KEY_AMOLED_MODE = "is_amoled_mode"
        private const val KEY_TUTORIAL_SEEN = "has_seen_tutorial"
        private const val KEY_IGNORED_UPDATE = "ignored_update_version"
        private const val KEY_CONTROL_MODE = "control_mode"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_VOLUME_KEYS = "volume_keys_control_tv"
        private const val KEY_AUTO_KEYBOARD = "auto_keyboard"
        /** How long the TV keeps lending its mic to apps after the voice key. */
        private const val TV_MIC_WINDOW_MS = 13_000L
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val DEFAULT_IP = ""
        const val DEFAULT_NAME = "Android TV"
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val client = AndroidTvRemoteV2Client(application, viewModelScope)
    val discoveryManager = DiscoveryManager(application)
    private val shortcutRepo = AppShortcutRepository(prefs)

    // ---- Preferences -------------------------------------------------------------------------

    private val _isAmoledMode = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_MODE, false))
    val isAmoledMode: StateFlow<Boolean> = _isAmoledMode.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "") }
            .getOrDefault(ThemeMode.DARK)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _controlMode = MutableStateFlow(
        runCatching { ControlMode.valueOf(prefs.getString(KEY_CONTROL_MODE, null) ?: "") }
            .getOrDefault(ControlMode.SWIPE)
    )
    val controlMode: StateFlow<ControlMode> = _controlMode.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _volumeKeysControlTv = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEYS, true))
    val volumeKeysControlTv: StateFlow<Boolean> = _volumeKeysControlTv.asStateFlow()

    private val _autoKeyboard = MutableStateFlow(prefs.getBoolean(KEY_AUTO_KEYBOARD, true))
    val autoKeyboard: StateFlow<Boolean> = _autoKeyboard.asStateFlow()

    // ---- UI state ----------------------------------------------------------------------------

    /** The quick tour opens by itself once, the first time a TV connects. */
    val showTutorial = MutableStateFlow(false)
    val availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    /** Debug builds only: a pretend connection state so the UI can be reviewed without a TV. */
    private val demoStatus = MutableStateFlow<ConnectionStatus?>(null)

    val connectionStatus: StateFlow<ConnectionStatus> =
        combine(client.status, demoStatus) { real, demo -> demo ?: real }
            .stateIn(viewModelScope, SharingStarted.Eagerly, client.status.value)

    val volumeState: StateFlow<VolumeState> =
        combine(client.volumeState, demoStatus) { real, demo ->
            if (demo is ConnectionStatus.Connected) VolumeState(level = 14, max = 40) else real
        }.stateIn(viewModelScope, SharingStarted.Eagerly, client.volumeState.value)

    val appShortcuts: StateFlow<List<AppShortcut>> = shortcutRepo.shortcuts

    private val _currentIp = MutableStateFlow(prefs.getString(KEY_LAST_IP, DEFAULT_IP) ?: DEFAULT_IP)
    val currentIp: StateFlow<String> = _currentIp.asStateFlow()

    private val _currentName = MutableStateFlow(prefs.getString(KEY_LAST_NAME, DEFAULT_NAME) ?: DEFAULT_NAME)
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices
    val isScanning: StateFlow<Boolean> = discoveryManager.isScanning

    val showPinDialog = MutableStateFlow(false)
    val showDevicePicker = MutableStateFlow(false)
    val isTyping = MutableStateFlow(false)

    /** The TV text field being typed into, if the TV told us about one. Seeds the typing panel. */
    private val _typingField = MutableStateFlow<ImeField?>(null)
    val typingField: StateFlow<ImeField?> = _typingField.asStateFlow()
    /** True when the panel was opened by the TV rather than the user, so the TV may close it too. */
    private var typingOpenedByTv = false

    private val _voice = MutableStateFlow<VoiceUi>(VoiceUi.Idle)
    val voice: StateFlow<VoiceUi> = _voice.asStateFlow()
    private var voiceJob: Job? = null
    private var recorder: VoiceRecorder? = null
    val showSettings = MutableStateFlow(false)

    /** True while the app is on screen; used to avoid reconnecting from the background. */
    private var inForeground = false

    init {
        viewModelScope.launch {
            connectionStatus.collect { status ->
                if (status is ConnectionStatus.NeedsPairing) {
                    showPinDialog.value = true
                } else if (status is ConnectionStatus.Connected) {
                    // The client can't reliably read the TV's name, so keep the name we already
                    // have (from discovery, or the IP for manual connections).
                    showPinDialog.value = false
                    if (!prefs.getBoolean(KEY_TUTORIAL_SEEN, false)) showTutorial.value = true
                }
            }
        }

        // A text field got focus on the TV (search box, sign-in, OTP...): bring up the keyboard.
        viewModelScope.launch {
            client.imeField.collect { field ->
                if (field != null) {
                    // While the panel is open the TV echoes every edit back; the panel already has it.
                    if (isTyping.value || !_autoKeyboard.value || _voice.value !is VoiceUi.Idle) return@collect
                    _typingField.value = field
                    typingOpenedByTv = true
                    isTyping.value = true
                } else if (isTyping.value && typingOpenedByTv) {
                    closeTyping()
                }
            }
        }

        viewModelScope.launch {
            client.voiceRequests.collect { sessionId -> acceptTvVoice(sessionId) }
        }
        viewModelScope.launch {
            client.voiceEnded.collect { recorder?.stop() }
        }

        // Auto-reconnect to a known TV when discovery finds it again.
        viewModelScope.launch {
            discoveryManager.discoveredDevices.collect { devices ->
                if (devices.isEmpty() || !inForeground) return@collect

                // Adopt the friendly name once discovery sees the TV we're configured for.
                devices.find { it.host == _currentIp.value }?.let { match ->
                    if (match.name.isNotBlank() && match.name != _currentName.value) {
                        _currentName.value = match.name
                        prefs.edit().putString(KEY_LAST_NAME, match.name).apply()
                    }
                }

                val status = connectionStatus.value
                if (status is ConnectionStatus.Connected ||
                    status is ConnectionStatus.Connecting ||
                    status is ConnectionStatus.NeedsPairing
                ) return@collect

                val known = devices.find { it.host == _currentIp.value }
                if (known != null) {
                    Log.i(TAG, "Connecting to known TV: ${known.name} (${known.host})")
                    connectToTv(known.host, known.name)
                } else if (_currentIp.value.isEmpty()) {
                    if (devices.size == 1) {
                        connectToTv(devices.first().host, devices.first().name)
                    } else {
                        showDevicePicker.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            delay(2500L)
            checkForUpdates(manual = false)
        }
    }

    // ---- Connection --------------------------------------------------------------------------

    fun connectToTv(ip: String, name: String? = null) {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty()) return

        val displayName = name?.takeIf { it.isNotBlank() }
            ?: if (cleanIp == _currentIp.value) _currentName.value else cleanIp

        _currentIp.value = cleanIp
        _currentName.value = displayName
        prefs.edit()
            .putString(KEY_LAST_IP, cleanIp)
            .putString(KEY_LAST_NAME, displayName)
            .apply()

        showDevicePicker.value = false

        viewModelScope.launch {
            try {
                client.connect(cleanIp)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to TV: ${e.message}", e)
            }
        }
    }

    fun reconnect() = connectToTv(_currentIp.value)

    fun finishPairing(pinCode: String) {
        showPinDialog.value = false
        viewModelScope.launch {
            try {
                client.finishPairing(pinCode)
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed: ${e.message}", e)
            }
        }
    }

    fun rePair() {
        if (_currentIp.value.isEmpty()) {
            showDevicePicker.value = true
        } else {
            client.forcePair(_currentIp.value)
        }
    }

    fun scanDevices() {
        discoveryManager.stopDiscovery()
        discoveryManager.startDiscovery()
    }

    fun onForeground() {
        inForeground = true
        discoveryManager.startDiscovery()
        if (_currentIp.value.isNotEmpty()) connectToTv(_currentIp.value)
    }

    fun onBackground() {
        inForeground = false
        // Release the TV session so other phones can connect, and stop battery-hungry discovery.
        client.disconnect()
        discoveryManager.stopDiscovery()
    }

    // ---- Remote input ------------------------------------------------------------------------

    fun sendKey(key: RemoteKey, direction: KeyDirection = KeyDirection.SHORT) {
        client.sendKey(key, direction)
    }

    fun sendText(text: String) = client.sendText(text)

    /** Opens the typing panel from the keyboard button, seeded with the TV field if one is focused. */
    fun openTyping() {
        typingOpenedByTv = false
        _typingField.value = client.imeField.value
        isTyping.value = true
    }

    fun closeTyping() {
        typingOpenedByTv = false
        isTyping.value = false
        _typingField.value = null
    }

    // ---- Voice search ------------------------------------------------------------------------

    fun hasMicPermission(): Boolean =
        getApplication<Application>().checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Opens a voice session on the TV and streams the microphone until the user stops talking. */
    fun startVoice() {
        if (voiceJob?.isActive == true) return
        if (connectionStatus.value !is ConnectionStatus.Connected) {
            _toastEvent.tryEmit("Not connected to a TV")
            return
        }
        if (!hasMicPermission()) return
        if (isTyping.value) closeTyping()

        voiceJob = viewModelScope.launch(Dispatchers.IO) {
            _voice.value = VoiceUi.Starting
            val session = client.startVoice()
            if (session == null) {
                // No assistant answered. The TV still lends its mic to apps for a short while, so
                // pressing a mic button on screen (YouTube search, for one) streams from the phone.
                _voice.value = VoiceUi.WaitingForTv
                val offered = withTimeoutOrNull(TV_MIC_WINDOW_MS) { client.voiceRequests.first() }
                if (offered == null) {
                    _voice.value = VoiceUi.Idle
                    _toastEvent.tryEmit("This TV has no voice search set up")
                    return@launch
                }
                client.acceptVoice(offered)
            }
            streamVoice()
        }
    }

    /** The TV opened a voice session itself (an app's mic button): talk into the phone. */
    private fun acceptTvVoice(sessionId: Int) {
        if (voiceJob?.isActive == true || !hasMicPermission()) return
        if (isTyping.value) closeTyping()
        voiceJob = viewModelScope.launch(Dispatchers.IO) {
            client.acceptVoice(sessionId)
            streamVoice()
        }
    }

    private suspend fun streamVoice() {
        _voice.value = VoiceUi.Listening(0f)
        val rec = VoiceRecorder().also { recorder = it }
        // Debug builds: replay files/voice_test.pcm if present, and keep the last capture in
        // files/voice_last.pcm, so the audio path can be checked without speaking.
        val files = getApplication<Application>().filesDir
        val replay = if (BuildConfig.DEBUG) files?.resolve("voice_test.pcm")?.takeIf { it.exists() }?.readBytes() else null
        val dump = if (BuildConfig.DEBUG) files?.resolve("voice_last.pcm")?.outputStream() else null
        val ok = try {
            rec.record(
                onLevel = { level -> if (_voice.value is VoiceUi.Listening) _voice.value = VoiceUi.Listening(level) },
                onChunk = { bytes, length ->
                    dump?.write(bytes, 0, length)
                    client.sendVoice(bytes, length)
                },
                replay = replay,
                replayRate = files?.resolve("voice_test.rate")?.takeIf { BuildConfig.DEBUG && it.exists() }
                    ?.readText()?.trim()?.toIntOrNull() ?: VoiceRecorder.SAMPLE_RATE
            )
        } finally {
            dump?.close()
            client.endVoice()
            recorder = null
            _voice.value = VoiceUi.Idle
        }
        if (!ok) _toastEvent.tryEmit("Couldn't use the microphone")
    }

    /** Stops listening; what was said so far is still searched on the TV. */
    fun stopVoice() {
        recorder?.stop()
        if (_voice.value is VoiceUi.Starting || _voice.value is VoiceUi.WaitingForTv) {
            voiceJob?.cancel()
            _voice.value = VoiceUi.Idle
        }
    }

    /** Shows a short message at the bottom of the pad. */
    fun notify(message: String) {
        _toastEvent.tryEmit(message)
    }

    fun launchApp(shortcut: AppShortcut) {
        if (connectionStatus.value !is ConnectionStatus.Connected) {
            _toastEvent.tryEmit("Not connected to a TV")
            return
        }
        client.launchApp(shortcut.launchTarget)
        _toastEvent.tryEmit("Opening ${shortcut.name}")
    }

    // ---- App shortcuts -----------------------------------------------------------------------

    fun addShortcut(shortcut: AppShortcut) = shortcutRepo.add(shortcut)
    fun updateShortcut(shortcut: AppShortcut) = shortcutRepo.update(shortcut)
    fun removeShortcut(id: String) = shortcutRepo.remove(id)
    fun moveShortcut(id: String, delta: Int) = shortcutRepo.move(id, delta)

    // ---- Settings ----------------------------------------------------------------------------

    fun setControlMode(mode: ControlMode) {
        _controlMode.value = mode
        prefs.edit().putString(KEY_CONTROL_MODE, mode.name).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun setDemo(state: String?) {
        if (!BuildConfig.DEBUG || state == null) return
        if (_currentIp.value.isEmpty()) {
            // Not persisted: just gives the demo a TV to talk about.
            _currentIp.value = "192.168.1.20"
            _currentName.value = "Living Room TV"
        }
        val name = _currentName.value
        demoStatus.value = when (state) {
            "connected" -> ConnectionStatus.Connected("demo", name)
            "connecting" -> ConnectionStatus.Connecting("Connecting…")
            "pairing" -> ConnectionStatus.NeedsPairing()
            "error" -> ConnectionStatus.Error("Demo error")
            "idle" -> ConnectionStatus.Disconnected
            else -> null
        }
    }

    /** Debug builds only: opens a sheet directly, for UI review over adb. */
    fun debugOpen(what: String?) {
        if (!BuildConfig.DEBUG || what == null) return
        viewModelScope.launch {
            delay(1200L) // let the start-up connection logic settle first
            debugOpenNow(what)
        }
    }

    private fun debugOpenNow(what: String) {
        when (what) {
            "settings" -> showSettings.value = true
            "picker" -> showDevicePicker.value = true
            "pairing" -> showPinDialog.value = true
            "tour" -> showTutorial.value = true
            "typing" -> isTyping.value = true
            "tvfield" -> {
                _typingField.value = ImeField(value = "stranger", label = "Search movies and shows")
                typingOpenedByTv = true
                isTyping.value = true
            }
            "voicewait" -> voiceJob = viewModelScope.launch {
                _voice.value = VoiceUi.WaitingForTv
                delay(8000)
                _voice.value = VoiceUi.Idle
            }
            "voice" -> voiceJob = viewModelScope.launch {
                // Simulated levels so the listening panel can be checked without a TV.
                repeat(40) { i ->
                    _voice.value = VoiceUi.Listening(((i * 37) % 100) / 100f)
                    delay(120)
                }
                _voice.value = VoiceUi.Idle
            }
            "update" -> availableUpdate.value = AppUpdateInfo(
                latestVersion = "1.3.0",
                releaseNotes = "Fresh Material 3 Expressive design, a light theme and a new swipe pad.",
                releaseUrl = "https://example.com",
                apkDownloadUrl = null
            )
            "close" -> {
                showSettings.value = false
                showDevicePicker.value = false
                showPinDialog.value = false
                showTutorial.value = false
                isTyping.value = false
                availableUpdate.value = null
            }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        _isAmoledMode.value = enabled
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    fun setAutoKeyboard(enabled: Boolean) {
        _autoKeyboard.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_KEYBOARD, enabled).apply()
    }

    fun setVolumeKeysControlTv(enabled: Boolean) {
        _volumeKeysControlTv.value = enabled
        prefs.edit().putBoolean(KEY_VOLUME_KEYS, enabled).apply()
    }

    fun dismissTutorial() {
        showTutorial.value = false
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply()
    }

    fun openTutorial() {
        showTutorial.value = true
    }

    // ---- Updates -----------------------------------------------------------------------------

    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            val currentVersion = getAppVersion()
            val update = UpdateChecker.checkForUpdate(currentVersion)
            if (update != null) {
                val ignoredVersion = prefs.getString(KEY_IGNORED_UPDATE, "")
                if (manual || ignoredVersion != update.latestVersion) {
                    availableUpdate.value = update
                }
            } else if (manual) {
                _toastEvent.emit("You're up to date (v$currentVersion)")
            }
        }
    }

    fun dismissUpdate(ignoreForNow: Boolean = true) {
        val update = availableUpdate.value
        if (ignoreForNow && update != null) {
            prefs.edit().putString(KEY_IGNORED_UPDATE, update.latestVersion).apply()
        }
        availableUpdate.value = null
    }

    override fun onCleared() {
        super.onCleared()
        recorder?.stop()
        discoveryManager.stopDiscovery()
        client.disconnect()
    }
}

sealed interface VoiceUi {
    data object Idle : VoiceUi
    /** Waiting for the TV to open its voice search. */
    data object Starting : VoiceUi
    /** The TV has no assistant; waiting for an app's own mic button on the TV. */
    data object WaitingForTv : VoiceUi
    /** Streaming the microphone; [level] is the current loudness, 0..1. */
    data class Listening(val level: Float) : VoiceUi
}
