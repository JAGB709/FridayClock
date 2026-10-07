package com.jarvis.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Configuración por ADB, sin abrir Ajustes (útil en la Fase 4 desde el ordenador):
 *
 *   adb shell am broadcast -a com.jarvis.clock.CONFIGURE \
 *     -n com.jarvis.clock/.ConfigReceiver \
 *     --es token "TOKEN" --es backend_url "http://100.x:8765" --es backend_token "T" \
 *     --es assistant_name "Friday" --es city "Madrid"
 *
 * Exigimos que venga con componente explícito (-n) para que no lo dispare otra app.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION || intent.component == null) return
        Prefs.init(ctx)
        var changedServer = false
        intent.getStringExtra("token")?.let { Prefs.authToken = it }
        intent.getStringExtra("backend_url")?.let { Prefs.backendUrl = it }
        intent.getStringExtra("backend_token")?.let { Prefs.backendToken = it }
        intent.getStringExtra("assistant_name")?.let { Prefs.assistantName = it }
        intent.getStringExtra("city")?.let { Prefs.city = it }
        if (intent.hasExtra("lat")) Prefs.latitude = intent.getDoubleExtra("lat", Prefs.latitude)
        if (intent.hasExtra("lon")) Prefs.longitude = intent.getDoubleExtra("lon", Prefs.longitude)
        if (intent.hasExtra("port")) { Prefs.httpPort = intent.getIntExtra("port", Prefs.httpPort); changedServer = true }
        if (intent.hasExtra("wake_word")) Prefs.wakeWord = intent.getBooleanExtra("wake_word", true)
        if (intent.hasExtra("bind_tailscale_only")) {
            Prefs.bindTailscaleOnly = intent.getBooleanExtra("bind_tailscale_only", false); changedServer = true
        }
        Log.i("ConfigReceiver", "Configuración aplicada por ADB")
        if (changedServer) ClockService.restartServer(ctx)
        // Confirmación visible en `adb shell am broadcast` (resultado del receiver).
        resultData = "ok"
    }

    companion object {
        const val ACTION = "com.jarvis.clock.CONFIGURE"
    }
}
