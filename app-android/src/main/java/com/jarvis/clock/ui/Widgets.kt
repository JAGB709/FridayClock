package com.jarvis.clock.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.jarvis.clock.design.darken

/** Lógica pura del selector de cifra (se prueba en JVM). */
object Steps {
    /** Suma [delta] a [v] dando la vuelta entre [min] y [max] (ambos incluidos). */
    fun wrap(v: Int, delta: Int, min: Int, max: Int): Int {
        val span = max - min + 1
        return ((v - min + delta) % span + span) % span + min
    }

    fun two(v: Int): String = "%02d".format(v)
}

/**
 * Controles Obsidiana construidos en código (sin AppCompat ni XML): colores de [Tokens], Inter
 * y un anillo de foco propio. Todos desactivan `defaultFocusHighlightEnabled`, que pintaba un
 * gris del sistema encima del anillo.
 */
object Widgets {
    enum class Kind { PRIMARY, SECONDARY, GHOST, DANGER }

    /** Alto mínimo de un objetivo táctil, en dp. */
    const val TOUCH = 48

    private const val RING_DP = 3

    fun dp(ctx: Context, v: Int) = Ui.dp(ctx, v)

    private fun shape(ctx: Context, fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(ctx, radiusDp).toFloat()
        if (strokeDp > 0) setStroke(dp(ctx, strokeDp), stroke)
    }

    /** Fondo con tres estados: pulsado, con foco (anillo) y normal. */
    fun stateBg(ctx: Context, fill: Int, border: Int, ring: Int, pressed: Int, radiusDp: Int = Tokens.RADIUS_MD) =
        StateListDrawable().apply {
            val b = if (border == Color.TRANSPARENT) 0 else 1
            addState(intArrayOf(android.R.attr.state_pressed), shape(ctx, pressed, border, b, radiusDp))
            addState(intArrayOf(android.R.attr.state_focused), shape(ctx, fill, ring, RING_DP, radiusDp))
            addState(intArrayOf(), shape(ctx, fill, border, b, radiusDp))
        }

    // fondo, texto, borde, anillo, pulsado
    private fun palette(kind: Kind): IntArray = when (kind) {
        Kind.PRIMARY -> intArrayOf(Tokens.Color.ACCENT, Tokens.Color.ON_ACCENT, Color.TRANSPARENT, Tokens.Color.TEXT, darken(Tokens.Color.ACCENT, 0.75f))
        Kind.SECONDARY -> intArrayOf(Tokens.Color.SURFACE_HIGH, Tokens.Color.TEXT, Tokens.Color.BORDER, Tokens.Color.ACCENT, Tokens.Color.BORDER)
        Kind.GHOST -> intArrayOf(Color.TRANSPARENT, Tokens.Color.TEXT, Color.TRANSPARENT, Tokens.Color.ACCENT, Tokens.Color.SURFACE_HIGH)
        Kind.DANGER -> intArrayOf(Color.TRANSPARENT, Tokens.Color.ERROR, Tokens.Color.BORDER, Tokens.Color.ERROR, Tokens.Color.SURFACE_HIGH)
    }

    private fun icon20(ctx: Context, name: String?, color: Int) =
        name?.let { Icons.tinted(ctx, it, color) }?.also { it.setBounds(0, 0, dp(ctx, 20), dp(ctx, 20)) }

