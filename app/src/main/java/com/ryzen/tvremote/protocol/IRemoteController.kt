package com.ryzen.tvremote.protocol

import kotlinx.coroutines.flow.SharedFlow
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

/** A text field focused on the TV: its current text and hint. */
data class ImeField(val value: String = "", val label: String = "")

enum class KeyDirection {
    SHORT,
    START_LONG,
    END_LONG
}

interface IRemoteController {
    val status: StateFlow<ConnectionStatus>
    val volumeState: StateFlow<VolumeState>
    val currentApp: StateFlow<String>
    /** The TV text field that wants a keyboard, or null when none is focused. */
    val imeField: StateFlow<ImeField?>
    val supportsVoice: Boolean
    /** Voice sessions the TV opens by itself, e.g. when an app's own mic button is pressed. */
    val voiceRequests: SharedFlow<Int>
    /** The TV couldn't open a link sent with [launchApp] (and hangs up on us when that happens). */
    val launchFailures: SharedFlow<String>
    /** The TV finished listening on its side. */
    val voiceEnded: SharedFlow<Unit>

    suspend fun connect(host: String)
    suspend fun finishPairing(pinCode: String)
    fun disconnect()

    fun sendKey(key: RemoteKey, direction: KeyDirection = KeyDirection.SHORT)
    fun sendKey(key: RemoteKey) = sendKey(key, KeyDirection.SHORT)
    fun sendText(text: String)
    fun launchApp(appIdOrUrl: String)

    /** Opens a voice session on the TV. Returns the session id, or null if the TV didn't accept one. */
    suspend fun startVoice(): Int?
    /** Takes a voice session the TV offered through [voiceRequests]. */
    fun acceptVoice(sessionId: Int)
    /** Streams 16-bit, 8 kHz, mono PCM to the open voice session. */
    fun sendVoice(samples: ByteArray, length: Int)
    fun endVoice()
}
