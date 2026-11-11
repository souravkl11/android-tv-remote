package com.ryzen.tvremote.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

/**
 * Records the phone's microphone as 16-bit, 8 kHz, mono PCM — the format the Android TV remote
 * protocol expects — and hands it over in [CHUNK_BYTES] pieces.
 */
class VoiceRecorder {

    companion object {
        private const val TAG = "VoiceRecorder"
        const val SAMPLE_RATE = 8000
        /** 1.28 s of audio per payload. TVs drop smaller payloads (8 KB was silently lost on a TCL set). */
        const val CHUNK_BYTES = 20 * 1024
        /** Level updates are read in shorter slices so the on-screen meter stays lively. */
        private const val SLICE_BYTES = 1024

        // The TV decides when the user has finished and closes the session; hanging up first
        // cuts the app's mic input off before it has a result. These limits are only a backstop
        // for when the TV never says it's done.
        private const val MAX_MS = 15_000L
        private const val NO_SPEECH_MS = 8_000L
        private const val TRAILING_SILENCE_MS = 5_000L
        private const val SPEECH_RMS = 900.0
    }

    @Volatile
    private var stopRequested = false

    fun stop() {
        stopRequested = true
    }

    /**
     * Records until [stop], a pause after speech, or the time limit. Blocks the calling thread.
     * [onLevel] gets a 0..1 loudness value for each slice; [onChunk] gets audio to send.
     * Returns false if the microphone couldn't be opened.
     */
    @SuppressLint("MissingPermission") // Checked by the caller before recording.
    suspend fun record(
        onLevel: (Float) -> Unit,
        onChunk: (ByteArray, Int) -> Unit,
        replay: ByteArray? = null,
        replayRate: Int = SAMPLE_RATE
    ): Boolean {
        stopRequested = false
        if (replay != null) return replay(replay, replayRate, onLevel, onChunk)
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK_BYTES * 2)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't create AudioRecord: ${e.message}")
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        val chunk = ByteArray(CHUNK_BYTES)
        var filled = 0
        val slice = ByteArray(SLICE_BYTES)
        val sliceMs = SLICE_BYTES * 1000L / (SAMPLE_RATE * 2)
        var elapsed = 0L
        var heardSpeech = false
        var quietFor = 0L

        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive && !stopRequested) {
                val read = recorder.read(slice, 0, slice.size)
                if (read <= 0) break

                val rms = rms(slice, read)
                onLevel((rms / 4000.0).coerceIn(0.0, 1.0).toFloat())

                var offset = 0
                while (offset < read) {
                    val n = minOf(read - offset, CHUNK_BYTES - filled)
                    System.arraycopy(slice, offset, chunk, filled, n)
                    filled += n
                    offset += n
                    if (filled == CHUNK_BYTES) {
                        onChunk(chunk, filled)
                        filled = 0
                    }
                }

                elapsed += sliceMs
                if (rms > SPEECH_RMS) {
                    heardSpeech = true
                    quietFor = 0
                } else {
                    quietFor += sliceMs
                }
                val done = elapsed >= MAX_MS ||
                    (!heardSpeech && elapsed >= NO_SPEECH_MS) ||
                    (heardSpeech && quietFor >= TRAILING_SILENCE_MS)
                if (done) break
            }
            if (filled > 0) onChunk(chunk, filled)
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {}
            recorder.release()
        }
        return true
    }

    /** Debug aid: streams a recorded PCM clip in real time instead of the microphone. */
    private suspend fun replay(pcm: ByteArray, rate: Int, onLevel: (Float) -> Unit, onChunk: (ByteArray, Int) -> Unit): Boolean {
        var offset = 0
        while (offset < pcm.size && !stopRequested && currentCoroutineContext().isActive) {
            val n = minOf(CHUNK_BYTES, pcm.size - offset)
            val chunk = pcm.copyOfRange(offset, offset + n)
            onLevel((rms(chunk, n) / 4000.0).coerceIn(0.0, 1.0).toFloat())
            onChunk(chunk, n)
            offset += n
            kotlinx.coroutines.delay(n * 1000L / (rate * 2))
        }
        // Then silence, like a quiet room, until the TV closes the session.
        val quiet = ByteArray(CHUNK_BYTES)
        var waited = 0L
        while (!stopRequested && currentCoroutineContext().isActive && waited < TRAILING_SILENCE_MS) {
            onLevel(0f)
            onChunk(quiet, quiet.size)
            val ms = CHUNK_BYTES * 1000L / (rate * 2)
            kotlinx.coroutines.delay(ms)
            waited += ms
        }
        return true
    }

    private fun rms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += sample.toDouble() * sample
            count++
            i += 2
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }
}
