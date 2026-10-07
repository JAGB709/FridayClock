package com.jarvis.clock.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import com.jarvis.clock.Events.State
import com.jarvis.clock.Prefs
import com.jarvis.clock.alarm.AlarmRingService

/**
 * Activación por voz: escucha de forma continua y, cuando oye una frase que llama a Friday
 * ("Friday, ¿qué hora es?"), la procesa sin que nadie pulse el micro.
 *
 * El reloj no lleva ningún modelo de reconocimiento (1 GB de RAM): su detector de voz solo recorta
 * fragmentos de habla y el puente del homelab (Whisper) decide si empiezan por el nombre.
 * Se pausa mientras el asistente escucha o habla, suena una alarma, hay música o el micro está silenciado.
 */
class WakeWord(private val ctx: Context) {

    private val backend = Backend(ctx)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    @Volatile private var running = false
    private var thread: Thread? = null
    private var seq = 0

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "wake-word").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
    }

    /** Motivo por el que ahora no se escucha, o null si se puede escuchar. */
    private fun blocker(): String? = when {
        !running -> "parado"
        !Prefs.wakeWord -> "desactivado en Ajustes"
        !backend.configured -> "sin backend"
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> "sin permiso de micro"
        Assistant.get(ctx).state != State.IDLE -> "asistente ${Assistant.get(ctx).state}"
        MicGate.commandActive -> "capturando una orden"
        AlarmRingService.ringing -> "alarma sonando"
        audio.isMicrophoneMute -> "micro silenciado"
        audio.isMusicActive -> "hay música/audio sonando"
        else -> null
    }

    private fun canListen(): Boolean = blocker() == null

    private fun loop() {
        var lastWhy: String? = null
        while (running) {
            try {
                val why = blocker()
                if (why != null) {
                    if (why != lastWhy) Log.i(TAG, "en pausa: $why")
                    lastWhy = why
                    Thread.sleep(IDLE_POLL_MS)
                    continue
                }
                if (lastWhy != null) Log.i(TAG, "escuchando")
                lastWhy = null
                val wav = VoiceRecorder().recordUtterance { canListen() }
                if (wav == null) { Thread.sleep(100); continue }
                handle(wav)
            } catch (_: InterruptedException) {
                return
            } catch (e: Exception) {
                Log.w(TAG, "escucha continua", e)
                runCatching { Thread.sleep(ERROR_BACKOFF_MS) }
            }
        }
    }

    private fun handle(wav: ByteArray) {
        // Depuración: el último fragmento enviado, para ver qué oye realmente el micro.
        val file = "wake_${seq++ % 10}.wav"
        runCatching { java.io.File(ctx.cacheDir, file).writeBytes(wav) }
        val r = backend.wake(wav)
        Log.i(TAG, "$file llama=${r.called} texto='${r.text}' orden='${r.command}'")
        if (!r.called) {
            // Ruido continuo (tele, conversación): no mandar un fragmento tras otro al puente.
            if (wav.size > LONG_SEGMENT_BYTES) Thread.sleep(LONG_SEGMENT_PAUSE_MS)
            return
        }
        val assistant = Assistant.get(ctx)
        if (r.command.isBlank()) assistant.listen() else assistant.ask(r.command, speak = true)
    }

    private companion object {
        const val TAG = "WakeWord"
        const val IDLE_POLL_MS = 600L
        const val ERROR_BACKOFF_MS = 5_000L
        const val LONG_SEGMENT_BYTES = VoiceRecorder.RATE * 2 * 5 // 5 s de audio
        const val LONG_SEGMENT_PAUSE_MS = 3_000L
    }
}
