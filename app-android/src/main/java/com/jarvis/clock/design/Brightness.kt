package com.jarvis.clock.design

import android.content.Context
import com.jarvis.clock.Prefs
import org.json.JSONObject

/**
 * Regla del brillo: por defecto manda el del perfil activo (el del panel); si se cambia en el reloj,
 * ese valor manda para ese perfil hasta que el reloj cambie de perfil o el panel cambie un brillo de perfil.
 */
object Brightness {
    data class Manual(val percent: Int, val profileId: String, val stamp: Int)

    /** Hash de "id:brillo" de todos los perfiles, en orden: cambia solo si cambia un brillo o el conjunto de perfiles. */
    fun stampOf(design: JSONObject): Int {
        val ps = design.optJSONArray("profiles") ?: return 0
        val sb = StringBuilder()
        for (i in 0 until ps.length()) {
            val p = ps.optJSONObject(i) ?: continue
            sb.append(p.optString("id")).append(':').append(p.optInt("brightness", 70)).append(';')
        }
        return sb.toString().hashCode()
    }

    fun valid(manual: Manual?, scheduledProfileId: String, savedDesign: JSONObject): Manual? {
        if (manual == null) return null
        if (manual.profileId != scheduledProfileId) return null
        if (manual.stamp != stampOf(savedDesign)) return null
        return manual
    }

    fun effective(profileBrightness: Int, manual: Manual?, profileId: String): Int {
        val v = if (manual != null && manual.profileId == profileId) manual.percent else profileBrightness
        return v.coerceIn(1, 100)
    }

    /** Guarda un valor manual para el perfil programado ahora, con el sello del diseño guardado. */
    fun setManual(ctx: Context, percent: Int, minuteOfDay: Int) {
        val design = DesignStore.load(ctx).design
        val id = DesignSchema.activeProfile(design, minuteOfDay)?.optString("id").orEmpty()
        Prefs.manualBrightness = Manual(percent.coerceIn(1, 100), id, stampOf(design))
    }

    fun clearManual() { Prefs.manualBrightness = null }
}
