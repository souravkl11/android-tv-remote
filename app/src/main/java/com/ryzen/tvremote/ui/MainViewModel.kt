package com.ryzen.tvremote.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryzen.tvremote.protocol.AdbRemoteClient
import com.ryzen.tvremote.protocol.AndroidTvRemoteV2Client
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.protocol.DiscoveredDevice
import com.ryzen.tvremote.protocol.DiscoveryManager
import com.ryzen.tvremote.protocol.IRemoteController
import com.ryzen.tvremote.protocol.KeyDirection
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.protocol.VolumeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val PREFS_NAME = "tv_remote_prefs"
        private const val KEY_LAST_IP = "last_tv_ip"
        private const val KEY_LAST_NAME = "last_tv_name"
        private const val KEY_USE_ADB = "use_adb"
        private const val KEY_AMOLED_MODE = "is_amoled_mode"
        private const val KEY_TUTORIAL_SEEN = "has_seen_tutorial"
        const val DEFAULT_IP = ""
        const val DEFAULT_NAME = "Android TV"
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val v2Client = AndroidTvRemoteV2Client(application, viewModelScope)
    private val adbClient = AdbRemoteClient(viewModelScope)
    val discoveryManager = DiscoveryManager(application)

    private val _isAdbMode = MutableStateFlow(prefs.getBoolean(KEY_USE_ADB, false))
    val isAdbMode: StateFlow<Boolean> = _isAdbMode.asStateFlow()

    private val _isAmoledMode = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_MODE, false))
    val isAmoledMode: StateFlow<Boolean> = _isAmoledMode.asStateFlow()

    val showTutorial = MutableStateFlow(!prefs.getBoolean(KEY_TUTORIAL_SEEN, false))

    private val activeController: IRemoteController
        get() = if (_isAdbMode.value) adbClient else v2Client

    val connectionStatus: StateFlow<ConnectionStatus> =
        combine(_isAdbMode, v2Client.status, adbClient.status) { adb, v2Status, adbStatus ->
            if (adb) adbStatus else v2Status
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.Disconnected)

    val volumeState: StateFlow<VolumeState> =
        combine(_isAdbMode, v2Client.volumeState, adbClient.volumeState) { adb, v2Vol, adbVol ->
            if (adb) adbVol else v2Vol
        }.stateIn(viewModelScope, SharingStarted.Eagerly, VolumeState())

    val currentApp: StateFlow<String> = v2Client.currentApp

    private val _currentIp = MutableStateFlow(prefs.getString(KEY_LAST_IP, DEFAULT_IP) ?: DEFAULT_IP)
    val currentIp: StateFlow<String> = _currentIp.asStateFlow()

    private val _currentName = MutableStateFlow(prefs.getString(KEY_LAST_NAME, DEFAULT_NAME) ?: DEFAULT_NAME)
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices
    val isScanning: StateFlow<Boolean> = discoveryManager.isScanning

    // Dialog display states
    val showPinDialog = MutableStateFlow(false)
    val showDevicePickerDialog = MutableStateFlow(false)
    val showKeyboardDialog = MutableStateFlow(false)

    init {
        // Observe connection status to automatically show PIN dialog if required
        viewModelScope.launch {
            v2Client.status.collect { status ->
                if (status is ConnectionStatus.NeedsPairing) {
                    showPinDialog.value = true
                } else if (status is ConnectionStatus.Connected) {
                    showPinDialog.value = false
                    _currentName.value = status.deviceName
                    prefs.edit().putString(KEY_LAST_NAME, status.deviceName).apply()
                }
            }
        }

        // Observe background mDNS scan and auto-connect to discovered TVs
        viewModelScope.launch {
            discoveryManager.discoveredDevices.collect { devices ->
                if (devices.isEmpty()) return@collect

                val currentStatus = connectionStatus.value
                val isConnected = currentStatus is ConnectionStatus.Connected
                val isConnecting = currentStatus is ConnectionStatus.Connecting

                // Attempt automatic reconnect when disconnected or in error state
                if (!isConnected && !isConnecting) {
                    val matchedDevice = devices.find { it.host == _currentIp.value || it.name == _currentName.value }
                    if (matchedDevice != null) {
                        Log.i(TAG, "Connecting to known TV: ${matchedDevice.name} (${matchedDevice.host})")
                        connectToTv(matchedDevice.host, matchedDevice.name)
                        return@collect
                    }

                    // If a single TV is discovered on network and current connection is inactive, auto-connect
                    if (devices.size == 1 && (currentStatus is ConnectionStatus.Error || currentStatus is ConnectionStatus.Disconnected)) {
                        val newTv = devices.first()
                        Log.i(TAG, "Auto-connecting to discovered TV: ${newTv.name} (${newTv.host})")
                        connectToTv(newTv.host, newTv.name)
                    } else if (devices.size > 1 && currentStatus is ConnectionStatus.Error) {
                        showDevicePickerDialog.value = true
                    }
                }
            }
        }

        // Start mDNS scan for TVs in background
        discoveryManager.startDiscovery()

        // Auto-connect to last saved IP address
        connectToTv(_currentIp.value, _currentName.value)
    }

    fun connectToTv(ip: String, name: String = DEFAULT_NAME) {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty()) return

        _currentIp.value = cleanIp
        _currentName.value = name

        prefs.edit()
            .putString(KEY_LAST_IP, cleanIp)
            .putString(KEY_LAST_NAME, name)
            .apply()

        showDevicePickerDialog.value = false

        viewModelScope.launch {
            try {
                activeController.connect(cleanIp)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to TV: ${e.message}", e)
            }
        }
    }

    fun finishPairing(pinCode: String) {
        showPinDialog.value = false
        viewModelScope.launch {
            try {
                v2Client.finishPairing(pinCode)
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed: ${e.message}", e)
            }
        }
    }

    fun setAdbMode(enabled: Boolean) {
        _isAdbMode.value = enabled
        prefs.edit().putBoolean(KEY_USE_ADB, enabled).apply()
        // Reconnect with chosen protocol
        connectToTv(_currentIp.value, _currentName.value)
    }

    fun setAmoledMode(enabled: Boolean) {
        _isAmoledMode.value = enabled
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    fun toggleAmoledMode() {
        setAmoledMode(!_isAmoledMode.value)
    }

    fun dismissTutorial() {
        showTutorial.value = false
        prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply()
    }

    fun openTutorial() {
        showTutorial.value = true
    }

    fun sendKey(key: RemoteKey, direction: KeyDirection = KeyDirection.SHORT) {
        activeController.sendKey(key, direction)
    }

    fun sendText(text: String) {
        activeController.sendText(text)
    }

    fun scanDevices() {
        discoveryManager.stopDiscovery()
        discoveryManager.startDiscovery()
    }

    fun reconnect() {
        connectToTv(_currentIp.value, _currentName.value)
    }

    fun rePair() {
        v2Client.forcePair(_currentIp.value)
    }

    fun onForeground() {
        connectToTv(_currentIp.value, _currentName.value)
    }

    fun onBackground() {
        // Release TV remote session so other phones/family members can connect
        activeController.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
        v2Client.disconnect()
        adbClient.disconnect()
    }
}
