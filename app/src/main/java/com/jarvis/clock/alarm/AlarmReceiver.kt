package com.jarvis.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val alarm = AlarmStore.get(ctx, id) ?: return
        if (!alarm.enabled) return
        AlarmScheduler.onFired(ctx, alarm)
        AlarmRingService.start(ctx, alarm)
    }
}
