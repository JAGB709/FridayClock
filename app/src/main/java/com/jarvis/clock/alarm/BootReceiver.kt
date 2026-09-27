package com.jarvis.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvis.clock.ClockService

/** Re-programa las alarmas tras reinicio, actualización o cambio de hora/zona. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(ctx)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ClockService.start(ctx)
        }
    }
}
