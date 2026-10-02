package com.jarvis.clock.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.jarvis.clock.Events
import com.jarvis.clock.Events.State
import com.jarvis.clock.Prefs
import com.jarvis.clock.alarm.Alarm
import com.jarvis.clock.alarm.AlarmRingService
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.alarm.AlarmStore
import com.jarvis.clock.weather.Weather
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cerebro del asistente. Flujo:
 *  1. Escuchar: si hay backend, graba el audio y lo manda (Whisper en el homelab);
 *     si no, usa el SpeechRecognizer del sistema si existe.
 *  2. Entender: primero órdenes locales (alarmas, temporizadores, hora, tiempo...)
 *     que funcionan sin red; lo demás a Hermes a través del puente del homelab.
 *  3. Actuar y responder por voz; si la respuesta es una pregunta, vuelve a escuchar.
 */
class Assistant private constructor(ctx: Context) {

    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "assistant").apply { isDaemon = true } }
    private val tts = Tts(app)
    private val backend = Backend(app)
    private val generation = AtomicInteger()
    @Volatile private var recorder: VoiceRecorder? = null
    private var recognizer: SpeechRecognizer? = null
    @Volatile var state = State.IDLE
        private set

    data class Result(val transcript: String, val reply: String, val expectReply: Boolean = false, val audio: ByteArray? = null)

    init { tts.warmUp() }

    // ------------------------------------------------------------------ entrada

    /** Botón de micro / gesto: empieza a escuchar, o para si ya estaba en marcha. */
    fun toggleListening() {
        if (state == State.LISTENING || state == State.THINKING || state == State.SPEAKING) {
            stopAll(); return
        }
        listen()
    }

    fun listen() {
        if (AlarmRingService.ringing) {
            // Con la alarma sonando el micro solo oiría la alarma: la paramos.
            AlarmRingService.dismiss(app); return
        }
        stopAll()
        val gen = generation.incrementAndGet()
        if (app.getSystemService(AudioManager::class.java).isMicrophoneMute) {
            say(gen, "El micrófono está silenciado. Pulsa el botón del centro para activarlo.")
            return
        }
        when {
            backend.configured -> worker.execute { listenWithBackend(gen) }
            SpeechRecognizer.isRecognitionAvailable(app) -> main.post { listenWithRecognizer(gen) }
            else -> say(gen, "No tengo reconocimiento de voz. Configura el backend del homelab en Ajustes.")
        }
    }

    /** Texto ya transcrito (API HTTP /ask o reconocedor del sistema). Bloqueante. */
    fun process(text: String): Result {
        val local = LocalIntents.parse(text)
        if (local != null) return Result(text, runLocal(local))
        if (!backend.configured) {
            return Result(text, "No sé hacer eso sin conexión al homelab. Puedo poner alarmas, temporizadores o decirte la hora y el tiempo.")
        }
        return try {
            val r = backend.assist(text = text)
            Actions.executeAll(app, r.actions)
            Result(text, r.reply, r.expectReply, r.audio)
        } catch (e: Exception) {
            Log.w(TAG, "backend", e)
            Result(text, "No consigo hablar con el homelab ahora mismo.")
        }
    }

    /** Pregunta por texto y además la responde en voz alta. */
    fun ask(text: String, speak: Boolean = true): Result {
        val gen = generation.incrementAndGet()
        post(State.THINKING, text)
        val r = process(text)
        if (speak) respond(gen, r) else post(State.IDLE)
        return r
    }

    fun speak(text: String) {
        val gen = generation.incrementAndGet()
        say(gen, text)
    }

    /** Buenos días tras descartar la alarma. */
    fun briefing() {
        val gen = generation.incrementAndGet()
        worker.execute {
            Weather.get()
            val r = if (backend.configured) {
                runCatching { backend.assist(mode = "briefing") }.getOrNull()
                    ?.let { Actions.executeAll(app, it.actions); Result("", it.reply, false, it.audio) }
            } else null
            respond(gen, r?.takeIf { it.reply.isNotBlank() } ?: Result("", localBriefing()))
        }
    }

    fun stopAll() {
        generation.incrementAndGet()
        recorder?.cancel()
        recorder = null
        main.post { recognizer?.cancel() }
        tts.stop()
        post(State.IDLE)
    }

    // ------------------------------------------------------------------ escucha

    private fun listenWithBackend(gen: Int) {
        earcon()
        post(State.LISTENING)
        val rec = VoiceRecorder().also { recorder = it }
        val wav = rec.record { Events.post(Events.Event.MicLevel(it)) }
        if (gen != generation.get()) return
        recorder = null
        if (wav == null) { post(State.IDLE); return }
        post(State.THINKING)
        // 1) Whisper en el homelab transcribe. 2) Las órdenes de despertador se
        // resuelven aquí mismo. 3) Todo lo demás va a Hermes.
        val text = try {
            backend.transcribe(wav)
        } catch (e: Exception) {
            Log.w(TAG, "stt", e)
            respond(gen, Result("", "No consigo hablar con el homelab ahora mismo."))
            return
        }
        if (gen != generation.get()) return
        Log.i(TAG, "stt='$text'")
        if (text.isBlank() || LocalIntents.parse(text) is LocalIntents.Intent.StopTalking) { post(State.IDLE); return }
        post(State.THINKING, text)
        respond(gen, process(text))
    }

    private fun listenWithRecognizer(gen: Int) {
        val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(app).also { recognizer = it }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { earcon(); post(State.LISTENING) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = Events.post(Events.Event.MicLevel(((rmsdB + 2) / 12f).coerceIn(0f, 1f)))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { post(State.THINKING) }
            override fun onError(error: Int) {
                if (gen == generation.get()) post(State.IDLE)
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { post(State.LISTENING, it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank() || gen != generation.get()) { post(State.IDLE); return }
                post(State.HEARD, text)
                worker.execute {
                    val r = process(text)
                    if (gen == generation.get()) respond(gen, r)
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        sr.startListening(i)
    }

    // ------------------------------------------------------------------ salida

    private fun respond(gen: Int, r: Result) {
        if (gen != generation.get()) return
        if (r.transcript.isNotBlank()) post(State.HEARD, r.transcript)
        if (r.reply.isBlank()) { post(State.IDLE); return }
        val followUp = Prefs.followUp && (r.expectReply || r.reply.trimEnd().endsWith("?"))
        val done = {
            if (gen == generation.get()) {
                if (followUp) main.postDelayed({ if (gen == generation.get()) listen() }, 250) else post(State.IDLE)
            }
        }
        post(State.SPEAKING, r.reply)
        if (r.audio != null) tts.play(r.audio, done) else tts.speak(r.reply, done)
    }

    private fun say(gen: Int, text: String) = respond(gen, Result("", text))

    private fun post(s: State, text: String = "") {
        state = s
        Events.post(Events.Event.AssistantState(s, text))
    }

    private fun earcon() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            main.postDelayed({ tg.release() }, 300)
        }
    }

    // ------------------------------------------------------------------ órdenes locales

    private fun runLocal(i: LocalIntents.Intent): String = when (i) {
        is LocalIntents.Intent.SetAlarm -> {
            val a = AlarmScheduler.create(app, i.hour, i.minute, i.days, i.label)
            val whenTxt = if (a.isRepeating) a.daysText().lowercase() else relativeDay(a.nextTrigger())
            "Hecho, alarma a las ${a.timeText()} $whenTxt."
        }
        is LocalIntents.Intent.SetTimer -> {
            AlarmScheduler.createTimer(app, i.seconds, i.label)
            "Temporizador de ${durationText(i.seconds)} en marcha."
        }
        is LocalIntents.Intent.CancelAlarm -> {
            val alarms = AlarmStore.all(app).filter { !it.isAbsolute }
            val targets = when {
                i.all -> alarms
                i.hour != null -> alarms.filter { it.hour == i.hour && it.minute == i.minute }
                else -> listOfNotNull(alarms.filter { it.enabled }.minByOrNull { it.nextTrigger() ?: Long.MAX_VALUE })
            }
            targets.forEach { AlarmScheduler.delete(app, it.id) }
            when (targets.size) {
                0 -> "No he encontrado esa alarma."
                1 -> "He quitado la alarma de las ${targets[0].timeText()}."
                else -> "He quitado ${targets.size} alarmas."
            }
        }
        LocalIntents.Intent.CancelTimers -> {
            val n = Actions.cancelTimers(app)
            if (n == 0) "No hay temporizadores activos." else "Temporizador cancelado."
        }
        LocalIntents.Intent.ListAlarms -> alarmsSummary()
        LocalIntents.Intent.StopRinging -> { AlarmRingService.dismiss(app); "" }
        LocalIntents.Intent.Snooze -> { AlarmRingService.snooze(app); "Vale, ${Prefs.snoozeMinutes} minutos más." }
        LocalIntents.Intent.Time -> "Son las ${SimpleDateFormat("H:mm", Locale.getDefault()).format(Date())}."
        LocalIntents.Intent.Date -> "Hoy es ${SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")).format(Date())}."
        LocalIntents.Intent.Weather -> Weather.get()?.spoken(Prefs.city) ?: "No puedo consultar el tiempo ahora mismo."
        is LocalIntents.Intent.Volume -> "Volumen al ${Actions.setVolume(app, i.percent ?: 0, i.delta)} por ciento."
        LocalIntents.Intent.StopTalking -> ""
        is LocalIntents.Intent.Bluetooth -> {
            if (!i.on) {
                Actions.bluetooth(app, org.json.JSONObject().put("action", "off"))
                "Bluetooth apagado."
            } else {
                val r = Actions.bluetooth(app, org.json.JSONObject().put("action", "speaker"))
                r.optString("message", "Bluetooth activado. Empareja el reloj desde tu móvil.")
            }
        }
    }

    private fun alarmsSummary(): String {
        val list = AlarmStore.all(app).filter { it.enabled }
        if (list.isEmpty()) return "No tienes alarmas activas."
        val next = AlarmScheduler.nextAlarm(app)
        val parts = list.filter { it.kind == Alarm.Kind.ALARM }.map { "a las ${it.timeText()} ${if (it.isRepeating) it.daysText().lowercase() else ""}".trim() }
        val nextTxt = next?.let { " La próxima suena ${relativeDay(it.second)} a las ${it.first.timeText()}." } ?: ""
        return if (parts.isEmpty()) nextTxt.trim() else "Tienes ${parts.size} alarma${if (parts.size > 1) "s" else ""}: ${parts.joinToString(", ")}.$nextTxt"
    }

    private fun localBriefing(): String = buildString {
        val now = Date()
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        append(if (h < 13) "Buenos días. " else if (h < 21) "Buenas tardes. " else "Buenas noches. ")
        append("Son las ${SimpleDateFormat("H:mm", Locale.getDefault()).format(now)} del ")
        append(SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")).format(now)).append(". ")
        Weather.get()?.let { append(it.spoken(Prefs.city)).append(' ') }
        AlarmScheduler.nextAlarm(app)?.takeIf { it.first.kind == Alarm.Kind.ALARM }?.let {
            append("Tu próxima alarma es ${relativeDay(it.second)} a las ${it.first.timeText()}.")
        }
    }

    private fun relativeDay(at: Long?): String {
        if (at == null) return ""
        val c = Calendar.getInstance()
        val today = c.get(Calendar.DAY_OF_YEAR)
        c.timeInMillis = at
        return when ((c.get(Calendar.DAY_OF_YEAR) - today + 366) % 366) {
            0 -> "hoy"
            1 -> "mañana"
            else -> "el " + SimpleDateFormat("EEEE", Locale("es", "ES")).format(Date(at))
        }
    }

    private fun durationText(s: Long): String {
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it hora${if (it > 1) "s" else ""}" },
            m.takeIf { it > 0 }?.let { "$it minuto${if (it > 1) "s" else ""}" },
            sec.takeIf { it > 0 }?.let { "$it segundo${if (it > 1) "s" else ""}" },
        ).joinToString(" y ").ifEmpty { "0 segundos" }
    }

    companion object {
        private const val TAG = "Assistant"
        @Volatile private var instance: Assistant? = null
        fun get(ctx: Context): Assistant = instance ?: synchronized(this) {
            instance ?: Assistant(ctx).also { instance = it }
        }
    }
}
