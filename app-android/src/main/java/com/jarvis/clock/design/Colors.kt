package com.jarvis.clock.design

/** "#rrggbb" -> color ARGB opaco. No usa android.graphics.Color para poder probarlo en la JVM. */
fun parseHex(s: String): Int = (0xFF000000L or s.removePrefix("#").toLong(16)).toInt()

/** Oscurece un color multiplicando cada canal por [factor] (0..1). */
fun darken(color: Int, factor: Float): Int {
    val r = ((color shr 16) and 0xFF) * factor
    val g = ((color shr 8) and 0xFF) * factor
    val b = (color and 0xFF) * factor
    return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
}

/** Negro o blanco, el que se lea mejor sobre [bg]. */
fun readableOn(bg: Int): Int {
    val r = (bg shr 16) and 0xFF
    val g = (bg shr 8) and 0xFF
    val b = bg and 0xFF
    return if ((r * 299 + g * 587 + b * 114) / 1000 > 140) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}
