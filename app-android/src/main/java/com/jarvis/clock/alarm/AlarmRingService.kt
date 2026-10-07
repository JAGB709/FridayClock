package com.jarvis.clock.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.audiofx.LoudnessEnhancer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.jarvis.clock.App
import com.jarvis.clock.Events
import com.jarvis.clock.Prefs
import com.jarvis.clock.assistant.Assistant
import org.json.JSONObject

/**
 * Hace sonar la alarma: volumen progresivo, pantalla completa, auto-posponer
 * si nadie la para y "buenos días" del asistente al descartarla.
 */
class AlarmRingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var beeper: ToneGenerator? = null
    private var booster: LoudnessEnhancer? = null
    private var rampTick: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var label = ""
    private var kind = Alarm.Kind.ALARM
    private var alarmId = -1
    private var current: Alarm? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> dismiss(briefing = true)
            ACTION_SNOOZE -> snooze()
            else -> {
                current = intent?.getStringExtra(EXTRA_ALARM)
                    ?.let { runCatching { Alarm.fromStorage(JSONObject(it)) }.getOrNull() }
                ring(
                    intent?.getIntExtra(EXTRA_ID, -1) ?: -1,
                    intent?.getStringExtra(EXTRA_LABEL) ?: "Alarma",
                    runCatching { Alarm.Kind.valueOf(intent?.getStringExtra(EXTRA_KIND) ?: "") }.getOrDefault(Alarm.Kind.ALARM),
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Int, label: String, kind: Alarm.Kind) {
        stopSound()
        Sounds.stopPreview()
        this.alarmId = id
        this.label = label
        this.kind = kind
        ringing = true
        currentLabel = label
        startedAt = System.currentTimeMillis()

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:alarm")
            .apply { acquire(65 * 60_000L) }

        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        Assistant.get(this).stopAll() // que no hable encima de la alarma
        ensureAudible()
        startSound()
        Events.post(Events.Event.AlarmRinging(id, label))
        // Somos el launcher: normalmente se nos permite abrir la actividad directamente.
        runCatching {
            startActivity(AlarmActivity.intent(this, label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        val autoMs = Prefs.alarmAutoSnoozeMinutes * 60_000L
        handler.postDelayed({
            if (kind == Alarm.Kind.TIMER) dismiss(briefing = false) else snooze()
        }, autoMs)
    }

    private fun ensureAudible() {
        // El altavoz de este reloj es flojo: la alarma siempre al máximo del sistema.
        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max) {
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
    }

    private fun alarmUri(): Uri? =
        listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
            .firstNotNullOfOrNull { RingtoneManager.getActualDefaultRingtoneUri(this, it) ?: RingtoneManager.getDefaultUri(it) }

    private fun volumeScale(): Float = ((current?.maxVolume ?: 100).coerceIn(1, 100)) / 100f

    private fun tryPlay(uri: Uri, attrs: AudioAttributes): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.setVolume(0f, 0f)
            mp.start()
            mp
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo reproducir $uri", e)
            mp.release()
            null
        }
    }

    /**
     * Arranca el sonido. [nivel] 0 = sonido propio y luego tono por defecto; 1 = solo tono
     * por defecto (el propio falló en pleno timbre); 2 = directamente pitidos.
     */
    private fun startSound(nivel: Int = 0) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val propio = if (nivel == 0) Sounds.uri(this, current?.sound) else null
        val porDefecto = if (nivel <= 1) alarmUri() else null
        var suenaPropio = false
        val mp = listOfNotNull(propio, porDefecto).firstNotNullOfOrNull { uri ->
            tryPlay(uri, attrs)?.also { suenaPropio = uri == propio }
        }

        if (mp != null) {
            player = mp
            // Si el reproductor falla a mitad (archivo corrupto, HAL), nunca queda en silencio.
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Error de reproducción en plena alarma ($what/$extra)")
                if (player === mp && ringing) {
                    handler.removeCallbacks(rampTick ?: Runnable {})
                    booster?.release()
                    booster = null
                    player = null
                    runCatching { mp.release() }
                    startSound(if (suenaPropio) 1 else 2)
                }
                true
            }
            // Loudness sobre el tono (si el HAL no lo soporta, se ignora); sube con la rampa.
            booster = runCatching {
                LoudnessEnhancer(mp.audioSessionId).apply { setTargetGain((BOOST_START_MB * volumeScale()).toInt()); enabled = true }
            }.onFailure { Log.w(TAG, "Sin LoudnessEnhancer", it) }.getOrNull()
            rampVolume()
        } else {
            Log.w(TAG, "Sin tono de alarma disponible, uso pitidos")
            // Una GSI limpia puede venir sin tonos: pitidos sintetizados por el stream de alarma.
            val vol = (ToneGenerator.MAX_VOLUME * volumeScale()).toInt().coerceAtLeast(1)
            beeper = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, vol) }.getOrNull()
            beep()
        }
    }

    private fun rampVolume() {
        val rampMs = (current?.rampSeconds ?: Prefs.alarmRampSeconds) * 1000L
        val scale = volumeScale()
        handler.removeCallbacks(rampTick ?: Runnable {})
        val tick = object : Runnable {
            override fun run() {
                val p = player ?: return
                val t = if (rampMs <= 0) 1f else ((System.currentTimeMillis() - startedAt).toFloat() / rampMs).coerceIn(0f, 1f)
                // Arranca ya fuerte y sube con curva suave hasta el máximo.
                val v = RAMP_FLOOR + (1f - RAMP_FLOOR) * t * t
                runCatching { p.setVolume(v * scale, v * scale) }
                booster?.let { b -> runCatching { b.setTargetGain(((BOOST_START_MB + (BOOST_MAX_MB - BOOST_START_MB) * t * t) * scale).toInt()) } }
                if (t < 1f) handler.postDelayed(this, 500)
            }
        }
        rampTick = tick
        handler.post(tick)
    }

    private fun beep() {
        val tick = object : Runnable {
            override fun run() {
                val b = beeper ?: return
                b.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                handler.postDelayed(this, 1500)
            }
        }
        handler.post(tick)
    }

    private fun snooze() {
        val min = current?.snoozeMinutes ?: Prefs.snoozeMinutes
        if (ringing) AlarmScheduler.snooze(this, label, min, current)
        finishRinging()
    }

    private fun dismiss(briefing: Boolean) {
        val wasAlarm = ringing && kind != Alarm.Kind.TIMER
        val wantsBriefing = current?.briefing ?: Prefs.morningBriefing
        finishRinging()
        if (briefing && wasAlarm && wantsBriefing) {
            Assistant.get(this).briefing()
        }
    }

    private fun finishRinging() {
        ringing = false
        currentLabel = null
        handler.removeCallbacksAndMessages(null)
        stopSound()
        Events.post(Events.Event.AlarmStopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        booster?.release()
        booster = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        beeper?.release()
        beeper = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        stopSound()
        ringing = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val full = PendingIntent.getActivity(
            this, 1, AlarmActivity.intent(this, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(a: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, AlarmRingService::class.java).setAction(a),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, App.CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Toca para abrir")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(Notification.Action.Builder(null, "Posponer", action(ACTION_SNOOZE, 2)).build())
            .addAction(Notification.Action.Builder(null, "Descartar", action(ACTION_DISMISS, 3)).build())
            .build()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        // La alarma arranca ya fuerte y sigue subiendo hasta el máximo.
        private const val RAMP_FLOOR = 0.8f        // volumen inicial del reproductor (0..1)
        private const val BOOST_START_MB = 600     // loudness inicial: +6 dB
        private const val BOOST_MAX_MB = 1200      // loudness final: +12 dB
        private const val NOTIF_ID = 42
        const val ACTION_DISMISS = "com.jarvis.clock.DISMISS"
        const val ACTION_SNOOZE = "com.jarvis.clock.SNOOZE"
        private const val EXTRA_ID = "id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ALARM = "alarm"

        @Volatile var ringing = false
            private set
        @Volatile var currentLabel: String? = null
            private set

        fun start(ctx: Context, alarm: Alarm) {
            ctx.startForegroundService(
                Intent(ctx, AlarmRingService::class.java)
                    .putExtra(EXTRA_ID, alarm.id)
                    .putExtra(EXTRA_LABEL, alarm.label)
                    .putExtra(EXTRA_KIND, alarm.kind.name)
                    .putExtra(EXTRA_ALARM, alarm.toStorage().toString())
            )
        }

        fun dismiss(ctx: Context) = send(ctx, ACTION_DISMISS)
        fun snooze(ctx: Context) = send(ctx, ACTION_SNOOZE)

        private fun send(ctx: Context, action: String) {
            if (!ringing) return
            ctx.startService(Intent(ctx, AlarmRingService::class.java).setAction(action))
        }
    }
}
