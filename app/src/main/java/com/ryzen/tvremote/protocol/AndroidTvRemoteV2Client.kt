package com.ryzen.tvremote.protocol

import android.content.Context
import android.os.Build
import android.util.Log
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    }

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _volumeState = MutableStateFlow(VolumeState())
    override val volumeState: StateFlow<VolumeState> = _volumeState.asStateFlow()

    private val _currentApp = MutableStateFlow("")
    override val currentApp: StateFlow<String> = _currentApp.asStateFlow()

    private var activeHost: String = ""
    private var activeDeviceName: String = "Living Room TV"

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
            readRemoteLoop(inputStream)

        } catch (e: Exception) {
            Log.w(TAG, "Failed to connect to remote service on port $DEFAULT_REMOTE_PORT: ${e.message}")
            val alreadyPaired = KeyManagerHelper.isHostPaired(context, host)
            if (!alreadyPaired && (e is SSLException || e.cause is SSLException || e.message?.contains("handshake", ignoreCase = true) == true)) {
                // First-time pairing required!
                startPairingFlow(host)
            } else {
                if (alreadyPaired) {
                    _status.value = ConnectionStatus.Error("TV session busy (another device may be connected). Retrying...")
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

    private suspend fun readRemoteLoop(input: InputStream) {
        val out = remoteOut ?: return

        while (scope.isActive && remoteSocket?.isConnected == true) {
            val msg = try {
                RemoteMessage.parseDelimitedFrom(input)
            } catch (e: Exception) {
                Log.w(TAG, "Remote socket stream ended: ${e.message}")
                break
            } ?: break

            when {
                msg.hasRemoteConfigure() -> {
                    // Send client configuration back
                    val reply = RemoteMessage.newBuilder().apply {
                        remoteConfigure = RemoteConfigure.newBuilder().apply {
                            code1 = 622
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
                            active = 622
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
                    val pkg = msg.remoteImeKeyInject.appInfo.appPackage
                    if (!pkg.isNullOrEmpty()) {
                        _currentApp.value = pkg
                    }
                }

                msg.hasRemoteImeShowRequest() -> {
                    val status = msg.remoteImeShowRequest.remoteTextFieldStatus
                    fieldCounter = status.counterField
                    Log.d(TAG, "TV requested IME show: counter=${status.counterField}, label=${status.label}, val=${status.value}")
                }

                msg.hasRemoteImeBatchEdit() -> {
                    imeCounter = msg.remoteImeBatchEdit.imeCounter
                    fieldCounter = msg.remoteImeBatchEdit.fieldCounter
                }
            }
        }

        // Connection dropped
        _status.value = ConnectionStatus.Disconnected
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

        val imeObj = RemoteImeObject.newBuilder().apply {
            start = text.length
            end = text.length
            value = text
        }.build()

        val editInfo = RemoteEditInfo.newBuilder().apply {
            insert = 0
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
        Log.d(TAG, "Dispatched text '$text' to TV")
    }

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
