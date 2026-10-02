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

/**
 * Hace sonar la alarma: volumen progresivo, pantalla completa, auto-posponer
 * si nadie la para y "buenos días" del asistente al descartarla.
 */
class AlarmRingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var beeper: ToneGenerator? = null
    private var booster: LoudnessEnhancer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var label = ""
    private var kind = Alarm.Kind.ALARM
    private var alarmId = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> dismiss(briefing = true)
            ACTION_SNOOZE -> snooze()
            else -> ring(
                intent?.getIntExtra(EXTRA_ID, -1) ?: -1,
                intent?.getStringExtra(EXTRA_LABEL) ?: "Alarma",
                runCatching { Alarm.Kind.valueOf(intent?.getStringExtra(EXTRA_KIND) ?: "") }.getOrDefault(Alarm.Kind.ALARM),
            )
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Int, label: String, kind: Alarm.Kind) {
        stopSound()
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

    private fun startSound() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val mp = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmRingService, alarmUri()!!)
                isLooping = true
                prepare()
                setVolume(0f, 0f)
                start()
            }
        }.onFailure { Log.w(TAG, "Sin tono de alarma disponible, uso pitidos", it) }.getOrNull()

        if (mp != null) {
            player = mp
            // Loudness sobre el tono (si el HAL no lo soporta, se ignora); sube con la rampa.
            booster = runCatching {
                LoudnessEnhancer(mp.audioSessionId).apply { setTargetGain(BOOST_START_MB); enabled = true }
            }.onFailure { Log.w(TAG, "Sin LoudnessEnhancer", it) }.getOrNull()
            rampVolume()
        } else {
            // Una GSI limpia puede venir sin tonos: pitidos sintetizados por el stream de alarma.
            beeper = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
            beep()
        }
    }

    private fun rampVolume() {
        val rampMs = Prefs.alarmRampSeconds * 1000L
        val tick = object : Runnable {
            override fun run() {
                val p = player ?: return
                val t = if (rampMs <= 0) 1f else ((System.currentTimeMillis() - startedAt).toFloat() / rampMs).coerceIn(0f, 1f)
                // Arranca ya fuerte y sube con curva suave hasta el máximo.
                val v = RAMP_FLOOR + (1f - RAMP_FLOOR) * t * t
                runCatching { p.setVolume(v, v) }
                booster?.let { b -> runCatching { b.setTargetGain((BOOST_START_MB + (BOOST_MAX_MB - BOOST_START_MB) * t * t).toInt()) } }
                if (t < 1f) handler.postDelayed(this, 500)
            }
        }
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
        val min = Prefs.snoozeMinutes
        if (ringing) AlarmScheduler.snooze(this, label, min)
        finishRinging()
    }

    private fun dismiss(briefing: Boolean) {
        val wasAlarm = ringing && kind != Alarm.Kind.TIMER
        finishRinging()
        if (briefing && wasAlarm && Prefs.morningBriefing) {
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
