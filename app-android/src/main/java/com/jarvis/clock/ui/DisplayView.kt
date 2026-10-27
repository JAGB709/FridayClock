package com.jarvis.clock.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Vista de contenido del agente (`/display` con type text o markdown): una tarjeta con título y
 * texto con estilo. Solo pinta texto (spans), nunca HTML ni enlaces; tocar cierra.
 */
object DisplayView {

    fun build(ctx: Context, title: String, content: String, onClose: () -> Unit): View {
        val blocks = SafeMarkdown.parse(content)
        val pad = Ui.dp(ctx, Tokens.SPACE[4])
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        // Mensaje corto sin título ni estructura: se muestra grande y centrado, como siempre.
        val simple = title.isEmpty() && blocks.size == 1 && blocks[0].kind == SafeMarkdown.Kind.PARAGRAPH && blocks[0].plain.length <= 90
        if (simple) {
            return TextView(ctx).apply {
                text = styled(blocks[0].runs)
                setTextColor(Ui.TEXT)
                gravity = Gravity.CENTER
                setPadding(pad, pad, pad, pad)
                setAutoSizeTextTypeUniformWithConfiguration(16, 96, 2, TypedValue.COMPLEX_UNIT_SP)
                setOnClickListener { onClose() }
            }
        }
        if (title.isNotEmpty()) {
            column.addView(TextView(ctx).apply {
                text = title
                setTextColor(Ui.ACCENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Tokens.TYPE[4].toFloat())
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, 0, 0, Ui.dp(ctx, Tokens.SPACE[2]))
            })
        }
        for (b in blocks) column.addView(blockView(ctx, b))
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                setStroke(Ui.dp(ctx, 1), Ui.BORDER)
                cornerRadius = Ui.dp(ctx, Tokens.RADIUS_LG).toFloat()
            }
            addView(ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(column) },
                LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val margin = Ui.dp(ctx, Tokens.SPACE[4])
        return android.widget.FrameLayout(ctx).apply {
            setPadding(margin, margin, margin, margin)
            addView(card, android.widget.FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { onClose() }
        }
    }

    private fun blockView(ctx: Context, b: SafeMarkdown.Block): View {
        val gap = Ui.dp(ctx, Tokens.SPACE[1] * 2)
        return TextView(ctx).apply {
            val body = styled(b.runs)
            when (b.kind) {
                SafeMarkdown.Kind.HEADING -> {
                    text = body
                    setTextColor(Ui.ACCENT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, Tokens.TYPE[if (b.level == 1) 4 else 3].toFloat())
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(0, gap, 0, gap)
                }
                SafeMarkdown.Kind.BULLET -> {
                    text = SpannableStringBuilder("${b.marker}  ").append(body)
                    setTextColor(Ui.TEXT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, Tokens.TYPE[3].toFloat())
                    setPadding(0, gap / 2, 0, gap / 2)
                }
                SafeMarkdown.Kind.QUOTE -> {
                    text = body
                    setTextColor(Ui.DIM)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, Tokens.TYPE[3].toFloat())
                    setTypeface(typeface, Typeface.ITALIC)
                    setPadding(Ui.dp(ctx, Tokens.SPACE[3]), gap, 0, gap)
                }
                SafeMarkdown.Kind.PARAGRAPH -> {
                    text = body
                    setTextColor(Ui.TEXT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, Tokens.TYPE[3].toFloat())
                    setLineSpacing(0f, 1.2f)
                    setPadding(0, gap, 0, gap)
                }
            }
        }
    }

    private fun styled(runs: List<SafeMarkdown.Run>): CharSequence {
        val sb = SpannableStringBuilder()
        for (r in runs) {
            val start = sb.length
            sb.append(r.text)
            val end = sb.length
            if (end == start) continue
            val style = (if (r.bold) Typeface.BOLD else 0) or (if (r.italic) Typeface.ITALIC else 0)
            if (style != 0) sb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (r.code) {
                sb.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(BackgroundColorSpan(Ui.BORDER), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }
}
