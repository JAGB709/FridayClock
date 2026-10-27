package com.jarvis.clock.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import com.jarvis.clock.ClockService
import com.jarvis.clock.Prefs
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.assistant.Assistant
import com.jarvis.clock.assistant.Backend
import com.jarvis.clock.design.Brightness
import com.jarvis.clock.design.DesignSchema
import com.jarvis.clock.design.DesignStore
import com.jarvis.clock.server.NetUtil
import java.util.Calendar
import kotlin.concurrent.thread

/** Ajustes: lo que antes estaba hardcodeado (token, backend, ciudad...) ahora se edita aquí. */
class SettingsActivity : Activity() {

    private val saves = mutableListOf<() -> Unit>()
    private lateinit var form: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = { v: Int -> Ui.dp(this, v) }
        window.setBackgroundDrawable(ColorDrawable(Tokens.Color.BG))
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(24))
        }
        cur = form

        info("Dirección: ${ips().joinToString(", ") { "$it:${Prefs.httpPort}" }.ifEmpty { "sin red" }}")

        section("Servidor del reloj")
        secret("Token (X-Auth-Token)", Prefs.authToken) { Prefs.authToken = it }
        number("Puerto HTTP", Prefs.httpPort) { if (it in 1024..65535 && it != Prefs.httpPort) { Prefs.httpPort = it; ClockService.restartServer(this) } }
        switch("Escuchar solo en Tailscale (recomendado)", Prefs.bindTailscaleOnly) {
            if (it != Prefs.bindTailscaleOnly) { Prefs.bindTailscaleOnly = it; ClockService.restartServer(this) }
        }

        section("Asistente")
        text("Nombre del asistente", Prefs.assistantName) { Prefs.assistantName = it }
        text("URL del puente del homelab", Prefs.backendUrl, hint = "http://100.x.y.z:8765") { Prefs.backendUrl = it }
        secret("Token del puente", Prefs.backendToken) { Prefs.backendToken = it }
        switch("Activar al decir \"${Prefs.assistantName}\" (escucha continua)", Prefs.wakeWord) { Prefs.wakeWord = it }
        switch("Seguir escuchando si pregunta algo", Prefs.followUp) { Prefs.followUp = it }
        decimal("Velocidad de voz (de 0,5 a 2)", Prefs.ttsRate.toDouble()) { Prefs.ttsRate = it.toFloat() }
        button("Probar conexión con Hermes", "cloud") { testBackend() }
        button("Ajustes de voz del sistema (TTS)", "volume-2") {
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
        button("Probar alarma (suena en 10 s)", "bell") {
            saveAll()
            AlarmScheduler.createTimer(this, 10, "Prueba de alarma")
            toast("Sonará en 10 segundos")
        }

        section("Pantalla")
        brightnessRow()
        number("Modo noche desde (hora)", Prefs.nightStartHour) { Prefs.nightStartHour = it }
        number("Modo noche hasta (hora)", Prefs.nightEndHour) { Prefs.nightEndHour = it }
        number("Brillo de noche (%)", Prefs.nightBrightness) { Prefs.nightBrightness = it }
        number("Brillo de día (%)", Prefs.dayBrightness) { Prefs.dayBrightness = it }

        section("Altavoz Bluetooth")
        button("Modo altavoz Bluetooth (conectar el móvil)", "music") { startActivity(Intent(this, BluetoothActivity::class.java)) }

        section("Red")
        button("Wi-Fi (elegir red y contraseña)", "wifi") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        button("Tailscale") {
            // La VPN mantiene el reloj accesible desde el homelab en cualquier Wi-Fi.
            packageManager.getLaunchIntentForPackage("com.tailscale.ipn")?.let { startActivity(it) }
        }

        section("Sistema")
        button("Elegir launcher predeterminado") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        button("Ajustes de Android", "settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }
        header.addView(Widgets.iconButton(this, "chevron-left", "Volver sin guardar") { finish() },
            LinearLayout.LayoutParams(dp(Widgets.TOUCH), dp(Widgets.TOUCH)))
        header.addView(Widgets.label(this, "Ajustes", 28, Tokens.Color.TEXT, Fonts.semibold(this)).apply { setPadding(dp(8), 0, 0, 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Widgets.button(this, "Guardar", Widgets.Kind.PRIMARY, "check") { saveAll(); toast("Guardado"); finish() },
            LinearLayout.LayoutParams(-2, dp(Widgets.TOUCH)))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Tokens.Color.BG) }
        root.addView(header)
        root.addView(ScrollView(this).apply { addView(form) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
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

    private lateinit var cur: LinearLayout

    private fun add(v: View, lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)) = cur.addView(v, lp)

    private fun info(t: String) = add(Widgets.label(this, t, 16, Tokens.Color.ACCENT).apply { setTextIsSelectable(true) })

    /** Cada sección es una tarjeta con su título encima. */
    private fun section(t: String) {
        form.addView(Widgets.label(this, t, 14, Tokens.Color.TEXT_MUTED, Fonts.semibold(this)).apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 20), 0, Ui.dp(this@SettingsActivity, 8))
        })
        cur = Widgets.card(this)
        form.addView(cur)
    }

    private fun field(label: String, value: String, type: Int, hint: String = "", onSave: (String) -> Unit) {
        add(Widgets.label(this, label, 14, Tokens.Color.TEXT_MUTED).apply { setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, Ui.dp(this@SettingsActivity, 4)) })
        val e = Widgets.field(this, value, type, hint)
        add(e, LinearLayout.LayoutParams(-1, Ui.dp(this, Widgets.TOUCH)))
        saves += { onSave(e.text.toString()) }
    }

    /** Campo de token: oculto por defecto, con botón Mostrar/Ocultar. */
    private fun secret(label: String, value: String, onSave: (String) -> Unit) {
        form.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(Ui.DIM); typeface = Fonts.regular(this@SettingsActivity) })
        val e = EditText(this).apply {
            setText(value); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine()
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        }
        form.addView(e)
        form.addView(Button(this).apply {
            text = "Mostrar"; isAllCaps = false
            setOnClickListener {
                val hidden = e.transformationMethod is android.text.method.PasswordTransformationMethod
                e.transformationMethod = if (hidden) android.text.method.HideReturnsTransformationMethod.getInstance()
                    else android.text.method.PasswordTransformationMethod.getInstance()
                text = if (hidden) "Ocultar" else "Mostrar"
            }
        }, LinearLayout.LayoutParams(-2, -2))
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
        val s = Widgets.switch(this, label, value)
        add(s, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@SettingsActivity, 4) })
        saves += { onSave(s.isChecked) }
    }

    private fun button(label: String, icon: String? = null, onClick: () -> Unit) =
        add(Widgets.button(this, label, Widgets.Kind.SECONDARY, icon) { onClick() },
            LinearLayout.LayoutParams(-1, Ui.dp(this, Widgets.TOUCH)).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

    private fun minuteOfDay(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Brillo efectivo ahora: el manual si sigue vigente, si no el del perfil programado. */
    private fun currentBrightness(): Int {
        val saved = DesignStore.load(this).design
        val profile = DesignSchema.activeProfile(saved, minuteOfDay())
        val id = profile?.optString("id").orEmpty()
        val manual = Brightness.valid(Prefs.manualBrightness, id, saved)
        return Brightness.effective(profile?.optInt("brightness", 70) ?: 70, manual, id)
    }

    private fun applyNow(pct: Int) {
        window.attributes = window.attributes.apply { screenBrightness = pct / 100f }
    }

    /** Deslizador de brillo actual: al soltar guarda el valor manual y lo aplica; el botón vuelve al del panel. */
    private fun brightnessRow() {
        add(Widgets.label(this, "Brillo actual", 14, Tokens.Color.TEXT_MUTED))
        val value = Widgets.label(this, "", 16, Tokens.Color.TEXT)
        val initial = currentBrightness()
        value.text = "$initial %"
        applyNow(initial)
        val bar = Widgets.seekBar(this, 99, initial - 1).apply { // 0..99 equivale a 1..100 %
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    value.text = "${p + 1} %"
                    if (fromUser) applyNow(p + 1)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {
                    val pct = sb.progress + 1
                    Brightness.setManual(this@SettingsActivity, pct, minuteOfDay())
                    applyNow(pct)
                    value.text = "$pct %"
                }
            })
        }
        add(bar, LinearLayout.LayoutParams(-1, Ui.dp(this, Widgets.TOUCH)))
        add(value)
        button("Volver al brillo del panel", "rotate-ccw") {
            Brightness.clearManual()
            val pct = currentBrightness()
            bar.progress = pct - 1
            value.text = "$pct %"
            applyNow(pct)
            toast("Brillo del panel restaurado")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
