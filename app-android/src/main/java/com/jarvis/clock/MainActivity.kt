package com.jarvis.clock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.jarvis.clock.Events.Event
import com.jarvis.clock.Events.State
import com.jarvis.clock.alarm.AlarmActivity
import com.jarvis.clock.alarm.AlarmRingService
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.assistant.Actions
import com.jarvis.clock.assistant.Assistant
import com.jarvis.clock.ui.AlarmsActivity
import com.jarvis.clock.ui.SettingsActivity
import com.jarvis.clock.ui.Ui
import com.jarvis.clock.weather.Weather
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Launcher del reloj: carátula con hora, fecha, tiempo y próxima alarma;
 * botón de micro para el asistente; capa para /display; modo noche.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var keyLabel: TextView
    private val hideKeyLabel = Runnable { keyLabel.visibility = View.GONE }
    private val es = Locale("es", "ES")
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE, d 'de' MMMM", es)

    private lateinit var root: FrameLayout
    private lateinit var face: LinearLayout
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private lateinit var nextAlarm: TextView
    private lateinit var bar: LinearLayout
    private lateinit var mic: ImageButton
    private lateinit var panel: LinearLayout
    private lateinit var panelState: TextView
    private lateinit var panelHeard: TextView
    private lateinit var panelReply: TextView
    private lateinit var level: ProgressBar
    private var displayLayer: FrameLayout? = null
    private var webView: WebView? = null

    private var night = false
    private var wakeUntil = 0L
    private var brightnessOverride: Int? = null
    private val assistant by lazy { Assistant.get(this) }

    private val listener = Events.Listener { onEvent(it) }

    private companion object {
        /** Paso de los botones de volumen, en % (el stream multimedia tiene 100 niveles). */
        const val VOLUME_STEP = 5
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Ui.immersive(window)
        buildUi()
        setContentView(root)
        Events.register(listener)
        requestPermissionsIfNeeded()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND -> handler.postDelayed({ assistant.listen() }, 300)
        }
    }

    override fun onResume() {
        super.onResume()
        Ui.immersive(window)
        tickMinute()
        refreshWeather()
        refreshNextAlarm()
        if (AlarmRingService.ringing) startActivity(AlarmActivity.intent(this, AlarmRingService.currentLabel ?: "Alarma"))
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        Events.unregister(listener)
        destroyWebView()
        super.onDestroy()
    }

    // Los botones físicos no hacen nada en el framework de este reloj (los gestionaba el
    // asistente de Google, deshabilitado): como launcher los recogemos nosotros.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val t0 = SystemClock.uptimeMillis()
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { stepVolume(VOLUME_STEP); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { stepVolume(-VOLUME_STEP); true }
            KeyEvent.KEYCODE_MUTE -> { toggleMicMute(); true } // KEY_MICMUTE del botón central
            else -> false
        }
        if (handled) {
            Log.i("Keys", "key=$keyCode repeticion=${event.repeatCount} cola=${t0 - event.eventTime}ms manejo=${SystemClock.uptimeMillis() - t0}ms")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun stepVolume(delta: Int) {
        showKeyLabel("Volumen ${Actions.setVolume(this, 0, delta)}%")
    }

    private fun toggleMicMute() {
        val am = getSystemService(AudioManager::class.java)
        am.isMicrophoneMute = !am.isMicrophoneMute
        showKeyLabel(if (am.isMicrophoneMute) "Micrófono silenciado" else "Micrófono activado")
    }

    private fun showKeyLabel(text: String) {
        keyLabel.text = text
        keyLabel.visibility = View.VISIBLE
        handler.removeCallbacks(hideKeyLabel)
        handler.postDelayed(hideKeyLabel, 1200)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Somos el launcher: "atrás" solo cierra capas.
        when {
            displayLayer?.visibility == View.VISIBLE -> hideDisplay()
            panel.visibility == View.VISIBLE -> assistant.stopAll()
        }
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        val dp = { v: Int -> Ui.dp(this, v) }
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        face = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val shortSide = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        time = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * 0.42f)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        date = label(22f, Ui.DIM)
        weather = label(20f, Ui.DIM)
        nextAlarm = label(18f, Ui.ACCENT).apply {
            setOnClickListener { openAlarms() }
        }
        face.addView(time)
        face.addView(date)
        face.addView(weather, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        face.addView(nextAlarm, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        root.addView(face, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(56) })

        // Barra inferior: alarmas · micro · ajustes
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val alarmsBtn = iconButton(android.R.drawable.ic_lock_idle_alarm) { openAlarms() }
        mic = iconButton(android.R.drawable.ic_btn_speak_now) { assistant.toggleListening() }.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF0D47A1")) }
        }
        val settingsBtn = iconButton(android.R.drawable.ic_menu_preferences) { startActivity(Intent(this, SettingsActivity::class.java)) }
        bar.addView(alarmsBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        bar.addView(mic, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginStart = dp(40); marginEnd = dp(40) })
        bar.addView(settingsBtn, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(8) })

        // Aviso instantáneo de los botones físicos (un Toast tarda en aparecer y se encola).
        keyLabel = TextView(this).apply {
            textSize = 26f; setTextColor(Color.WHITE); visibility = View.GONE
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.parseColor("#CC101820")) }
        }
        root.addView(keyLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(16) })

        // Panel del asistente
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.parseColor("#E6101820")) }
            visibility = View.GONE
            setOnClickListener { assistant.stopAll() }
        }
        panelState = label(16f, Ui.ACCENT).apply { gravity = Gravity.START }
        level = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        panelHeard = label(20f, Ui.DIM).apply { gravity = Gravity.START }
        panelReply = label(24f, Color.WHITE).apply { gravity = Gravity.START; maxLines = 6 }
        panel.addView(panelState)
        panel.addView(level, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6); bottomMargin = dp(6) })
        panel.addView(panelHeard)
        panel.addView(panelReply)
        root.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(16), dp(16), dp(16), dp(80))
        })

        // Gestos: doble toque = hablar; toque en modo noche = despertar pantalla; largo = ajustes.
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { wakeScreen(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { assistant.listen(); return true }
            override fun onLongPress(e: MotionEvent) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        face.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
    }

    private fun label(sp: Float, color: Int) = TextView(this).apply {
        textSize = sp
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    private fun iconButton(icon: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        setBackgroundColor(Color.TRANSPARENT)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 10))
        setOnClickListener { wakeScreen(); onClick() }
    }

    private fun openAlarms() = startActivity(Intent(this, AlarmsActivity::class.java))

    // ------------------------------------------------------------------ reloj

    private fun tickMinute() {
        val now = Date()
        time.text = timeFmt.format(now)
        date.text = dateFmt.format(now).replaceFirstChar { it.uppercase() }
        applyNightMode()
        antiBurnIn()
        val c = Calendar.getInstance()
        if (c.get(Calendar.MINUTE) % 20 == 0) refreshWeather()
        refreshNextAlarm()
        val msToNextMinute = 60_000 - (System.currentTimeMillis() % 60_000) + 50
        handler.removeCallbacks(minuteRunnable)
        handler.postDelayed(minuteRunnable, msToNextMinute)
    }

    private val minuteRunnable = Runnable { tickMinute() }

    /** Desplaza la carátula unos píxeles cada minuto para no quemar la pantalla. */
    private fun antiBurnIn() {
        val m = Calendar.getInstance().get(Calendar.MINUTE)
        val d = Ui.dp(this, 6).toFloat()
        face.translationX = ((m % 5) - 2) * d
        face.translationY = (((m / 5) % 3) - 1) * d
    }

    private fun applyNightMode() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val awake = System.currentTimeMillis() < wakeUntil
        night = Prefs.isNight(hour) && !awake
        val color = if (night) Ui.NIGHT else Color.WHITE
        time.setTextColor(color)
        date.setTextColor(if (night) Ui.NIGHT else Ui.DIM)
        weather.visibility = if (night) View.GONE else View.VISIBLE
        nextAlarm.setTextColor(if (night) Ui.NIGHT else Ui.ACCENT)
        bar.alpha = if (night) 0.15f else 1f
        val pct = brightnessOverride ?: if (night) Prefs.nightBrightness else Prefs.dayBrightness
        window.attributes = window.attributes.apply { screenBrightness = pct / 100f }
    }

    private fun wakeScreen() {
        if (!night) return
        wakeUntil = System.currentTimeMillis() + 15_000
        applyNightMode()
        handler.postDelayed({ applyNightMode() }, 15_500)
    }

    private fun refreshWeather() {
        Weather.cached()?.let { weather.text = it.shortText() }
        thread(name = "weather") {
            val w = Weather.get() ?: return@thread
            runOnUiThread { weather.text = w.shortText() }
        }
    }

    private fun refreshNextAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            nextAlarm.text = "⚠ Concede el permiso de alarmas exactas (toca aquí)"
            nextAlarm.setOnClickListener {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
            return
        }
        nextAlarm.setOnClickListener { openAlarms() }
        val next = AlarmScheduler.nextAlarm(this)
        nextAlarm.text = next?.let { (a, at) ->
            val c = Calendar.getInstance().apply { timeInMillis = at }
            val today = Calendar.getInstance()
            val day = when (c.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)) {
                0 -> "hoy"; 1 -> "mañana"; else -> SimpleDateFormat("EEE", es).format(Date(at))
            }
            "⏰ ${a.label} · $day ${a.timeText()}"
        } ?: ""
    }

    // ------------------------------------------------------------------ eventos

    private fun onEvent(e: Event) {
        when (e) {
            is Event.AssistantState -> showAssistant(e.state, e.text)
            is Event.MicLevel -> level.progress = (e.level * 100).toInt()
            is Event.Display -> showDisplay(e)
            Event.DisplayClear -> hideDisplay()
            Event.AlarmsChanged -> refreshNextAlarm()
            is Event.AlarmRinging -> runCatching { startActivity(AlarmActivity.intent(this, e.label)) }
            Event.AlarmStopped -> refreshNextAlarm()
            is Event.Brightness -> { brightnessOverride = e.percent; applyNightMode() }
            is Event.SettingsChanged -> {
                // Un brillo nuevo desde el panel manda sobre un /brightness puntual anterior.
                if (e.keys.any { it.endsWith("brightness") }) brightnessOverride = null
                applyNightMode()
            }
            is Event.BluetoothDiscoverable -> runCatching {
                startActivity(com.jarvis.clock.bt.BluetoothControl.discoverableIntent(e.seconds))
            }
        }
    }

    private fun showAssistant(s: State, text: String) {
        when (s) {
            State.IDLE -> {
                handler.postDelayed({ if (assistant.state == State.IDLE) panel.visibility = View.GONE }, 4000)
                mic.alpha = 1f
                level.visibility = View.GONE
                return
            }
            State.LISTENING -> {
                wakeScreen()
                panelState.text = "Te escucho…"
                panelReply.text = ""
                panelHeard.text = text
                level.visibility = View.VISIBLE
            }
            State.HEARD -> panelHeard.text = "«$text»"
            State.THINKING -> { panelState.text = "Pensando…"; level.visibility = View.GONE; if (text.isNotEmpty()) panelHeard.text = "«$text»" }
            State.SPEAKING -> { panelState.text = Prefs.assistantName; panelReply.text = text; level.visibility = View.GONE }
            State.ERROR -> { panelState.text = "Error"; panelReply.text = text }
        }
        mic.alpha = 0.6f
        panel.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ /display

    private val hideDisplayRunnable = Runnable { hideDisplay() }

    private fun showDisplay(e: Event.Display) {
        wakeScreen()
        val layer = displayLayer ?: FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { hideDisplay() }
            root.addView(this, FrameLayout.LayoutParams(-1, -1))
            displayLayer = this
        }
        layer.removeAllViews()
        destroyWebView()
        when (e.type) {
            "html", "url" -> {
                val wv = WebView(this).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    if (e.type == "url") loadUrl(e.content)
                    else loadDataWithBaseURL(null, e.content, "text/html", "utf-8", null)
                }
                webView = wv
                layer.addView(wv, FrameLayout.LayoutParams(-1, -1))
                // Tocar la esquina superior derecha cierra (el WebView se come los toques).
                layer.addView(View(this).apply { setOnClickListener { hideDisplay() } },
                    FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.TOP or Gravity.END))
            }
            "image" -> {
                val iv = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setOnClickListener { hideDisplay() }
                }
                layer.addView(iv, FrameLayout.LayoutParams(-1, -1))
                thread(name = "display-img") {
                    val bmp = runCatching { loadBitmap(e.content) }.getOrNull()
                    runOnUiThread { if (bmp != null) iv.setImageBitmap(bmp) }
                }
            }
            else -> {
                val tv = TextView(this).apply {
                    text = e.content
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 24))
                    setAutoSizeTextTypeUniformWithConfiguration(16, 96, 2, TypedValue.COMPLEX_UNIT_SP)
                    setOnClickListener { hideDisplay() }
                }
                layer.addView(tv, FrameLayout.LayoutParams(-1, -1))
            }
        }
        layer.visibility = View.VISIBLE
        handler.removeCallbacks(hideDisplayRunnable)
        handler.postDelayed(hideDisplayRunnable, e.durationMs)
    }

    private fun hideDisplay() {
        handler.removeCallbacks(hideDisplayRunnable)
        displayLayer?.let { it.visibility = View.GONE; it.removeAllViews() }
        destroyWebView() // libera los ~30 MB del WebView en cuanto no se usa
    }

    private fun destroyWebView() {
        webView?.let { it.stopLoading(); (it.parent as? FrameLayout)?.removeView(it); it.destroy() }
        webView = null
    }

    /** URL http(s), data URI o base64 pelado. Submuestrea para no reventar la RAM. */
    private fun loadBitmap(src: String): Bitmap? {
        val bytes = when {
            src.startsWith("http") -> URL(src).openStream().use { it.readBytes() }
            src.startsWith("data:") -> Base64.decode(src.substringAfter(","), Base64.DEFAULT)
            else -> Base64.decode(src, Base64.DEFAULT)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxSide = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    // ------------------------------------------------------------------ permisos

    private fun requestPermissionsIfNeeded() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }
}
