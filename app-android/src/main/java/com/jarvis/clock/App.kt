package com.jarvis.clock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jarvis.clock.alarm.AlarmScheduler

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
        Prefs.init(this)
        // Por si el proceso se ha reiniciado sin pasar por BOOT_COMPLETED.
        AlarmScheduler.rescheduleAll(this)
        ClockService.start(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Servicio del reloj", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Alarmas", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // el sonido lo gestiona AlarmRingService
                enableVibration(false)
                setBypassDnd(true)
            }
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "service"
        const val CHANNEL_ALARM = "alarm"
    }
}
