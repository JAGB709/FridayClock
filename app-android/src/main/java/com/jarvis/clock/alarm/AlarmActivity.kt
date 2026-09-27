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
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.clock.Events
import com.jarvis.clock.Prefs
import com.jarvis.clock.ui.Ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de alarma. El brillo sube poco a poco (efecto amanecer) a la vez
 * que el volumen, y los botones son enormes para acertar medio dormido.
 */
class AlarmActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var time: TextView
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val startedAt = System.currentTimeMillis()

    private val listener = Events.Listener { e ->
        if (e is Events.Event.AlarmStopped) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        Ui.immersive(window)

        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarma"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(Ui.dp(this@AlarmActivity, 24), Ui.dp(this@AlarmActivity, 16), Ui.dp(this@AlarmActivity, 24), Ui.dp(this@AlarmActivity, 16))
        }
        time = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 96f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        val lbl = TextView(this).apply {
            text = label
            setTextColor(Ui.ACCENT)
            textSize = 30f
            gravity = Gravity.CENTER
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val snooze = bigButton("Posponer ${Prefs.snoozeMinutes} min", Color.parseColor("#FF263238")) {
            AlarmRingService.snooze(this); finish()
        }
        val dismiss = bigButton("Descartar", Color.parseColor("#FF0277BD")) {
            AlarmRingService.dismiss(this); finish()
        }
        buttons.addView(snooze, LinearLayout.LayoutParams(0, Ui.dp(this, 96), 1f).apply { marginEnd = Ui.dp(this@AlarmActivity, 12) })
        buttons.addView(dismiss, LinearLayout.LayoutParams(0, Ui.dp(this, 96), 1f))

        root.addView(time)
        root.addView(lbl, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@AlarmActivity, 28) })
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        Events.register(listener)
        tick()
    }

    private fun bigButton(text: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 24f
        setTextColor(Color.WHITE)
        setBackgroundColor(bg)
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun tick() {
        time.text = fmt.format(Date())
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
        if (!AlarmRingService.ringing) finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Atrás no apaga la alarma: hay que elegir.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Events.unregister(listener)
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