    /** Botón de texto con icono Lucide opcional a la izquierda. */
    fun button(ctx: Context, label: String, kind: Kind = Kind.SECONDARY, icon: String? = null,
               textSp: Int = 16, onClick: () -> Unit): Button = Button(ctx).apply {
        val p = palette(kind)
        text = label
        isAllCaps = false
        textSize = textSp.toFloat()
        typeface = Fonts.semibold(ctx)
        setTextColor(p[1])
        background = stateBg(ctx, p[0], p[2], p[3], p[4])
        stateListAnimator = null
        defaultFocusHighlightEnabled = false
        minHeight = dp(ctx, TOUCH); minimumHeight = dp(ctx, TOUCH)
        minWidth = dp(ctx, TOUCH); minimumWidth = dp(ctx, TOUCH)
        setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0)
        gravity = Gravity.CENTER
        icon20(ctx, icon, p[1])?.let {
            setCompoundDrawablesRelative(it, null, null, null)
            compoundDrawablePadding = dp(ctx, 8)
        }
        setOnClickListener { onClick() }
    }

    /** Botón cuadrado solo con icono (atrás, borrar, chevrones). */
    fun iconButton(ctx: Context, icon: String, description: String, kind: Kind = Kind.GHOST,
                   sizeDp: Int = TOUCH, onClick: () -> Unit): ImageButton = ImageButton(ctx).apply {
        val p = palette(kind)
        setImageDrawable(Icons.tinted(ctx, icon, p[1]))
        contentDescription = description
        background = stateBg(ctx, p[0], p[2], p[3], p[4])
        defaultFocusHighlightEnabled = false
        scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = dp(ctx, 12)
        setPadding(pad, pad, pad, pad)
        minimumWidth = dp(ctx, sizeDp); minimumHeight = dp(ctx, sizeDp)
        setOnClickListener { onClick() }
    }

    fun label(ctx: Context, text: String, sizeSp: Int, color: Int, tf: Typeface = Fonts.regular(ctx)) =
        TextView(ctx).apply { this.text = text; textSize = sizeSp.toFloat(); setTextColor(color); typeface = tf }

    /** Tarjeta Obsidiana: superficie con borde fino. */
    fun card(ctx: Context, padDp: Int = 16, radiusDp: Int = Tokens.RADIUS_MD) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(ctx, Tokens.Color.SURFACE, Tokens.Color.BORDER, 1, radiusDp)
        setPadding(dp(ctx, padDp), dp(ctx, padDp), dp(ctx, padDp), dp(ctx, padDp))
    }

    /** Fila tocable y enfocable (tarjeta de alarma). */
    fun focusableCard(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true; isClickable = true
        defaultFocusHighlightEnabled = false
        background = stateBg(ctx, Tokens.Color.SURFACE, Tokens.Color.BORDER, Tokens.Color.ACCENT, Tokens.Color.SURFACE_HIGH)
        setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
        minimumHeight = dp(ctx, 72)
    }

    /** Chip alternable (días de la semana). */
    fun chip(ctx: Context, text: String, checked: Boolean, onChange: (Boolean) -> Unit = {}): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 14f
        typeface = Fonts.semibold(ctx)
        gravity = Gravity.CENTER
        isFocusable = true; isClickable = true
        defaultFocusHighlightEnabled = false
        minHeight = dp(ctx, TOUCH); minimumHeight = dp(ctx, TOUCH)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused, android.R.attr.state_selected), shape(ctx, Tokens.Color.ACCENT, Tokens.Color.TEXT, RING_DP, Tokens.RADIUS_SM))
            addState(intArrayOf(android.R.attr.state_focused), shape(ctx, Tokens.Color.SURFACE_HIGH, Tokens.Color.ACCENT, RING_DP, Tokens.RADIUS_SM))
            addState(intArrayOf(android.R.attr.state_selected), shape(ctx, Tokens.Color.ACCENT, Color.TRANSPARENT, 0, Tokens.RADIUS_SM))
            addState(intArrayOf(), shape(ctx, Tokens.Color.SURFACE_HIGH, Tokens.Color.BORDER, 1, Tokens.RADIUS_SM))
        }
        fun paint() { setTextColor(if (isSelected) Tokens.Color.ON_ACCENT else Tokens.Color.TEXT_MUTED) }
        isSelected = checked
        paint()
        setOnClickListener { isSelected = !isSelected; paint(); onChange(isSelected) }
    }

    /** Interruptor con los colores del acento y anillo de foco. */
    fun switch(ctx: Context, label: String? = null, checked: Boolean = false): Switch = Switch(ctx).apply {
        if (label != null) {
            text = label; textSize = 16f; setTextColor(Tokens.Color.TEXT); typeface = Fonts.regular(ctx)
        }
        isChecked = checked
        defaultFocusHighlightEnabled = false
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(ctx, TOUCH); minimumHeight = dp(ctx, TOUCH)
        val on = intArrayOf(android.R.attr.state_checked)
        val none = intArrayOf()
        thumbTintList = ColorStateList(arrayOf(on, none), intArrayOf(Tokens.Color.ACCENT, Tokens.Color.TEXT_MUTED))
        trackTintList = ColorStateList(arrayOf(on, none), intArrayOf(darken(Tokens.Color.ACCENT, 0.45f), Tokens.Color.BORDER))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(ctx, Color.TRANSPARENT, Tokens.Color.ACCENT, RING_DP, Tokens.RADIUS_SM))
            addState(intArrayOf(), shape(ctx, Color.TRANSPARENT, Color.TRANSPARENT, 0, Tokens.RADIUS_SM))
        }
        setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
    }

    /** Campo de texto: superficie alta, borde fino y borde de acento con foco. */
    fun field(ctx: Context, value: String, type: Int = android.text.InputType.TYPE_CLASS_TEXT, hint: String = ""): EditText =
        EditText(ctx).apply {
            setText(value)
            this.hint = hint
            inputType = type
            setSingleLine()
            textSize = 16f
            typeface = Fonts.regular(ctx)
            setTextColor(Tokens.Color.TEXT)
            setHintTextColor(Tokens.Color.TEXT_MUTED)
            defaultFocusHighlightEnabled = false
            minHeight = dp(ctx, TOUCH); minimumHeight = dp(ctx, TOUCH)
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), shape(ctx, Tokens.Color.SURFACE_HIGH, Tokens.Color.ACCENT, 2, Tokens.RADIUS_SM))
                addState(intArrayOf(), shape(ctx, Tokens.Color.SURFACE_HIGH, Tokens.Color.BORDER, 1, Tokens.RADIUS_SM))
            }
        }

    fun seekBar(ctx: Context, max: Int, progress: Int): SeekBar = SeekBar(ctx).apply {
        this.max = max
        this.progress = progress
        defaultFocusHighlightEnabled = false
        progressTintList = ColorStateList.valueOf(Tokens.Color.ACCENT)
        thumbTintList = ColorStateList.valueOf(Tokens.Color.ACCENT)
        progressBackgroundTintList = ColorStateList.valueOf(Tokens.Color.BORDER)
        minimumHeight = dp(ctx, TOUCH)
        setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(ctx, Color.TRANSPARENT, Tokens.Color.ACCENT, 2, Tokens.RADIUS_SM))
            addState(intArrayOf(), shape(ctx, Color.TRANSPARENT, Color.TRANSPARENT, 0, Tokens.RADIUS_SM))
        }
    }

    /**
     * Selector de cifra: chevrón izquierdo, número grande y chevrón derecho. Un toque cambia en 1;
     * mantener pulsado repite. Da la vuelta entre [min] y [max].
     */
    class Stepper(ctx: Context, private val min: Int, private val max: Int, start: Int) {
        var value: Int = start.coerceIn(min, max)
            private set
        private val num = TextView(ctx).apply {
            textSize = 40f; setTextColor(Tokens.Color.TEXT); typeface = Fonts.light(ctx)
            Fonts.tabular(this); gravity = Gravity.CENTER
            text = Steps.two(value)
        }
        val view = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(arrow(ctx, "chevron-left", "Menos", -1), LinearLayout.LayoutParams(dp(ctx, TOUCH), dp(ctx, TOUCH)))
            addView(num, LinearLayout.LayoutParams(dp(ctx, 72), -2))
            addView(arrow(ctx, "chevron-right", "Más", +1), LinearLayout.LayoutParams(dp(ctx, TOUCH), dp(ctx, TOUCH)))
        }

        private fun step(d: Int) { value = Steps.wrap(value, d, min, max); num.text = Steps.two(value) }

        private fun arrow(ctx: Context, icon: String, desc: String, d: Int): ImageButton {
            val h = Handler(Looper.getMainLooper())
            val repeat = object : Runnable { override fun run() { step(d); h.postDelayed(this, 110) } }
            val stop = { h.removeCallbacks(repeat) }
            return iconButton(ctx, icon, desc, Kind.SECONDARY) { step(d) }.apply {
                setOnLongClickListener { h.post(repeat); true }
                setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) stop(); false }
                setOnKeyListener { _, _, e -> if (e.action == KeyEvent.ACTION_UP) stop(); false }
                onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (!f) stop() }
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) { stop() }
                })
            }
        }
    }
}
