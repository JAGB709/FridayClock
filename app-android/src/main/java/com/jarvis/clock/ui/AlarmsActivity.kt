package com.jarvis.clock.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jarvis.clock.Events
import com.jarvis.clock.alarm.Alarm
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.alarm.AlarmStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Lista y edición de alarmas y temporizadores (sistema Obsidiana), para dedo o D-pad en 800x480. */
class AlarmsActivity : Activity() {

    private lateinit var list: LinearLayout
    private val listener = Events.Listener { if (it is Events.Event.AlarmsChanged) render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(window)
        window.setBackgroundDrawable(ColorDrawable(Tokens.Color.BG))
        val dp = { v: Int -> Ui.dp(this, v) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Tokens.Color.BG)
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(Widgets.iconButton(this, "chevron-left", "Volver") { finish() },
            LinearLayout.LayoutParams(dp(Widgets.TOUCH), dp(Widgets.TOUCH)))
        header.addView(Widgets.label(this, "Alarmas", 28, Tokens.Color.TEXT, Fonts.semibold(this)).apply { setPadding(dp(8), 0, 0, 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Widgets.button(this, "Nueva", Widgets.Kind.PRIMARY, "plus") { edit(null) },
            LinearLayout.LayoutParams(-2, dp(Widgets.TOUCH)))
        root.addView(header)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list); isFillViewport = true },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
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
        val dp = { v: Int -> Ui.dp(this, v) }
        val alarms = AlarmStore.all(this)
        if (alarms.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, 0)
            }
            empty.addView(android.widget.ImageView(this).apply { setImageDrawable(Icons.tinted(this@AlarmsActivity, "bell", Tokens.Color.TEXT_MUTED)) },
                LinearLayout.LayoutParams(dp(48), dp(48)))
            empty.addView(Widgets.label(this, "No hay alarmas", 20, Tokens.Color.TEXT, Fonts.semibold(this)).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4))
            })
            empty.addView(Widgets.label(this, "También puedes decir: «pon una alarma a las siete y media entre semana»", 16, Tokens.Color.TEXT_MUTED).apply {
                gravity = Gravity.CENTER
            })
            list.addView(empty, LinearLayout.LayoutParams(-1, -2))
            return
        }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        for (a in alarms) {
            val row = Widgets.focusableCard(this).apply {
                setOnClickListener { if (!a.isAbsolute) edit(a) }
                setOnLongClickListener { confirmDelete(a); true }
            }
            val color = if (a.enabled) Tokens.Color.TEXT else Tokens.Color.TEXT_MUTED
            row.addView(Widgets.label(this, a.timeText(), 40, color, Fonts.light(this)).apply { Fonts.tabular(this) },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(20) })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(Widgets.label(this, a.label, 18, color, Fonts.semibold(this)))
            val sub = if (a.isAbsolute) "${a.daysText()} · suena a las ${fmt.format(Date(a.triggerAt))}" else a.daysText()
            info.addView(Widgets.label(this, sub, 14, Tokens.Color.TEXT_MUTED))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            if (a.isAbsolute) {
                row.addView(Widgets.button(this, "Cancelar", Widgets.Kind.DANGER, "x") { AlarmScheduler.delete(this, a.id) },
                    LinearLayout.LayoutParams(-2, dp(Widgets.TOUCH)))
            } else {
                row.addView(Widgets.iconButton(this, "trash-2", "Borrar alarma", Widgets.Kind.GHOST) { confirmDelete(a) },
                    LinearLayout.LayoutParams(dp(Widgets.TOUCH), dp(Widgets.TOUCH)).apply { marginEnd = dp(4) })
                row.addView(Widgets.switch(this, null, a.enabled).apply {
                    setOnCheckedChangeListener { _, on -> AlarmScheduler.setEnabled(this@AlarmsActivity, a.id, on) }
                }, LinearLayout.LayoutParams(-2, dp(Widgets.TOUCH)))
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun confirmDelete(a: Alarm) {
        Dialogs.show(
            this, "¿Borrar la alarma de las ${a.timeText()}?", a.label,
            actions = listOf(
                Dialogs.Action("Cancelar", Widgets.Kind.GHOST, focus = true),
                Dialogs.Action("Borrar", Widgets.Kind.DANGER, "trash-2") { AlarmScheduler.delete(this, a.id) },
            ),
        )
    }

    private fun edit(existing: Alarm?) {
        val dp = { v: Int -> Ui.dp(this, v) }
        val now = Calendar.getInstance()
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val hour = Widgets.Stepper(this, 0, 23, existing?.hour ?: now.get(Calendar.HOUR_OF_DAY))
        val minute = Widgets.Stepper(this, 0, 59, existing?.minute ?: 0)
        val pickers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        pickers.addView(hour.view)
        pickers.addView(TextView(this).apply {
            text = ":"; textSize = 40f; setTextColor(Tokens.Color.TEXT_MUTED); typeface = Fonts.light(this@AlarmsActivity)
            setPadding(dp(8), 0, dp(8), 0)
        })
        pickers.addView(minute.view)
        form.addView(pickers)

        val days = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = Alarm.DAY_SHORT.mapIndexed { i, d ->
            Widgets.chip(this, d, (existing?.days ?: 0) and (1 shl i) != 0).also {
                days.addView(it, LinearLayout.LayoutParams(0, dp(Widgets.TOUCH), 1f).apply {
                    marginStart = if (i == 0) 0 else dp(4)
                })
            }
        }
        form.addView(days, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val label = Widgets.field(this, existing?.label ?: "Alarma", hint = "Etiqueta")
        form.addView(label, LinearLayout.LayoutParams(-1, dp(Widgets.TOUCH)).apply { topMargin = dp(12) })

        val actions = mutableListOf<Dialogs.Action>()
        if (existing != null) {
            actions += Dialogs.Action("Borrar", Widgets.Kind.DANGER, "trash-2", atStart = true) { AlarmScheduler.delete(this, existing.id) }
        }
        actions += Dialogs.Action("Cancelar", Widgets.Kind.GHOST)
        actions += Dialogs.Action("Guardar", Widgets.Kind.PRIMARY, "check", focus = true) {
            var mask = 0
            chips.forEachIndexed { i, c -> if (c.isSelected) mask = mask or (1 shl i) }
            val l = label.text.toString().ifBlank { "Alarma" }
            if (existing == null) {
                AlarmScheduler.create(this, hour.value, minute.value, mask, l)
            } else {
                AlarmScheduler.save(this, existing.copy(hour = hour.value, minute = minute.value, days = mask, label = l, enabled = true))
            }
        }
        Dialogs.show(this, if (existing == null) "Nueva alarma" else "Editar alarma", body = form, actions = actions)
    }
}
