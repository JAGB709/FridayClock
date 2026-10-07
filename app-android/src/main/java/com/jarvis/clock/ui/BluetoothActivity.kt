package com.jarvis.clock.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jarvis.clock.bt.BluetoothControl
import org.json.JSONObject

/** Pantalla de "modo altavoz": encender BT, hacer visible y emparejar el móvil. */
class BluetoothActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var state: TextView
    private lateinit var paired: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(window)
        val dp = { v: Int -> Ui.dp(this, v) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "Altavoz Bluetooth"; textSize = 26f; setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Enciende el Bluetooth y hazlo visible; luego busca “Friday” en tu móvil y empareja. La música del móvil sonará por el reloj."
            textSize = 15f; setTextColor(Ui.DIM); setPadding(0, dp(6), 0, dp(12))
        })

        root.addView(bigButton("Activar modo altavoz (visible 5 min)", "#FF0277BD") {
            val r = BluetoothControl.speakerMode(this, 300)
            if (r.needsUi) runCatching { startActivity(BluetoothControl.discoverableIntent(300)) }
            toast(r.message); refresh()
        })
        root.addView(bigButton("Encender Bluetooth", "#FF263238") { BluetoothControl.enable(this); refresh() })
        root.addView(bigButton("Hacer visible (diálogo del sistema)", "#FF263238") {
            runCatching { startActivity(BluetoothControl.discoverableIntent(300)) }; refresh()
        })
        root.addView(bigButton("Apagar Bluetooth", "#FF263238") { BluetoothControl.disable(this); refresh() })

        state = TextView(this).apply { textSize = 16f; setTextColor(Ui.ACCENT); setPadding(0, dp(14), 0, dp(4)) }
        paired = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE) }
        root.addView(state); root.addView(paired)
        root.addView(bigButton("Volver", "#FF263238") { finish() })

        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Color.BLACK) })
        refresh()
    }

    private fun bigButton(text: String, bg: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 18f
        setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor(bg))
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, Ui.dp(this@BluetoothActivity, 56))
        lp.topMargin = Ui.dp(this@BluetoothActivity, 8); layoutParams = lp
    }

    private fun refresh() {
        val s: JSONObject = BluetoothControl.status(this)
        if (!s.optBoolean("available")) { state.text = "Este reloj no tiene Bluetooth."; return }
        val on = s.optBoolean("enabled"); val vis = s.optBoolean("discoverable"); val conn = s.optBoolean("phone_connected")
        state.text = buildString {
            append(if (on) "Bluetooth encendido" else "Bluetooth apagado")
            if (vis) append(" · visible")
            append(if (conn) " · móvil conectado" else " · sin móvil conectado")
        }
        val arr = s.optJSONArray("paired")
        paired.text = if (arr == null || arr.length() == 0) "Sin dispositivos emparejados." else buildString {
            append("Emparejados:\n")
            for (i in 0 until arr.length()) append("· ${arr.getJSONObject(i).optString("name")}\n")
        }
        // Refresco suave para ver cuando el móvil conecta.
        handler.postDelayed({ if (!isFinishing) refresh() }, 3000)
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun toast(m: String) = android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show()
}
