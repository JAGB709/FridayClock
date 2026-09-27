package com.jarvis.clock.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jarvis.clock.Prefs
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Voz del asistente. Usa el TTS del sistema (en una GSI limpia instala
 * RHVoice o eSpeak NG desde F-Droid) y, si el backend manda audio ya
 * sintetizado (Piper), lo reproduce directamente.
 */
class Tts(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var player: MediaPlayer? = null
    private val pending = mutableListOf<Pair<String, (() -> Unit)?>>()
    private val callbacks = HashMap<String, () -> Unit>()
    private val seq = AtomicInteger()

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    val available get() = ready

    @Synchronized
    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            synchronized(this) {
                val t = tts
                if (status == TextToSpeech.SUCCESS && t != null) {
                    val r = t.setLanguage(Locale("es", "ES"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        t.setLanguage(Locale("es"))
                    }
                    t.setAudioAttributes(attrs)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String) {}
                        override fun onDone(id: String) = fire(id)
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String) = fire(id)
                        override fun onError(id: String, code: Int) = fire(id)
                    })
                    ready = true
                    val q = pending.toList()
                    pending.clear()
                    q.forEach { speakNow(it.first, it.second) }
                } else {
                    Log.w(TAG, "TTS no disponible (status=$status)")
                    val q = pending.toList()
                    pending.clear()
                    q.forEach { it.second?.invoke() }
                }
            }
        }
    }

    private fun fire(id: String) {
        val cb = synchronized(this) { callbacks.remove(id) }
        cb?.invoke()
    }

    /** Pre-calienta el motor para que la primera respuesta no tarde. */
    fun warmUp() = ensure()

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }
        ensure()
        synchronized(this) {
            if (!ready) { pending += text to onDone; return }
        }
        speakNow(text, onDone)
    }

    private fun speakNow(text: String, onDone: (() -> Unit)?) {
        val t = tts ?: run { onDone?.invoke(); return }
        val id = "u" + seq.incrementAndGet()
        if (onDone != null) synchronized(this) { callbacks[id] = onDone }
        t.setSpeechRate(Prefs.ttsRate)
        val r = t.speak(clean(text), TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (r != TextToSpeech.SUCCESS) fire(id)
    }

    /** Reproduce audio ya sintetizado (wav/mp3/ogg) que llega del backend. */
    fun play(bytes: ByteArray, onDone: (() -> Unit)? = null) {
        stop()
        val f = File(ctx.cacheDir, "reply.audio")
        f.writeBytes(bytes)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(f.absolutePath)
                setOnCompletionListener { release(); if (player === it) player = null; onDone?.invoke() }
                setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; onDone?.invoke(); true }
                prepare()
                start()
            }
        }.onFailure { onDone?.invoke() }.getOrNull()
    }

    fun stop() {
        tts?.stop()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        val cbs = synchronized(this) { callbacks.values.toList().also { callbacks.clear() } }
        cbs.forEach { it() }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    /** Quita markdown que el LLM pueda colar y que el TTS leería en voz alta. */
    private fun clean(s: String) = s
        .replace(Regex("[*_#`>]+"), "")
        .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object { private const val TAG = "Tts" }
}
