package com.ryzen.tvremote.protocol

import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionStatus {
    object Disconnected : ConnectionStatus
    data class Connecting(val message: String) : ConnectionStatus
    data class NeedsPairing(val serverCertHash: String? = null) : ConnectionStatus
    data class Connected(val host: String, val deviceName: String) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

data class VolumeState(
    val level: Int = 0,
    val max: Int = 100,
    val isMuted: Boolean = false
)

interface IRemoteController {
    val status: StateFlow<ConnectionStatus>
    val volumeState: StateFlow<VolumeState>
    val currentApp: StateFlow<String>

    suspend fun connect(host: String)
    suspend fun finishPairing(pinCode: String)
    fun disconnect()

    fun sendKey(key: RemoteKey)
    fun sendText(text: String)
    fun launchApp(appIdOrUrl: String)
}
