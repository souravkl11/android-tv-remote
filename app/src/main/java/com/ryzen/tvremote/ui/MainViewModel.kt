package com.ryzen.tvremote.ui

import android.app.Application
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
import com.ryzen.tvremote.protocol.KeyDirection
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.protocol.VolumeState
import com.ryzen.tvremote.update.AppUpdateInfo
import com.ryzen.tvremote.update.UpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val _controlMode = MutableStateFlow(
        runCatching { ControlMode.valueOf(prefs.getString(KEY_CONTROL_MODE, null) ?: "") }
            .getOrDefault(ControlMode.SWIPE)
    )
    val controlMode: StateFlow<ControlMode> = _controlMode.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _volumeKeysControlTv = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEYS, true))
    val volumeKeysControlTv: StateFlow<Boolean> = _volumeKeysControlTv.asStateFlow()

    // ---- UI state ----------------------------------------------------------------------------

    val showTutorial = MutableStateFlow(!prefs.getBoolean(KEY_TUTORIAL_SEEN, false))
    val availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val connectionStatus: StateFlow<ConnectionStatus> = client.status
    val volumeState: StateFlow<VolumeState> = client.volumeState

    val appShortcuts: StateFlow<List<AppShortcut>> = shortcutRepo.shortcuts

    private val _currentIp = MutableStateFlow(prefs.getString(KEY_LAST_IP, DEFAULT_IP) ?: DEFAULT_IP)
    val currentIp: StateFlow<String> = _currentIp.asStateFlow()

    private val _currentName = MutableStateFlow(prefs.getString(KEY_LAST_NAME, DEFAULT_NAME) ?: DEFAULT_NAME)
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices
    val isScanning: StateFlow<Boolean> = discoveryManager.isScanning

    val showPinDialog = MutableStateFlow(false)
    val showDevicePicker = MutableStateFlow(false)
    val showKeyboardDialog = MutableStateFlow(false)
    val showSettings = MutableStateFlow(false)

    /** True while the app is on screen; used to avoid reconnecting from the background. */
    private var inForeground = false

    init {
        viewModelScope.launch {
            client.status.collect { status ->
                if (status is ConnectionStatus.NeedsPairing) {
                    showPinDialog.value = true
                } else if (status is ConnectionStatus.Connected) {
                    // The client can't reliably read the TV's name, so keep the name we already
                    // have (from discovery, or the IP for manual connections).
                    showPinDialog.value = false
                }
            }
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

    fun launchApp(shortcut: AppShortcut) {
        if (connectionStatus.value !is ConnectionStatus.Connected) {
            _toastEvent.tryEmit("Not connected to a TV")
            return
        }
        client.launchApp(shortcut.launchTarget)
        _toastEvent.tryEmit("Opening ${shortcut.name} on TV…")
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

    fun setAmoledMode(enabled: Boolean) {
        _isAmoledMode.value = enabled
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
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
                _toastEvent.emit("You are on the latest version (v$currentVersion)")
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
        discoveryManager.stopDiscovery()
        client.disconnect()
    }
}
