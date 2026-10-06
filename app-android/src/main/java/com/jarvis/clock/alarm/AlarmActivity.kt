package com.jarvis.clock.alarm

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.clock.Events
import com.jarvis.clock.Prefs
import com.jarvis.clock.design.DesignStore
import com.jarvis.clock.design.FaceData
import com.jarvis.clock.design.darken
import com.jarvis.clock.design.parseHex
import com.jarvis.clock.design.readableOn
import com.jarvis.clock.ui.FaceHost
import com.jarvis.clock.ui.Ui
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de alarma. La zona de arriba la dibuja el motor de pantallas (diseño `alarm_screen`);
 * los botones son nativos y enormes para acertar medio dormido. El brillo sube poco a poco
 * (efecto amanecer) a la vez que el volumen. Si el WebView falla, vuelve la zona nativa de siempre.
 */
class AlarmActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var time: TextView? = null
    private var faceHost: FaceHost? = null
    private lateinit var label: String
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val startedAt = System.currentTimeMillis()

    private val listener = Events.Listener { e ->
        if (e is Events.Event.AlarmStopped) finish()
    }

    private val hostListener = object : FaceHost.Listener {
        override fun onTap() {}
        override fun onDoubleTap() {}
        override fun onLongPress() {}
        override fun onPermission() {}
        override fun onFailure(reason: String) { runOnUiThread { rebuildNative() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        Ui.immersive(window)
        label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarma"
        setContentView(buildRoot(web = true))
        Events.register(listener)
        tick()
    }

    private fun rebuildNative() {
        faceHost?.release()
        faceHost = null
        setContentView(buildRoot(web = false))
    }

    private fun themeColor(o: JSONObject?, key: String, default: Int): Int =
        runCatching { parseHex(o!!.getString(key)) }.getOrDefault(default)

    private fun buildRoot(web: Boolean): LinearLayout {
        val dp = { v: Int -> Ui.dp(this, v) }
        val design = DesignStore.load(this).design
        val alarm = design.optJSONObject("alarm_screen")
        val theme = design.optJSONObject("themes")?.optJSONObject(alarm?.optString("theme").orEmpty())
        val accent = themeColor(theme, "accent", Ui.ACCENT)
        val bg = theme?.optJSONObject("bg")
        val rootBg = if (bg?.optString("type") == "color") themeColor(bg, "color", Color.BLACK) else Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(rootBg)
            setPadding(0, 0, 0, dp(16))
        }

        val top: View = (if (web) FaceHost.create(this, hostListener) else null)?.let { host ->
            faceHost = host
            host.render(design, JSONObject().put("alarm", true))
            host.setData(FaceData.build(this, ringingLabel = label))
            host.view
        } ?: nativeTop()
        root.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))

        val snoozeText = alarm?.optString("snooze_label").orEmpty().ifEmpty { "Posponer {min} min" }
            .replace("{min}", Prefs.snoozeMinutes.toString())
        val dismissText = alarm?.optString("dismiss_label").orEmpty().ifEmpty { "Descartar" }
        val snoozeBg = darken(accent, 0.3f)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), 0)
        }
        buttons.addView(
            bigButton(snoozeText, snoozeBg, readableOn(snoozeBg)) { AlarmRingService.snooze(this); finish() },
            LinearLayout.LayoutParams(0, dp(96), 1f).apply { marginEnd = dp(12) },
        )
        buttons.addView(
            bigButton(dismissText, accent, readableOn(accent)) { AlarmRingService.dismiss(this); finish() },
            LinearLayout.LayoutParams(0, dp(96), 1f),
        )
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    /** La zona de arriba de siempre: hora grande y nombre de la alarma. */
    private fun nativeTop(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val t = TextView(this@AlarmActivity).apply {
            setTextColor(Color.WHITE)
            textSize = 96f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        t.text = fmt.format(Date())
        time = t
        addView(t)
        addView(TextView(this@AlarmActivity).apply {
            text = label
            setTextColor(Ui.ACCENT)
            textSize = 30f
            gravity = Gravity.CENTER
        })
    }

    private fun bigButton(text: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 24f
        setTextColor(fg)
        setBackgroundColor(bg)
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun tick() {
        time?.text = fmt.format(Date())
        // Amanecer: el brillo de la pantalla sigue a la rampa de volumen.
        val ramp = (Prefs.alarmRampSeconds * 1000L).coerceAtLeast(1)
        val t = ((System.currentTimeMillis() - startedAt).toFloat() / ramp).coerceIn(0f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = 0.05f + 0.95f * t }
        handler.postDelayed(::tick, 1000)
    }

    private fun showOverLockscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    override fun onResume() {
        super.onResume()
        faceHost?.view?.onResume()
        if (!AlarmRingService.ringing) finish()
    }

    override fun onPause() {
        faceHost?.view?.onPause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Atrás no apaga la alarma: hay que elegir.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Events.unregister(listener)
        faceHost?.release()
        faceHost = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_LABEL = "label"
        fun intent(ctx: Context, label: String): Intent =
            Intent(ctx, AlarmActivity::class.java)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
