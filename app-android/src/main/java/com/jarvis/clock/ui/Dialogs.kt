package com.jarvis.clock.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView

/** Diálogo Obsidiana propio: sustituye a AlertDialog (que no sigue los tokens). */
object Dialogs {
    class Action(
        val label: String,
        val kind: Widgets.Kind = Widgets.Kind.SECONDARY,
        val icon: String? = null,
        /** A la izquierda de la fila (acciones destructivas como Borrar). */
        val atStart: Boolean = false,
        /** Recibe el foco al abrir. */
        val focus: Boolean = false,
        val onClick: () -> Unit = {},
    )

    fun show(activity: Activity, title: String, message: String? = null, body: View? = null, actions: List<Action>): Dialog {
        val dp = { v: Int -> Ui.dp(activity, v) }
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Tokens.Color.SURFACE); cornerRadius = dp(Tokens.RADIUS_LG).toFloat()
                setStroke(dp(1), Tokens.Color.BORDER)
            }
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }
        content.addView(Widgets.label(activity, title, 20, Tokens.Color.TEXT, Fonts.semibold(activity)))
        if (message != null) {
            content.addView(Widgets.label(activity, message, 16, Tokens.Color.TEXT_MUTED).apply { setPadding(0, dp(8), 0, 0) })
        }
        if (body != null) {
            content.addView(ScrollView(activity).apply { addView(body) },
                LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        var focusView: View? = null
        var last: View? = null
        fun add(a: Dialogs.Action) {
            val b = Widgets.button(activity, a.label, a.kind, a.icon) { dialog.dismiss(); a.onClick() }
            if (a.focus) focusView = b
            last = b
            row.addView(b, LinearLayout.LayoutParams(-2, dp(Widgets.TOUCH)).apply { marginStart = dp(8) })
        }
        actions.filter { it.atStart }.forEach(::add)
        row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        actions.filter { !it.atStart }.forEach(::add)
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        dialog.setContentView(content)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setDimAmount(0.75f)
        }
        dialog.show()
        dialog.window?.let { w ->
            val width = minOf(activity.resources.displayMetrics.widthPixels - dp(48), dp(560))
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            Ui.immersive(w)
        }
        (focusView ?: last)?.requestFocus()
        return dialog
    }
}
