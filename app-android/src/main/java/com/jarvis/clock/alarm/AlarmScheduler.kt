package com.jarvis.clock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jarvis.clock.MainActivity
import com.jarvis.clock.Prefs

/**
 * Toda la programación pasa por AlarmManager.setAlarmClock(): es exacta, despierta
 * el dispositivo aunque esté en Doze y no depende de la red ni del homelab.
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    const val EXTRA_ID = "alarm_id"

    fun create(ctx: Context, hour: Int, minute: Int, days: Int = 0, label: String = "Alarma"): Alarm {
        val a = Alarm(AlarmStore.newId(ctx), hour, minute, days, label.ifBlank { "Alarma" })
        save(ctx, a)
        return a
    }

    fun createTimer(ctx: Context, seconds: Long, label: String = "Temporizador"): Alarm {
        val at = System.currentTimeMillis() + seconds * 1000
        return createAbsolute(ctx, at, label.ifBlank { "Temporizador" }, Alarm.Kind.TIMER)
    }

    fun snooze(ctx: Context, label: String, minutes: Int = Prefs.snoozeMinutes): Alarm =
        createAbsolute(ctx, System.currentTimeMillis() + minutes * 60_000L, label, Alarm.Kind.SNOOZE)

    private fun createAbsolute(ctx: Context, at: Long, label: String, kind: Alarm.Kind): Alarm {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
        val a = Alarm(
            id = AlarmStore.newId(ctx),
            hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
            minute = cal.get(java.util.Calendar.MINUTE),
            label = label,
            triggerAt = at,
            kind = kind,
        )
        save(ctx, a)
        return a
    }

    /** Guarda y (re)programa. */
    fun save(ctx: Context, alarm: Alarm) {
        AlarmStore.upsert(ctx, alarm)
        schedule(ctx, alarm)
    }

    fun delete(ctx: Context, id: Int): Boolean {
        cancel(ctx, id)
        return AlarmStore.remove(ctx, id)
    }

    fun setEnabled(ctx: Context, id: Int, enabled: Boolean): Alarm? {
        val a = AlarmStore.get(ctx, id) ?: return null
        val n = a.copy(enabled = enabled)
        save(ctx, n)
        return n
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        for (a in AlarmStore.all(ctx)) {
            // Temporizadores que vencieron con el reloj apagado: fuera.
            if (a.isAbsolute && a.triggerAt < now - 60_000) {
                AlarmStore.remove(ctx, a.id)
                continue
            }
            schedule(ctx, a)
        }
    }

    /** Llamado desde AlarmReceiver cuando una alarma acaba de sonar. */
    fun onFired(ctx: Context, alarm: Alarm) {
        when {
            alarm.isAbsolute -> AlarmStore.remove(ctx, alarm.id)
            alarm.isRepeating -> schedule(ctx, alarm, System.currentTimeMillis() + 60_000) // siguiente día que toque
            else -> AlarmStore.upsert(ctx, alarm.copy(enabled = false))
        }
    }

    fun nextAlarm(ctx: Context): Pair<Alarm, Long>? =
        AlarmStore.all(ctx).mapNotNull { a -> a.nextTrigger()?.let { a to it } }.minByOrNull { it.second }

    private fun schedule(ctx: Context, alarm: Alarm, from: Long = System.currentTimeMillis()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(ctx, alarm.id)
        val at = alarm.nextTrigger(from)
        if (at == null) {
            am.cancel(pi)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Sin permiso de alarma exacta: mejor una inexacta que ninguna.
            Log.w(TAG, "Sin permiso SCHEDULE_EXACT_ALARM; usando setAndAllowWhileIdle")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            return
        }
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
    }

    private fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(ctx, id))
    }

    private fun firePendingIntent(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
