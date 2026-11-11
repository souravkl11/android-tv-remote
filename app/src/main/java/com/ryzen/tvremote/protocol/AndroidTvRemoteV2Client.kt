package com.ryzen.tvremote.protocol

import android.content.Context
import android.os.Build
import android.util.Log
import com.ryzen.tvremote.BuildConfig
import com.google.protobuf.ByteString
import com.ryzen.tvremote.protocol.pairing.PairingConfiguration
import com.ryzen.tvremote.protocol.pairing.PairingEncoding
import com.ryzen.tvremote.protocol.pairing.PairingMessage
import com.ryzen.tvremote.protocol.pairing.PairingOption
import com.ryzen.tvremote.protocol.pairing.PairingRequest
import com.ryzen.tvremote.protocol.pairing.PairingSecret
import com.ryzen.tvremote.protocol.pairing.RoleType
import com.ryzen.tvremote.protocol.remote.RemoteAppLinkLaunchRequest
import com.ryzen.tvremote.protocol.remote.RemoteConfigure
import com.ryzen.tvremote.protocol.remote.RemoteDeviceInfo
import com.ryzen.tvremote.protocol.remote.RemoteDirection
import com.ryzen.tvremote.protocol.remote.RemoteEditInfo
import com.ryzen.tvremote.protocol.remote.RemoteImeBatchEdit
import com.ryzen.tvremote.protocol.remote.RemoteImeObject
import com.ryzen.tvremote.protocol.remote.RemoteKeyCode
import com.ryzen.tvremote.protocol.remote.RemoteKeyInject
import com.ryzen.tvremote.protocol.remote.RemoteMessage
import com.ryzen.tvremote.protocol.remote.RemotePingResponse
import com.ryzen.tvremote.protocol.remote.RemoteSetActive
import com.ryzen.tvremote.protocol.remote.RemoteTextFieldStatus
import com.ryzen.tvremote.protocol.remote.RemoteVoiceBegin
import com.ryzen.tvremote.protocol.remote.RemoteVoiceEnd
import com.ryzen.tvremote.protocol.remote.RemoteVoicePayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

