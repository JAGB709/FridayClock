package com.jarvis.clock.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Graba una frase del micro con detección de fin de habla por energía
 * (VAD sencillo): calibra el ruido de fondo, espera a que hables y corta
 * tras ~1 s de silencio. Devuelve un WAV 16 kHz mono listo para Whisper.
 */
class VoiceRecorder {
    @Volatile private var cancelled = false

    fun cancel() { cancelled = true }

    /**
     * Bloqueante. [onLevel] recibe 0..1 para animar la UI.
     * Devuelve null si no se detectó voz o se canceló.
     */
    @SuppressLint("MissingPermission")
    fun record(onLevel: (Float) -> Unit = {}): ByteArray? {
        cancelled = false
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, FRAME * 2 * 4)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }

        val pcm = ByteArrayOutputStream(RATE * 2 * 6)
        val frame = ShortArray(FRAME)
        val bytes = ByteBuffer.allocate(FRAME * 2).order(ByteOrder.LITTLE_ENDIAN)
        var noise = 0.0
        var frames = 0
        var speechFrames = 0
        var silentFrames = 0
        var speaking = false
        val framesPerSec = RATE / FRAME
        try {
            rec.startRecording()
            while (!cancelled) {
                val n = rec.read(frame, 0, FRAME)
                if (n <= 0) break
                frames++
                var sum = 0.0
                for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                val rms = sqrt(sum / n)
                onLevel((rms / 6000.0).coerceIn(0.0, 1.0).toFloat())

                if (frames <= CALIBRATION_FRAMES) {
                    noise += rms / CALIBRATION_FRAMES
                } else {
                    val threshold = max(noise * 2.5, 350.0)
                    if (rms > threshold) {
                        speechFrames++
                        silentFrames = 0
                        if (speechFrames >= 3) speaking = true
                    } else {
                        silentFrames++
                        if (!speaking) speechFrames = 0
                    }
                }

                bytes.clear()
                for (i in 0 until n) bytes.putShort(frame[i])
                pcm.write(bytes.array(), 0, n * 2)

                if (speaking && silentFrames > framesPerSec * END_SILENCE_MS / 1000) break
                if (!speaking && frames > framesPerSec * NO_SPEECH_TIMEOUT_S) return null
                if (frames > framesPerSec * MAX_SECONDS) break
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        if (cancelled || !speaking) return null
        return wav(pcm.toByteArray())
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + pcm.size); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(RATE); b.putInt(RATE * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(pcm.size); b.put(pcm)
        return b.array()
    }

    companion object {
        const val RATE = 16_000
        private const val FRAME = 480 // 30 ms
        private const val CALIBRATION_FRAMES = 8
        private const val END_SILENCE_MS = 1100
        private const val NO_SPEECH_TIMEOUT_S = 7
        private const val MAX_SECONDS = 15
    }
}
