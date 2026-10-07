package com.jarvis.clock.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import com.jarvis.clock.R

/** Iconos Lucide (VectorDrawable) por nombre; un nombre desconocido devuelve 0 / null. */
object Icons {
    private val MAP = mapOf(
        "bell" to R.drawable.ic_bell, "mic" to R.drawable.ic_mic, "settings" to R.drawable.ic_settings,
        "sun" to R.drawable.ic_sun, "cloud" to R.drawable.ic_cloud, "cloud-sun" to R.drawable.ic_cloud_sun,
        "cloud-rain" to R.drawable.ic_cloud_rain, "cloud-snow" to R.drawable.ic_cloud_snow,
        "cloud-lightning" to R.drawable.ic_cloud_lightning, "cloud-fog" to R.drawable.ic_cloud_fog,
        "moon" to R.drawable.ic_moon, "clock" to R.drawable.ic_clock, "alert-triangle" to R.drawable.ic_alert_triangle,
        "check" to R.drawable.ic_check, "x" to R.drawable.ic_x, "trash-2" to R.drawable.ic_trash_2,
        "plus" to R.drawable.ic_plus, "play" to R.drawable.ic_play, "pause" to R.drawable.ic_pause,
        "volume-2" to R.drawable.ic_volume_2, "image" to R.drawable.ic_image, "upload" to R.drawable.ic_upload,
        "chevron-right" to R.drawable.ic_chevron_right,
    )

    fun res(name: String): Int = MAP[name] ?: 0

    fun tinted(ctx: Context, name: String, color: Int): Drawable? {
        val id = res(name)
        if (id == 0) return null
        return ctx.getDrawable(id)?.mutate()?.also { it.setColorFilter(color, PorterDuff.Mode.SRC_IN) }
    }
}
