package com.jarvis.clock.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.jarvis.clock.ClockService
import com.jarvis.clock.Prefs
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.assistant.Assistant
import com.jarvis.clock.assistant.Backend
import com.jarvis.clock.server.NetUtil
import kotlin.concurrent.thread

/** Ajustes: lo que antes estaba hardcodeado (token, backend, ciudad...) ahora se edita aquí. */
class SettingsActivity : Activity() {

    private val saves = mutableListOf<() -> Unit>()
    private lateinit var form: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = { v: Int -> Ui.dp(this, v) }
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
            setBackgroundColor(Color.BLACK)
        }

        title("Ajustes")
        info("Dirección: ${ips().joinToString(", ") { "$it:${Prefs.httpPort}" }.ifEmpty { "sin red" }}")

        section("Servidor del reloj")
        text("Token (X-Auth-Token)", Prefs.authToken) { Prefs.authToken = it }
        number("Puerto HTTP", Prefs.httpPort) { if (it in 1024..65535 && it != Prefs.httpPort) { Prefs.httpPort = it; ClockService.restartServer(this) } }
        switch("Escuchar solo en Tailscale (recomendado)", Prefs.bindTailscaleOnly) {
            if (it != Prefs.bindTailscaleOnly) { Prefs.bindTailscaleOnly = it; ClockService.restartServer(this) }
        }

        section("Asistente")
        text("Nombre del asistente", Prefs.assistantName) { Prefs.assistantName = it }
        text("URL del puente del homelab", Prefs.backendUrl, hint = "http://100.x.y.z:8765") { Prefs.backendUrl = it }
        text("Token del puente", Prefs.backendToken) { Prefs.backendToken = it }
        switch("Activar al decir \"${Prefs.assistantName}\" (escucha continua)", Prefs.wakeWord) { Prefs.wakeWord = it }
        switch("Seguir escuchando si pregunta algo", Prefs.followUp) { Prefs.followUp = it }
        decimal("Velocidad de voz (0.5–2)", Prefs.ttsRate.toDouble()) { Prefs.ttsRate = it.toFloat() }
        button("Probar conexión con Hermes") { testBackend() }
        button("Ajustes de voz del sistema (TTS)") {
            runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
        }

        section("Ubicación (tiempo)")
        text("Ciudad", Prefs.city) { Prefs.city = it }
        decimal("Latitud", Prefs.latitude) { Prefs.latitude = it }
        decimal("Longitud", Prefs.longitude) { Prefs.longitude = it }

        section("Despertador")
        number("Minutos al posponer", Prefs.snoozeMinutes) { Prefs.snoozeMinutes = it }
        number("Segundos de subida de volumen", Prefs.alarmRampSeconds) { Prefs.alarmRampSeconds = it }
        number("Minutos sonando antes de posponer sola", Prefs.alarmAutoSnoozeMinutes) { Prefs.alarmAutoSnoozeMinutes = it }
        switch("Buenos días del asistente al descartar", Prefs.morningBriefing) { Prefs.morningBriefing = it }
        button("Probar alarma (suena en 10 s)") {
            saveAll()
            AlarmScheduler.createTimer(this, 10, "Prueba de alarma")
            toast("Sonará en 10 segundos")
        }

        section("Pantalla")
        number("Modo noche desde (hora)", Prefs.nightStartHour) { Prefs.nightStartHour = it }
        number("Modo noche hasta (hora)", Prefs.nightEndHour) { Prefs.nightEndHour = it }
        number("Brillo de noche (%)", Prefs.nightBrightness) { Prefs.nightBrightness = it }
        number("Brillo de día (%)", Prefs.dayBrightness) { Prefs.dayBrightness = it }

        section("Altavoz Bluetooth")
        button("Modo altavoz Bluetooth (conectar el móvil)") { startActivity(Intent(this, BluetoothActivity::class.java)) }

        section("Red")
        button("Wi-Fi (elegir red y contraseña)") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        button("Tailscale") {
            // La VPN mantiene el reloj accesible desde el homelab en cualquier Wi-Fi.
            packageManager.getLaunchIntentForPackage("com.tailscale.ipn")?.let { startActivity(it) }
        }

        section("Sistema")
        button("Elegir launcher predeterminado") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        button("Ajustes de Android") { startActivity(Intent(Settings.ACTION_SETTINGS)) }

        val save = Button(this).apply {
            text = "Guardar"; textSize = 20f; isAllCaps = false
            setTextColor(Tokens.Color.ON_ACCENT); setBackgroundColor(Tokens.Color.ACCENT)
            typeface = Fonts.semibold(this@SettingsActivity)
            setOnClickListener { saveAll(); toast("Guardado"); finish() }
        }
        form.addView(save, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(20) })
        setContentView(ScrollView(this).apply { addView(form); setBackgroundColor(Color.BLACK) })
    }

    private fun saveAll() = saves.forEach { runCatching { it() } }

    private fun testBackend() {
        saveAll()
        val b = Backend(this)
        if (!b.configured) { toast("Configura primero la URL del puente"); return }
        toast("Probando…")
        thread {
            val msg = runCatching { b.assist(text = "Responde solo: conexión correcta.").reply }
                .getOrElse { "Error: ${it.message}" }
            runOnUiThread { toast(msg) }
            Assistant.get(this).speak(msg)
        }
    }

    private fun ips(): List<String> = NetUtil.ipv4()

    // ------------------------------------------------------------------ helpers de formulario

    private fun title(t: String) = form.addView(TextView(this).apply { text = t; textSize = 28f; setTextColor(Ui.TEXT); typeface = Fonts.semibold(this@SettingsActivity) })
    private fun info(t: String) = form.addView(TextView(this).apply { text = t; textSize = 16f; setTextColor(Ui.ACCENT); typeface = Fonts.regular(this@SettingsActivity); setTextIsSelectable(true) })

    private fun section(t: String) = form.addView(TextView(this).apply {
        text = t; textSize = 18f; setTextColor(Ui.ACCENT); typeface = Fonts.semibold(this@SettingsActivity)
        setPadding(0, Ui.dp(this@SettingsActivity, 20), 0, Ui.dp(this@SettingsActivity, 4))
    })

    private fun field(label: String, value: String, type: Int, hint: String = "", onSave: (String) -> Unit) {
        form.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(Ui.DIM); typeface = Fonts.regular(this@SettingsActivity) })
        val e = EditText(this).apply {
            setText(value); this.hint = hint; inputType = type; setSingleLine()
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
        }
        form.addView(e)
        saves += { onSave(e.text.toString()) }
    }

    private fun text(label: String, value: String, hint: String = "", onSave: (String) -> Unit) =
        field(label, value, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, hint, onSave)

    private fun number(label: String, value: Int, onSave: (Int) -> Unit) =
        field(label, value.toString(), InputType.TYPE_CLASS_NUMBER) { it.toIntOrNull()?.let(onSave) }

    private fun decimal(label: String, value: Double, onSave: (Double) -> Unit) =
        field(label, value.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED) {
            it.replace(',', '.').toDoubleOrNull()?.let(onSave)
        }

    private fun switch(label: String, value: Boolean, onSave: (Boolean) -> Unit) {
        val s = Switch(this).apply {
            text = label; isChecked = value; textSize = 16f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, Ui.dp(this@SettingsActivity, 8))
        }
        form.addView(s)
        saves += { onSave(s.isChecked) }
    }

    private fun button(label: String, onClick: () -> Unit) = form.addView(Button(this).apply {
        text = label; isAllCaps = false
        setOnClickListener { onClick() }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@SettingsActivity, 6) })

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
