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