class AndroidTvRemoteV2Client(
    private val context: Context,
    private val scope: CoroutineScope
) : IRemoteController {

    companion object {
        private const val TAG = "TvRemoteV2Client"
        const val DEFAULT_PAIR_PORT = 6467
        const val DEFAULT_REMOTE_PORT = 6466
        private const val CLIENT_NAME = "Android TV Remote"

        // Feature bits exchanged in RemoteConfigure / RemoteSetActive.
        private const val FEATURE_PING = 1
        private const val FEATURE_KEY = 2
        private const val FEATURE_IME = 4
        private const val FEATURE_VOICE = 8
        private const val FEATURE_POWER = 32
        private const val FEATURE_VOLUME = 64
        private const val FEATURE_APP_LINK = 512
        private const val CLIENT_FEATURES =
            FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_VOICE or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK

        /** TVs drop short voice payloads, so the last chunk is padded with silence to a full one. */
        private const val VOICE_CHUNK_MIN_BYTES = 20 * 1024
        private const val VOICE_BEGIN_TIMEOUT_MS = 2500L

        // The TV acknowledges each text edit with a new field counter. Edits sent before that
        // carry a stale counter and get mangled, so they're queued until the ack (or a timeout).
        private const val IME_TEXT_ACK_TIMEOUT_MS = 500L
        private const val IME_DELETE_GAP_MS = 120L
    }

    private sealed interface ImeOp {
        data class Text(val text: String) : ImeOp
        data object Delete : ImeOp
    }

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _volumeState = MutableStateFlow(VolumeState())
    override val volumeState: StateFlow<VolumeState> = _volumeState.asStateFlow()

    private val _currentApp = MutableStateFlow("")
    override val currentApp: StateFlow<String> = _currentApp.asStateFlow()

    private var activeHost: String = ""
    private var activeDeviceName: String = "Android TV"

    private var remoteSocket: SSLSocket? = null
    private var remoteOut: OutputStream? = null
    private val sendLock = Any()

    private var pairingSocket: SSLSocket? = null
    private var pairingServerCert: X509Certificate? = null
    private var pairingDeferred: CompletableDeferred<Boolean>? = null

    private var sessionJob: Job? = null
    private var sendChannel = Channel<RemoteMessage>(Channel.UNLIMITED)
    private var sendJob: Job? = null
    private var imeCounter = 0
    private var fieldCounter = 0
    // Counters from remote_ime_key_inject / remote_ime_show_request win over the batch-edit echo.
    private var hasImeCounter = false
    private var hasFieldCounter = false
    private var activeFeatures = CLIENT_FEATURES

    private val _imeField = MutableStateFlow<ImeField?>(null)
    override val imeField: StateFlow<ImeField?> = _imeField.asStateFlow()

    private val imeLock = Any()
    private val imeQueue = ArrayDeque<ImeOp>()
    private var imeSeq = 0
    /** Sequence number of the edit waiting for the TV's ack, or 0 when idle. */
    private var imeAwaiting = 0

    private val _voiceRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    override val voiceRequests: SharedFlow<Int> = _voiceRequests.asSharedFlow()

    private val _launchFailures = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val launchFailures: SharedFlow<String> = _launchFailures.asSharedFlow()

    private val _voiceEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val voiceEnded: SharedFlow<Unit> = _voiceEnded.asSharedFlow()

    private var voiceBegin: CompletableDeferred<Int>? = null
    @Volatile
    private var voiceSessionId: Int? = null

    /** Bumped on every connect/disconnect so a stale session can't overwrite the live status. */
    @Volatile
    private var sessionGen = 0

    override suspend fun connect(host: String) {
        activeHost = host
        disconnect()

        sessionJob = scope.launch(Dispatchers.IO) {
            connectInternal(host)
        }
    }

    fun forcePair(host: String) {
        KeyManagerHelper.setHostPaired(context, host, false)
        activeHost = host
        disconnect()
        sessionJob = scope.launch(Dispatchers.IO) {
            startPairingFlow(host)
        }
    }

    private suspend fun connectInternal(host: String) {
        val gen = sessionGen
        _status.value = ConnectionStatus.Connecting("Connecting to $host:$DEFAULT_REMOTE_PORT...")

        try {
            val sslContext = KeyManagerHelper.getOrCreateSslContext(context, CLIENT_NAME)
            val socket = sslContext.socketFactory.createSocket() as SSLSocket
            socket.soTimeout = 0 // Infinite read timeout for persistent connection
            socket.keepAlive = true
            socket.tcpNoDelay = true

            socket.connect(InetSocketAddress(host, DEFAULT_REMOTE_PORT), 5000)
            socket.startHandshake()

            // Extract TV name from certificate if available
            val certs = socket.session.peerCertificates
            if (certs.isNotEmpty() && certs[0] is X509Certificate) {
                val cert = certs[0] as X509Certificate
                val cn = cert.subjectX500Principal.name
                val parts = cn.split("/")
                if (parts.size > 2) {
                    activeDeviceName = parts[parts.size - 2]
                }
            }

            remoteSocket = socket
            remoteOut = socket.outputStream
            val inputStream = socket.inputStream

            // Outbound message queue processed on Dispatchers.IO to guarantee FIFO and avoid NetworkOnMainThreadException
            sendJob?.cancel()
            val channel = Channel<RemoteMessage>(Channel.UNLIMITED)
            sendChannel = channel
            sendJob = scope.launch(Dispatchers.IO) {
                for (msg in channel) {
                    val out = remoteOut ?: break
                    try {
                        synchronized(sendLock) {
                            msg.writeDelimitedTo(out)
                            out.flush()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing message to remote socket: ${e.message}", e)
                        if (remoteSocket?.isConnected != true) {
                            break
                        }
                    }
                }
            }

            _status.value = ConnectionStatus.Connecting("Configuring session...")
            readRemoteLoop(inputStream, gen)

        } catch (e: Exception) {
            if (gen != sessionGen) return // superseded by a newer session
            Log.w(TAG, "Failed to connect to remote service on port $DEFAULT_REMOTE_PORT: ${e.message}")
            val alreadyPaired = KeyManagerHelper.isHostPaired(context, host)
            if (!alreadyPaired && (e is SSLException || e.cause is SSLException || e.message?.contains("handshake", ignoreCase = true) == true)) {
                // First-time pairing required!
                startPairingFlow(host)
            } else {
                if (alreadyPaired) {
                    _status.value = ConnectionStatus.Error("Can't connect. The TV may be off, busy with another remote, or no longer paired. Try Retry or Re-pair.")
                } else {
                    _status.value = ConnectionStatus.Error("Cannot reach TV at $host: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun startPairingFlow(host: String) = withContext(Dispatchers.IO) {
        _status.value = ConnectionStatus.Connecting("Initiating pairing on port $DEFAULT_PAIR_PORT...")
        try {
            val sslContext = KeyManagerHelper.getOrCreateSslContext(context, CLIENT_NAME)
            val socket = sslContext.socketFactory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, DEFAULT_PAIR_PORT), 5000)
            socket.startHandshake()

            val peerCerts = socket.session.peerCertificates
            if (peerCerts.isEmpty() || peerCerts[0] !is X509Certificate) {
                throw IllegalStateException("No peer certificate received from TV during pairing")
            }
            pairingServerCert = peerCerts[0] as X509Certificate
            pairingSocket = socket

            val output = socket.outputStream
            val input = socket.inputStream

            // Step 1: Send PairingRequest
            val reqMsg = PairingMessage.newBuilder().apply {
                protocolVersion = 2
                status = PairingMessage.Status.STATUS_OK
                pairingRequest = PairingRequest.newBuilder().apply {
                    clientName = CLIENT_NAME
                    serviceName = "atvremote"
                }.build()
            }.build()
            reqMsg.writeDelimitedTo(output)
            output.flush()

            // Step 2: Receive PairingRequestAck
            val ackMsg = PairingMessage.parseDelimitedFrom(input)
                ?: throw IllegalStateException("Connection closed waiting for PairingRequestAck")
            if (!ackMsg.hasPairingRequestAck()) {
                throw IllegalStateException("Expected PairingRequestAck, got: $ackMsg")
            }

            // Step 3: Send PairingOption
            val optMsg = PairingMessage.newBuilder().apply {
                protocolVersion = 2
                status = PairingMessage.Status.STATUS_OK
                pairingOption = PairingOption.newBuilder().apply {
                    preferredRole = RoleType.ROLE_TYPE_INPUT
                    addInputEncodings(
                        PairingEncoding.newBuilder().apply {
                            type = PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL
                            symbolLength = 6
                        }.build()
                    )
                }.build()
            }.build()
            optMsg.writeDelimitedTo(output)
            output.flush()

            // Step 4: Receive PairingOption from TV
            val tvOptMsg = PairingMessage.parseDelimitedFrom(input)
                ?: throw IllegalStateException("Connection closed waiting for PairingOption")
            if (!tvOptMsg.hasPairingOption()) {
                throw IllegalStateException("Expected PairingOption, got: $tvOptMsg")
            }

            // Step 5: Send PairingConfiguration
            val cfgMsg = PairingMessage.newBuilder().apply {
                protocolVersion = 2
                status = PairingMessage.Status.STATUS_OK
                pairingConfiguration = PairingConfiguration.newBuilder().apply {
                    clientRole = RoleType.ROLE_TYPE_INPUT
                    encoding = PairingEncoding.newBuilder().apply {
                        type = PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL
                        symbolLength = 6
                    }.build()
                }.build()
            }.build()
            cfgMsg.writeDelimitedTo(output)
            output.flush()

            // Step 6: Receive PairingConfigurationAck
            val cfgAck = PairingMessage.parseDelimitedFrom(input)
                ?: throw IllegalStateException("Connection closed waiting for PairingConfigurationAck")
            if (!cfgAck.hasPairingConfigurationAck()) {
                throw IllegalStateException("Expected PairingConfigurationAck, got: $cfgAck")
            }

            // TV is now displaying the 6-character PIN!
            Log.i(TAG, "TV is now displaying the pairing PIN on-screen.")
            _status.value = ConnectionStatus.NeedsPairing()

        } catch (e: Exception) {
            Log.e(TAG, "Error during pairing initialization: ${e.message}", e)
            closePairingSocket()
            _status.value = ConnectionStatus.Error("Pairing initiation failed: ${e.localizedMessage}")
        }
    }

    override suspend fun finishPairing(pinCode: String): Unit = withContext(Dispatchers.IO) {
        val socket = pairingSocket
        val serverCert = pairingServerCert
        if (socket == null || serverCert == null) {
            _status.value = ConnectionStatus.Error("Pairing session expired. Please connect again.")
            return@withContext
        }

        try {
            val clientCert = KeyManagerHelper.getClientCertificate(context)
            val secretBytes = KeyManagerHelper.computePairingSecret(clientCert, serverCert, pinCode)

            val output = socket.outputStream
            val input = socket.inputStream

            // Step 7: Send PairingSecret
            val secretMsg = PairingMessage.newBuilder().apply {
                protocolVersion = 2
                status = PairingMessage.Status.STATUS_OK
                pairingSecret = PairingSecret.newBuilder().apply {
                    secret = ByteString.copyFrom(secretBytes)
                }.build()
            }.build()
            secretMsg.writeDelimitedTo(output)
            output.flush()

            // Step 8: Receive PairingSecretAck
            val secretAck = PairingMessage.parseDelimitedFrom(input)
                ?: throw IllegalStateException("Connection closed waiting for PairingSecretAck")
            if (!secretAck.hasPairingSecretAck()) {
                throw IllegalStateException("Pairing failed, status: ${secretAck.status}")
            }

            Log.i(TAG, "Pairing verified successfully!")
            KeyManagerHelper.setHostPaired(context, activeHost, true)
            closePairingSocket()

            // Immediate reconnect on Port 6466 with saved credentials in background session!
            delay(300)
            sessionJob?.cancel()
            sessionGen++
            sessionJob = scope.launch(Dispatchers.IO) {
                connectInternal(activeHost)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to finish pairing: ${e.message}", e)
            closePairingSocket()
            _status.value = ConnectionStatus.Error("Incorrect PIN or pairing failed: ${e.localizedMessage}")
        }
    }

    private fun closePairingSocket() {
        try {
            pairingSocket?.close()
        } catch (_: Exception) {}
        pairingSocket = null
        pairingServerCert = null
    }

    private suspend fun readRemoteLoop(input: InputStream, gen: Int) {
        while (currentCoroutineContext().isActive && gen == sessionGen && remoteSocket?.isConnected == true) {
            val msg = try {
                RemoteMessage.parseDelimitedFrom(input)
            } catch (e: Exception) {
                Log.w(TAG, "Remote socket stream ended: ${e.message}")
                break
            } ?: break

            if (BuildConfig.DEBUG && !msg.hasRemotePingRequest()) {
                Log.v(TAG, "<< " + msg.toString().replace(Regex("\\s+"), " "))
            }

            when {
                msg.hasRemoteConfigure() -> {
                    // Reply with the features both sides support.
                    val tvFeatures = msg.remoteConfigure.code1
                    activeFeatures = if (tvFeatures != 0) CLIENT_FEATURES and tvFeatures else CLIENT_FEATURES
                    Log.i(TAG, "TV features=$tvFeatures, active=$activeFeatures")
                    val reply = RemoteMessage.newBuilder().apply {
                        remoteConfigure = RemoteConfigure.newBuilder().apply {
                            code1 = activeFeatures
                            deviceInfo = RemoteDeviceInfo.newBuilder().apply {
                                model = Build.MODEL ?: "Android Phone"
                                vendor = Build.MANUFACTURER ?: "Google"
                                unknown1 = 1
                                unknown2 = "1"
                                packageName = "atvremote"
                                appVersion = "1.0.0"
                            }.build()
                        }.build()
                    }.build()
                    sendRaw(reply)
                    _status.value = ConnectionStatus.Connected(activeHost, activeDeviceName)
                    Log.i(TAG, "Configured session with TV, marked as Connected: $activeDeviceName ($activeHost)")
                }

                msg.hasRemoteSetActive() -> {
                    val reply = RemoteMessage.newBuilder().apply {
                        remoteSetActive = RemoteSetActive.newBuilder().apply {
                            active = activeFeatures
                        }.build()
                    }.build()
                    sendRaw(reply)
                    _status.value = ConnectionStatus.Connected(activeHost, activeDeviceName)
                }

                msg.hasRemoteStart() -> {
                    _status.value = ConnectionStatus.Connected(activeHost, activeDeviceName)
                    Log.i(TAG, "Android TV Remote Session active! Connected to $activeDeviceName ($activeHost)")
                }

                msg.hasRemotePingRequest() -> {
                    val reply = RemoteMessage.newBuilder().apply {
                        remotePingResponse = RemotePingResponse.newBuilder().apply {
                            val1 = msg.remotePingRequest.val1
                        }.build()
                    }.build()
                    sendRaw(reply)
                }

                msg.hasRemoteSetVolumeLevel() -> {
                    val vol = msg.remoteSetVolumeLevel
                    _volumeState.value = VolumeState(
                        level = vol.volumeLevel,
                        max = vol.volumeMax,
                        isMuted = vol.volumeMuted
                    )
                }

                msg.hasRemoteImeKeyInject() -> {
                    val inject = msg.remoteImeKeyInject
                    val pkg = inject.appInfo.appPackage
                    if (!pkg.isNullOrEmpty()) {
                        _currentApp.value = pkg
                    }
                    val focused = inject.hasTextFieldStatus() && inject.textFieldStatus.counterField != 0
                    if (inject.appInfo.counter != 0) {
                        imeCounter = inject.appInfo.counter
                        hasImeCounter = true
                    }
                    if (focused) {
                        fieldCounter = inject.textFieldStatus.counterField
                        hasFieldCounter = true
                        _imeField.value = inject.textFieldStatus.toImeField()
                    } else {
                        // A key inject without a text field means focus left the field.
                        _imeField.value = null
                        clearImeQueue()
                    }
                    Log.d(TAG, "IME key inject: app=$pkg focused=$focused ime=$imeCounter field=$fieldCounter")
                }

                msg.hasRemoteImeShowRequest() -> {
                    val show = msg.remoteImeShowRequest
                    if (show.hasRemoteTextFieldStatus()) {
                        val status = show.remoteTextFieldStatus
                        fieldCounter = status.counterField
                        hasFieldCounter = true
                        Log.d(TAG, "TV text field status: field=${status.counterField} label='${status.label}'")
                        _imeField.value = status.toImeField()
                        onImeAck()
                    } else {
                        Log.d(TAG, "TV text field closed")
                        _imeField.value = null
                        clearImeQueue()
                    }
                }

                msg.hasRemoteImeBatchEdit() -> {
                    val edit = msg.remoteImeBatchEdit
                    if (edit.imeCounter == 0 && edit.fieldCounter == 0 && edit.editInfoCount == 0) {
                        // An empty batch edit is sent when the TV keyboard goes away.
                        Log.d(TAG, "TV keyboard hidden")
                        _imeField.value = null
                        clearImeQueue()
                    } else {
                        if (!hasImeCounter) imeCounter = edit.imeCounter
                        if (!hasFieldCounter) fieldCounter = edit.fieldCounter
                        // The same field got focus again (no key inject is sent for that).
                        if (_imeField.value == null && edit.imeCounter != 0) _imeField.value = ImeField()
                    }
                }

                msg.hasRemoteVoiceBegin() -> {
                    val pending = voiceBegin
                    if (pending != null && !pending.isCompleted) {
                        pending.complete(msg.remoteVoiceBegin.sessionId)
                    } else if (voiceSessionId == null) {
                        // An app on the TV (e.g. YouTube's mic button) wants audio from the remote.
                        Log.i(TAG, "TV asked for voice: session ${msg.remoteVoiceBegin.sessionId} from '${msg.remoteVoiceBegin.packageName}'")
                        _voiceRequests.tryEmit(msg.remoteVoiceBegin.sessionId)
                    }
                }

                // The TV stops listening (speech recognised or timed out) by echoing the session back.
                msg.hasRemoteVoiceEnd() || msg.hasRemoteVoicePayload() -> {
                    val ended = if (msg.hasRemoteVoiceEnd()) msg.remoteVoiceEnd.sessionId else msg.remoteVoicePayload.sessionId
                    if (ended == voiceSessionId) {
                        Log.i(TAG, "TV closed voice session $ended")
                        _voiceEnded.tryEmit(Unit)
                    }
                }

                msg.hasRemoteError() -> {
                    Log.w(TAG, "TV reported an error: ${msg.remoteError}")
                    val failed = msg.remoteError.message
                    if (failed.hasRemoteAppLinkLaunchRequest()) {
                        _launchFailures.tryEmit(failed.remoteAppLinkLaunchRequest.appLink)
                    }
                }
            }
        }

        if (gen == sessionGen) {
            _imeField.value = null
            voiceBegin?.cancel()
            voiceSessionId = null
        }

        // Connection dropped (only report it if this is still the live session)
        if (gen == sessionGen) _status.value = ConnectionStatus.Disconnected
    }

    private fun sendRaw(msg: RemoteMessage) {
        val out = remoteOut
        val sock = remoteSocket
        if (out == null || sock == null || !sock.isConnected) {
            Log.w(TAG, "Cannot send raw message: remote socket not connected (out=$out, sock=${sock?.isConnected})")
            return
        }
        sendChannel.trySend(msg)
    }

    override fun sendKey(key: RemoteKey, direction: KeyDirection) {
        // Deletes are ordered with queued text so "ab⌫" never removes the wrong character.
        if (key == RemoteKey.BACKSPACE && direction == KeyDirection.SHORT) {
            enqueueIme(ImeOp.Delete)
        } else {
            sendKeyNow(key, direction)
        }
    }

    private fun sendKeyNow(key: RemoteKey, direction: KeyDirection) {
        val out = remoteOut
        if (out == null || remoteSocket?.isConnected != true) {
            Log.w(TAG, "Cannot send key ${key.displayName}: remote socket not connected (out=$out, connected=${remoteSocket?.isConnected})")
            return
        }

        val protoDirection = when (direction) {
            KeyDirection.SHORT -> RemoteDirection.SHORT
            KeyDirection.START_LONG -> RemoteDirection.START_LONG
            KeyDirection.END_LONG -> RemoteDirection.END_LONG
        }

        val msg = RemoteMessage.newBuilder().apply {
            remoteKeyInject = RemoteKeyInject.newBuilder().apply {
                keyCode = key.protoKeyCode
                this.direction = protoDirection
            }.build()
        }.build()

        sendRaw(msg)
        Log.d(TAG, "Dispatched key: ${key.displayName} (${key.protoKeyCode}) direction=$direction to TV")
    }

    override fun sendText(text: String) {
        if (text.isEmpty() || _status.value !is ConnectionStatus.Connected) return
        enqueueIme(ImeOp.Text(text))
    }

    private fun enqueueIme(op: ImeOp) {
        synchronized(imeLock) {
            val last = imeQueue.lastOrNull()
            if (op is ImeOp.Text && last is ImeOp.Text) {
                imeQueue.removeLast()
                imeQueue.addLast(ImeOp.Text(last.text + op.text))
            } else {
                imeQueue.addLast(op)
            }
            if (imeAwaiting == 0) pumpImeLocked()
        }
    }

    /** Sends the next queued edit and waits for its ack (or a short gap for deletes). */
    private fun pumpImeLocked() {
        val op = imeQueue.removeFirstOrNull()
        if (op == null) {
            imeAwaiting = 0
            return
        }
        val wait = when (op) {
            is ImeOp.Text -> {
                sendEditNow(op.text)
                IME_TEXT_ACK_TIMEOUT_MS
            }
            ImeOp.Delete -> {
                sendKeyNow(RemoteKey.BACKSPACE, KeyDirection.SHORT)
                IME_DELETE_GAP_MS
            }
        }
        val seq = ++imeSeq
        imeAwaiting = seq
        scope.launch {
            delay(wait)
            synchronized(imeLock) {
                if (imeAwaiting == seq) pumpImeLocked()
            }
        }
    }

    private fun onImeAck() {
        synchronized(imeLock) {
            if (imeAwaiting != 0) pumpImeLocked()
        }
    }

    private fun clearImeQueue() {
        synchronized(imeLock) {
            imeQueue.clear()
            imeAwaiting = 0
        }
    }

    private fun sendEditNow(text: String) {
        // Inserted at the TV's cursor, which ends up after the text.
        val imeObj = RemoteImeObject.newBuilder().apply {
            start = text.length
            end = text.length
            value = text
        }.build()

        val editInfo = RemoteEditInfo.newBuilder().apply {
            insert = 1
            textFieldStatus = imeObj
        }.build()

        val batchEdit = RemoteImeBatchEdit.newBuilder().apply {
            imeCounter = this@AndroidTvRemoteV2Client.imeCounter
            fieldCounter = this@AndroidTvRemoteV2Client.fieldCounter
            addEditInfo(editInfo)
        }.build()

        val msg = RemoteMessage.newBuilder().apply {
            remoteImeBatchEdit = batchEdit
        }.build()

        sendRaw(msg)
        Log.d(TAG, "Dispatched ${text.length} chars to TV (ime=$imeCounter field=$fieldCounter)")
    }

    override val supportsVoice: Boolean
        get() = activeFeatures and FEATURE_VOICE != 0

    override suspend fun startVoice(): Int? {
        if (_status.value !is ConnectionStatus.Connected || voiceSessionId != null) return null
        val pending = CompletableDeferred<Int>()
        voiceBegin = pending
        // KEYCODE_SEARCH asks the TV to open a voice session; it answers with remote_voice_begin.
        sendKey(RemoteKey.SEARCH)
        val sessionId = withTimeoutOrNull(VOICE_BEGIN_TIMEOUT_MS) { pending.await() }
        voiceBegin = null
        if (sessionId == null) {
            Log.w(TAG, "TV did not open a voice session")
            return null
        }
        sendRaw(RemoteMessage.newBuilder().apply {
            remoteVoiceBegin = RemoteVoiceBegin.newBuilder().setSessionId(sessionId).build()
        }.build())
        voiceSessionId = sessionId
        Log.i(TAG, "Voice session $sessionId open")
        return sessionId
    }

    override fun acceptVoice(sessionId: Int) {
        if (voiceSessionId != null) return
        sendRaw(RemoteMessage.newBuilder().apply {
            remoteVoiceBegin = RemoteVoiceBegin.newBuilder().setSessionId(sessionId).build()
        }.build())
        voiceSessionId = sessionId
        Log.i(TAG, "Voice session $sessionId accepted")
    }

    override fun sendVoice(samples: ByteArray, length: Int) {
        val session = voiceSessionId ?: return
        if (length <= 0) return
        val padded = if (length < VOICE_CHUNK_MIN_BYTES) {
            ByteArray(VOICE_CHUNK_MIN_BYTES).also { System.arraycopy(samples, 0, it, 0, length) }
        } else {
            samples.copyOf(length)
        }
        sendRaw(RemoteMessage.newBuilder().apply {
            remoteVoicePayload = RemoteVoicePayload.newBuilder()
                .setSessionId(session)
                .setSamples(ByteString.copyFrom(padded))
                .build()
        }.build())
    }

    override fun endVoice() {
        val session = voiceSessionId ?: return
        voiceSessionId = null
        sendRaw(RemoteMessage.newBuilder().apply {
            remoteVoiceEnd = RemoteVoiceEnd.newBuilder().setSessionId(session).build()
        }.build())
        Log.i(TAG, "Voice session $session closed")
    }

    private fun RemoteTextFieldStatus.toImeField() = ImeField(value = value, label = label)

    override fun launchApp(appIdOrUrl: String) {
        if (_status.value !is ConnectionStatus.Connected) return

        val targetUrl = if (appIdOrUrl.contains("://")) {
            appIdOrUrl
        } else {
            "market://launch?id=$appIdOrUrl"
        }

        val msg = RemoteMessage.newBuilder().apply {
            remoteAppLinkLaunchRequest = RemoteAppLinkLaunchRequest.newBuilder().apply {
                appLink = targetUrl
            }.build()
        }.build()

        sendRaw(msg)
    }

    override fun disconnect() {
        sessionGen++
        sessionJob?.cancel()
        sessionJob = null
        sendJob?.cancel()
        sendJob = null
        try {
            sendChannel.close()
        } catch (_: Exception) {}
        closePairingSocket()

        try {
            remoteSocket?.close()
        } catch (_: Exception) {}
        remoteSocket = null
        remoteOut = null

        _status.value = ConnectionStatus.Disconnected
    }
}
