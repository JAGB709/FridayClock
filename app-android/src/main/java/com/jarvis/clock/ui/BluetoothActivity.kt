package com.jarvis.clock.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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
        window.setBackgroundDrawable(ColorDrawable(Tokens.Color.BG))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(16))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(Widgets.iconButton(this, "chevron-left", "Volver") { finish() },
            LinearLayout.LayoutParams(dp(Widgets.TOUCH), dp(Widgets.TOUCH)))
        header.addView(Widgets.label(this, "Altavoz Bluetooth", 28, Tokens.Color.TEXT, Fonts.semibold(this)).apply { setPadding(dp(8), 0, 0, 0) })
        root.addView(header)
        root.addView(Widgets.label(this, "Enciende el Bluetooth y hazlo visible; luego busca «Friday» en tu móvil y empareja. La música del móvil sonará por el reloj.",
            16, Tokens.Color.TEXT_MUTED).apply { setPadding(0, dp(8), 0, dp(8)) })

        root.addView(bigButton("Activar modo altavoz (visible 5 min)", Widgets.Kind.PRIMARY, "music") {
            val r = BluetoothControl.speakerMode(this, 300)
            if (r.needsUi) runCatching { startActivity(BluetoothControl.discoverableIntent(300)) }
            toast(r.message); refresh()
        })
        root.addView(bigButton("Encender Bluetooth", Widgets.Kind.SECONDARY, "check") { BluetoothControl.enable(this); refresh() })
        root.addView(bigButton("Hacer visible (diálogo del sistema)", Widgets.Kind.SECONDARY, "info") {
            runCatching { startActivity(BluetoothControl.discoverableIntent(300)) }; refresh()
        })
        root.addView(bigButton("Apagar Bluetooth", Widgets.Kind.DANGER, "x") { BluetoothControl.disable(this); refresh() })

        state = Widgets.label(this, "", 16, Tokens.Color.ACCENT).apply { setPadding(0, dp(16), 0, dp(4)) }
        paired = Widgets.label(this, "", 16, Tokens.Color.TEXT)
        root.addView(state); root.addView(paired)

        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Tokens.Color.BG) })
        refresh()
    }

    private fun bigButton(text: String, kind: Widgets.Kind, icon: String, onClick: () -> Unit) =
        Widgets.button(this, text, kind, icon) { onClick() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this@BluetoothActivity, 56)).apply { topMargin = Ui.dp(this@BluetoothActivity, 8) }
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
