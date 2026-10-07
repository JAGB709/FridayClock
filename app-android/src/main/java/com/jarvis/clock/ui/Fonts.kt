package com.jarvis.clock.ui

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/** Inter empaquetada en assets; si no se puede cargar, la fuente sans-serif del sistema. */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    private fun load(ctx: Context, file: String): Typeface = cache.getOrPut(file) {
        runCatching { Typeface.createFromAsset(ctx.applicationContext.assets, "face/fonts/$file") }
            .getOrDefault(Typeface.SANS_SERIF)
    }

    fun light(ctx: Context): Typeface = load(ctx, "Inter-Light.ttf")
    fun regular(ctx: Context): Typeface = load(ctx, "Inter-Regular.ttf")
    fun semibold(ctx: Context): Typeface = load(ctx, "Inter-SemiBold.ttf")

    /** Números de ancho fijo: la hora no baila al cambiar de cifra. */
    fun tabular(tv: TextView) { tv.fontFeatureSettings = "tnum" }
}
