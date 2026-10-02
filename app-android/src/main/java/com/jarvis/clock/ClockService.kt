package com.jarvis.clock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.jarvis.clock.assistant.WakeWord
import com.jarvis.clock.server.Api
import com.jarvis.clock.server.HttpServer
import com.jarvis.clock.server.NetUtil

/** Servicio en primer plano que mantiene vivo el servidor HTTP local. */
class ClockService : Service() {

    private var server: HttpServer? = null
    private var wake: WakeWord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, App.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Jarvis Clock")
            .setContentText("Servidor local en el puerto ${Prefs.httpPort}")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        server = newServer()
        wake = WakeWord(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTART_SERVER) {
            server?.stop()
            server = newServer()
        }
        return START_STICKY
    }

    private fun newServer(): HttpServer {
        val host = if (Prefs.bindTailscaleOnly) NetUtil.tailscaleAddress() else null
        return HttpServer(Prefs.httpPort, host, Api(this)::handle).also { it.start() }
    }

    override fun onDestroy() {
        wake?.stop()
        server?.stop()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val ACTION_RESTART_SERVER = "com.jarvis.clock.RESTART_SERVER"

        fun start(ctx: Context) {
            runCatching {
                ctx.startForegroundService(Intent(ctx, ClockService::class.java))
            }.onFailure { Log.w("ClockService", "No se pudo arrancar el servicio", it) }
        }

        fun restartServer(ctx: Context) {
            runCatching {
                ctx.startForegroundService(Intent(ctx, ClockService::class.java).setAction(ACTION_RESTART_SERVER))
            }
        }
    }
}
