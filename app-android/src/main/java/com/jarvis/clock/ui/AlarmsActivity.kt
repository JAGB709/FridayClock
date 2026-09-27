package com.jarvis.clock.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.ToggleButton
import com.jarvis.clock.Events
import com.jarvis.clock.alarm.Alarm
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.alarm.AlarmStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista y edición de alarmas y temporizadores, pensada para dedo en pantalla pequeña. */
class AlarmsActivity : Activity() {

    private lateinit var list: LinearLayout
    private val listener = Events.Listener { if (it is Events.Event.AlarmsChanged) render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(window)
        val dp = { v: Int -> Ui.dp(this, v) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(this).apply {
            text = "‹"; textSize = 26f; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        header.addView(TextView(this).apply {
            text = "Alarmas"; textSize = 26f; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "+ Nueva"; textSize = 18f; isAllCaps = false
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#FF0277BD"))
            setOnClickListener { edit(null) }
        }, LinearLayout.LayoutParams(-2, dp(52)))
        root.addView(header)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        Events.register(listener)
        render()
    }

    override fun onDestroy() {
        Events.unregister(listener)
        super.onDestroy()
    }

    private fun render() {
        list.removeAllViews()
        val alarms = AlarmStore.all(this)
        if (alarms.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No hay alarmas.\nTambién puedes decir: «pon una alarma a las siete y media entre semana»."
                textSize = 18f; setTextColor(Ui.DIM); gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@AlarmsActivity, 40), 0, 0)
            })
            return
        }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        for (a in alarms) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(this@AlarmsActivity, 8), Ui.dp(this@AlarmsActivity, 10), Ui.dp(this@AlarmsActivity, 8), Ui.dp(this@AlarmsActivity, 10))
                setOnClickListener { if (!a.isAbsolute) edit(a) }
                setOnLongClickListener { confirmDelete(a); true }
            }
            val color = if (a.enabled) Color.WHITE else Ui.DIM
            row.addView(TextView(this).apply {
                text = a.timeText(); textSize = 40f; setTextColor(color)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Ui.dp(this@AlarmsActivity, 16) })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this).apply { text = a.label; textSize = 18f; setTextColor(color) })
            info.addView(TextView(this).apply {
                text = if (a.isAbsolute) "${a.daysText()} · suena a las ${fmt.format(Date(a.triggerAt))}" else a.daysText()
                textSize = 15f; setTextColor(Ui.DIM)
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            if (a.isAbsolute) {
                row.addView(Button(this).apply {
                    text = "Cancelar"; isAllCaps = false
                    setOnClickListener { AlarmScheduler.delete(this@AlarmsActivity, a.id) }
                })
            } else {
                row.addView(Switch(this).apply {
                    isChecked = a.enabled
                    scaleX = 1.3f; scaleY = 1.3f
                    setOnCheckedChangeListener { _, on -> AlarmScheduler.setEnabled(this@AlarmsActivity, a.id, on) }
                })
            }
            list.addView(row)
            list.addView(android.view.View(this).apply { setBackgroundColor(Color.parseColor("#FF263238")) }, LinearLayout.LayoutParams(-1, 1))
        }
    }

    private fun confirmDelete(a: Alarm) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar la alarma de las ${a.timeText()}?")
            .setPositiveButton("Borrar") { _, _ -> AlarmScheduler.delete(this, a.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun edit(existing: Alarm?) {
        val dp = { v: Int -> Ui.dp(this, v) }
        val now = java.util.Calendar.getInstance()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val pickers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val hour = NumberPicker(this).apply {
            minValue = 0; maxValue = 23; setFormatter { "%02d".format(it) }
            value = existing?.hour ?: now.get(java.util.Calendar.HOUR_OF_DAY)
        }
        val minute = NumberPicker(this).apply {
            minValue = 0; maxValue = 59; setFormatter { "%02d".format(it) }
            value = existing?.minute ?: 0
        }
        pickers.addView(hour)
        pickers.addView(TextView(this).apply { text = ":"; textSize = 32f })
        pickers.addView(minute)
        form.addView(pickers)

        val days = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val toggles = Alarm.DAY_SHORT.mapIndexed { i, d ->
            ToggleButton(this).apply {
                textOn = d; textOff = d; text = d
                isChecked = (existing?.days ?: 0) and (1 shl i) != 0
            }.also { days.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        }
        form.addView(days)
        val label = EditText(this).apply {
            hint = "Etiqueta"
            setText(existing?.label ?: "Alarma")
            setSingleLine()
        }
        form.addView(label)

        val b = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nueva alarma" else "Editar alarma")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Guardar") { _, _ ->
                var mask = 0
                toggles.forEachIndexed { i, t -> if (t.isChecked) mask = mask or (1 shl i) }
                val l = label.text.toString().ifBlank { "Alarma" }
                if (existing == null) {
                    AlarmScheduler.create(this, hour.value, minute.value, mask, l)
                } else {
                    AlarmScheduler.save(this, existing.copy(hour = hour.value, minute = minute.value, days = mask, label = l, enabled = true))
                }
            }
            .setNegativeButton("Cancelar", null)
        if (existing != null) b.setNeutralButton("Borrar") { _, _ -> AlarmScheduler.delete(this, existing.id) }
        b.show()
    }
}
