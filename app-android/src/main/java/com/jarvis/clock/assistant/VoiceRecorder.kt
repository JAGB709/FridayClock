package com.jarvis.clock.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
        var noise = Double.MAX_VALUE // mínimo del tramo de calibración (robusto a golpes)
        var window = 0.0
        var frames = 0
        var speechFrames = 0
        var silentFrames = 0
        var speaking = false
        var peak = 0.0
        val framesPerSec = RATE / FRAME
        MicGate.commandActive = true
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
                if (rms > window) window = rms
                if (frames % 15 == 0) { Log.i(TAG, "t=${frames * 30 / 1000f}s rms_max=${window.toInt()} ruido=${noise.toInt()}"); window = 0.0 }

                // Los primeros fotogramas son el golpe de arranque del micro digital (rms ~6000):
                // ni se evalúan ni se guardan, o falsean la calibración y el volumen.
                if (frames <= SKIP_FRAMES) continue

                if (frames <= SKIP_FRAMES + CALIBRATION_FRAMES) {
                    noise = min(noise, rms)
                } else {
                    val threshold = max(noise * NOISE_FACTOR, MIN_THRESHOLD)
                    if (rms > peak) peak = rms
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
            MicGate.commandActive = false
            Log.i(TAG, "frames=$frames ruido=${noise.toInt()} umbral=${max(noise * NOISE_FACTOR, MIN_THRESHOLD).toInt()} pico=${peak.toInt()} hablando=$speaking cancelado=$cancelled")
        }
        if (cancelled || !speaking) return null
        return wav(normalize(pcm.toByteArray()))
    }

    /**
     * Escucha continua para la palabra de activación. Bloqueante: devuelve el siguiente fragmento de
     * voz (con ~0,3 s previos) cuando termina, o null si [isActive] deja de cumplirse o el ruido fue
     * demasiado corto para ser una frase. Se llama en bucle; el micro se libera entre llamadas.
     */
    @SuppressLint("MissingPermission")
    fun recordUtterance(isActive: () -> Boolean): ByteArray? {
        cancelled = false
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, FRAME * 2 * 4)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }

        val pcm = ByteArrayOutputStream(RATE * 2 * 4)
        val frame = ShortArray(FRAME)
        val pre = ArrayDeque<ShortArray>()
        val bytes = ByteBuffer.allocate(FRAME * 2).order(ByteOrder.LITTLE_ENDIAN)
        val framesPerSec = RATE / FRAME
        fun put(f: ShortArray, n: Int) {
            bytes.clear(); for (i in 0 until n) bytes.putShort(f[i]); pcm.write(bytes.array(), 0, n * 2)
        }
        var noise = Double.MAX_VALUE
        var frames = 0
        var speechFrames = 0
        var silentFrames = 0
        var voiced = 0
        var speakingSince = 0
        var peakWindow = 0.0
        var speaking = false
        try {
            rec.startRecording()
            while (!cancelled && isActive()) {
                val n = rec.read(frame, 0, FRAME)
                if (n <= 0) break
                frames++
                var sum = 0.0
                for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                val rms = sqrt(sum / n)
                if (frames <= SKIP_FRAMES) continue // golpe de arranque del micro
                if (frames <= SKIP_FRAMES + CALIBRATION_FRAMES) { noise = min(noise, rms); continue }

                val threshold = max(noise * NOISE_FACTOR, MIN_THRESHOLD)
                if (rms > peakWindow) peakWindow = rms
                if (frames % 33 == 0) { Log.i(TAG, "escucha: rms_max=${peakWindow.toInt()} umbral=${threshold.toInt()} ruido=${noise.toInt()} hablando=$speaking"); peakWindow = 0.0 }
                if (!speaking) {
                    pre.addLast(frame.copyOf(n)); if (pre.size > PREROLL_FRAMES) pre.removeFirst()
                    if (rms > threshold) speechFrames++ else {
                        speechFrames = 0
                        noise = (noise * 0.995 + rms * 0.005).coerceIn(2.0, 400.0) // sigue al ruido de fondo
                    }
                    if (speechFrames >= 3) {
                        speaking = true; speakingSince = frames; silentFrames = 0; voiced = speechFrames
                        pre.forEach { put(it, it.size) }
                    }
                } else {
                    put(frame, n)
                    if (rms > threshold) { silentFrames = 0; voiced++ } else silentFrames++
                    if (silentFrames > framesPerSec * WAKE_END_SILENCE_MS / 1000) break
                    if (frames - speakingSince > framesPerSec * WAKE_MAX_SECONDS) break
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        if (!speaking || voiced < WAKE_MIN_VOICED_FRAMES) return null
        return wav(normalize(pcm.toByteArray()))
    }

    /** El micro de este reloj entrega la voz muy floja: se sube hasta un nivel que Whisper entienda bien. */
    private fun normalize(pcm: ByteArray): ByteArray {
        val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(pcm.size / 2) { bb.getShort(it * 2) }
        val top = samples.maxOfOrNull { abs(it.toInt()) } ?: return pcm
        if (top <= 0 || top >= TARGET_PEAK) return pcm
        val gain = min(MAX_GAIN, TARGET_PEAK.toDouble() / top)
        Log.i(TAG, "normalizo: pico=$top ganancia=x${"%.1f".format(gain)}")
        val out = ByteBuffer.allocate(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        for (v in samples) out.putShort((v * gain).toInt().coerceIn(-32768, 32767).toShort())
        return out.array()
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
        private const val TAG = "VoiceRecorder"
        const val RATE = 16_000
        private const val FRAME = 480 // 30 ms
        private const val SKIP_FRAMES = 10        // golpe de arranque del micro
        private const val CALIBRATION_FRAMES = 10
        private const val NOISE_FACTOR = 3.0
        private const val MIN_THRESHOLD = 25.0    // este micro es muy flojo: el ruido real ronda 3-6 y la voz 150-500
        private const val TARGET_PEAK = 16_000
        private const val PREROLL_FRAMES = 10       // 0,3 s previos al inicio de la voz
        private const val WAKE_END_SILENCE_MS = 700 // fin de frase en la escucha continua
        private const val WAKE_MAX_SECONDS = 7
        private const val WAKE_MIN_VOICED_FRAMES = 6 // <0,18 s de voz: un golpe, no una palabra
        private const val MAX_GAIN = 60.0
        private const val END_SILENCE_MS = 1100
        private const val NO_SPEECH_TIMEOUT_S = 7
        private const val MAX_SECONDS = 15
    }
}

/** Hay una captura de orden en curso: la escucha continua suelta el micro hasta que acabe. */
object MicGate {
    @Volatile var commandActive = false
}
