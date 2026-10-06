package com.jarvis.clock.design

import android.content.Context
import com.jarvis.clock.Prefs
import org.json.JSONObject

/** Guarda el diseño de pantallas. Si no hay ninguno, o el guardado no es válido, se usa el inicial. */
object DesignStore {
    private const val FILE = "design"
    private const val KEY = "json"

    data class Loaded(val design: JSONObject, val saved: Boolean)

    /** Puro: interpreta lo guardado; cualquier fallo devuelve el diseño de respaldo con saved=false. */
    fun decode(raw: String?, fallback: () -> JSONObject): Loaded {
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (parsed != null && DesignSchema.validate(parsed) is DesignSchema.Result.Ok) return Loaded(parsed, true)
        }
        return Loaded(fallback(), false)
    }

    fun initial(): JSONObject =
        DesignSchema.initial(Prefs.dayBrightness, Prefs.nightBrightness, Prefs.nightStartHour, Prefs.nightEndHour)

    fun load(ctx: Context): Loaded = decode(sp(ctx).getString(KEY, null)) { initial() }

    fun save(ctx: Context, design: JSONObject) {
        sp(ctx).edit().putString(KEY, design.toString()).commit()
    }

    fun reset(ctx: Context) {
        sp(ctx).edit().remove(KEY).commit()
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
