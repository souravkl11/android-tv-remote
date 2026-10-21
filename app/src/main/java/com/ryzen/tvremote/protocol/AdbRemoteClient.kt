package com.ryzen.tvremote.protocol

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight raw ADB protocol client implementation for direct Wi-Fi control (port 5555).
 * Dispatches `input keyevent` directly to the Android TV system shell.
 */
class AdbRemoteClient(
    private val scope: CoroutineScope
) : IRemoteController {

    companion object {
        private const val TAG = "AdbRemoteClient"
        const val ADB_PORT = 5555

        // ADB Protocol Message Commands
        private const val A_SYNC = 0x434e5953
        private const val A_CNXN = 0x4e584e43
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val A_AUTH = 0x48545541

        private const val A_VERSION = 0x01000000
        private const val MAX_PAYLOAD = 4096
    }

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _volumeState = MutableStateFlow(VolumeState())
    override val volumeState: StateFlow<VolumeState> = _volumeState.asStateFlow()

    private val _currentApp = MutableStateFlow("")
    override val currentApp: StateFlow<String> = _currentApp.asStateFlow()

    private var activeHost: String = ""
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var sessionJob: Job? = null
    private val sendLock = Any()
    private var localIdCounter = 1

    override suspend fun connect(host: String) {
        activeHost = host
        disconnect()

        sessionJob = scope.launch(Dispatchers.IO) {
            connectAdb(host)
        }
    }

    private suspend fun connectAdb(host: String) = withContext(Dispatchers.IO) {
        _status.value = ConnectionStatus.Connecting("Connecting to ADB at $host:$ADB_PORT...")
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, ADB_PORT), 4000)
            socket = s
            outputStream = s.getOutputStream()
            inputStream = s.getInputStream()

            // Send CNXN
            val banner = "host::tvremote\u0000".toByteArray(Charsets.UTF_8)
            writeAdbMessage(A_CNXN, A_VERSION, MAX_PAYLOAD, banner)

            // Read response
            val header = readAdbHeader()
            if (header != null && header.command == A_CNXN) {
                _status.value = ConnectionStatus.Connected(host, "Android TV (ADB)")
                Log.i(TAG, "Connected to Android TV ADB service at $host")
            } else if (header != null && header.command == A_AUTH) {
                _status.value = ConnectionStatus.Error("ADB authentication required. Please allow USB/network debugging on TV screen.")
            } else {
                _status.value = ConnectionStatus.Connected(host, "Android TV (ADB)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "ADB Connection error: ${e.message}", e)
            _status.value = ConnectionStatus.Error("ADB port 5555 unreachable: ${e.localizedMessage}")
        }
    }

    private data class AdbHeader(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataChecksum: Int,
        val magic: Int
    )

    private fun writeAdbMessage(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val out = outputStream ?: return
        val checksum = calculateChecksum(data)
        val magic = command xor -0x1

        val buffer = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(data.size)
        buffer.putInt(checksum)
        buffer.putInt(magic)
        buffer.put(data)

        synchronized(sendLock) {
            out.write(buffer.array())
            out.flush()
        }
    }

    private fun readAdbHeader(): AdbHeader? {
        val input = inputStream ?: return null
        val buffer = ByteArray(24)
        var readTotal = 0
        while (readTotal < 24) {
            val r = input.read(buffer, readTotal, 24 - readTotal)
            if (r == -1) return null
            readTotal += r
        }

        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = bb.getInt()
        val a0 = bb.getInt()
        val a1 = bb.getInt()
        val len = bb.getInt()
        val ck = bb.getInt()
        val mg = bb.getInt()

        // Read payload if any
        if (len > 0) {
            val payload = ByteArray(len)
            var pRead = 0
            while (pRead < len) {
                val pr = input.read(payload, pRead, len - pRead)
                if (pr == -1) break
                pRead += pr
            }
        }

        return AdbHeader(cmd, a0, a1, len, ck, mg)
    }

    private fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += b.toInt() and 0xFF
        }
        return sum
    }

    private fun sendShellCommand(cmd: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val localId = localIdCounter++
                val destination = "shell:$cmd\u0000".toByteArray(Charsets.UTF_8)
                writeAdbMessage(A_OPEN, localId, 0, destination)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send ADB shell command: ${e.message}")
            }
        }
    }

    override fun sendKey(key: RemoteKey) {
        sendShellCommand("input keyevent ${key.androidKeyCode}")
    }

    override fun sendText(text: String) {
        val escaped = text.replace(" ", "%s").replace("'", "\\'")
        sendShellCommand("input text '$escaped'")
    }

    override fun launchApp(appIdOrUrl: String) {
        if (appIdOrUrl.startsWith("http://") || appIdOrUrl.startsWith("https://")) {
            sendShellCommand("am start -a android.intent.action.VIEW -d '$appIdOrUrl'")
        } else {
            sendShellCommand("monkey -p $appIdOrUrl -c android.intent.category.LAUNCHER 1")
        }
    }

    override suspend fun finishPairing(pinCode: String) {
        // ADB does not use PIN code
    }

    override fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
        inputStream = null
        _status.value = ConnectionStatus.Disconnected
    }
}
