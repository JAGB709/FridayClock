package com.jarvis.clock.design

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Diseño de pantallas del reloj (JSON versión 1): temas, pantallas en rejilla 8×6, perfiles por
 * hora y pantalla de alarma. La validación es pura y de todo o nada: si algo falla se devuelve
 * cada ruta con su motivo y no se guarda nada.
 */
object DesignSchema {
    const val COLS = 8
    const val ROWS = 6
    const val ALARM_ROWS = 4
    const val MAX_BYTES = 64 * 1024

    private val SLUG = Regex("^[a-z0-9_-]{1,24}$")
    private val COLOR = Regex("^#[0-9a-fA-F]{6}$")
    private val HM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val IMAGE = Regex("^[A-Za-z0-9_-]{1,60}\\.(jpg|jpeg|png|webp)$")
    private val FONTS = setOf("inter", "thin", "normal", "bold", "mono", "digital")
    private val ROLES = setOf("fg", "muted", "accent")
    private val ALIGNS = setOf("left", "center", "right")
    private val FITS = setOf("cover", "contain", "fill")
    private val PHOTO_FIT_KEYS = listOf("fit", "zoom", "pos_x", "pos_y")
    private val COMMON = setOf("id", "type", "x", "y", "w", "h", "color", "align")
    private val ELEMENT_PROPS = mapOf(
        "clock" to setOf("style", "seconds", "hour12"),
        "date" to setOf("format"),
        "weather_now" to setOf("detail"),
        "forecast" to setOf("mode", "count"),
        "next_alarm" to setOf("show_label"),
        "text" to setOf("mode", "text", "quotes"),
        "photos" to setOf("images", "interval_s", "transition", "order", "fit", "zoom", "pos_x", "pos_y"),
    )

    sealed class Result {
        data class Ok(val design: JSONObject) : Result()
        data class Invalid(val errors: Map<String, String>) : Result()
    }

    private class Ctx {
        val errors = LinkedHashMap<String, String>()
        fun err(path: String, msg: String) { if (path !in errors) errors[path] = msg }
    }

    private class Rect(val id: String, val x: Int, val y: Int, val w: Int, val h: Int)

    // ------------------------------------------------------------------ validación

    fun validate(design: JSONObject): Result {
        if (design.toString().toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return Result.Invalid(mapOf("design" to "supera 64 KB"))
        }
        val c = Ctx()
        unknownKeys(c, design, setOf("version", "themes", "screens", "profiles", "alarm_screen"), "")
        if ((design.opt("version") as? Number)?.toInt() != 1) c.err("version", "debe ser 1")

        val themeIds = HashSet<String>()
        val themes = design.optJSONObject("themes")
        if (themes == null) c.err("themes", "falta") else {
            if (themes.length() !in 1..12) c.err("themes", "debe haber entre 1 y 12 temas")
            for (id in themes.keys()) {
                themeIds += id
                if (!SLUG.matches(id)) c.err("themes.$id", "identificador no válido")
                validateTheme(c, themes.optJSONObject(id), "themes.$id")
            }
        }

        val screenIds = HashSet<String>()
        val screens = design.optJSONObject("screens")
        if (screens == null) c.err("screens", "falta") else {
            if (screens.length() !in 1..8) c.err("screens", "debe haber entre 1 y 8 pantallas")
            for (id in screens.keys()) {
                screenIds += id
                val p = "screens.$id"
                if (!SLUG.matches(id)) c.err(p, "identificador no válido")
                val s = screens.optJSONObject(id)
                if (s == null) { c.err(p, "debe ser un objeto"); continue }
                unknownKeys(c, s, setOf("name", "elements"), p)
                text(c, s, "name", p, 1, 30, true)
                validateElements(c, s.optJSONArray("elements"), "$p.elements", ROWS, 2)
            }
        }

        val profiles = design.optJSONArray("profiles")
        if (profiles == null) c.err("profiles", "falta") else {
            if (profiles.length() !in 1..4) c.err("profiles", "debe haber entre 1 y 4 perfiles")
            val ids = HashSet<String>()
            val froms = HashSet<String>()
            for (i in 0 until profiles.length()) validateProfile(c, profiles.optJSONObject(i), i, ids, froms, themeIds, screenIds)
        }

        val alarm = design.optJSONObject("alarm_screen")
        if (alarm == null) c.err("alarm_screen", "falta") else {
            unknownKeys(c, alarm, setOf("theme", "elements", "snooze_label", "dismiss_label"), "alarm_screen")
            val th = alarm.opt("theme") as? String
            if (th == null || th !in themeIds) c.err("alarm_screen.theme", "el tema no existe")
            validateElements(c, alarm.optJSONArray("elements"), "alarm_screen.elements", ALARM_ROWS, 1)
            if (alarm.has("snooze_label")) text(c, alarm, "snooze_label", "alarm_screen", 1, 20, false)
            if (alarm.has("dismiss_label")) text(c, alarm, "dismiss_label", "alarm_screen", 1, 20, false)
        }
        return if (c.errors.isEmpty()) Result.Ok(design) else Result.Invalid(c.errors)
    }

    private fun validateTheme(c: Ctx, t: JSONObject?, path: String) {
        if (t == null) { c.err(path, "debe ser un objeto"); return }
        unknownKeys(c, t, setOf("name", "bg", "fg", "muted", "accent", "font"), path)
        text(c, t, "name", path, 1, 30, true)
        for (k in listOf("fg", "muted", "accent")) color(c, t, k, path, true)
        oneOf(c, t, "font", path, FONTS, required = true)
        val bg = t.optJSONObject("bg")
        if (bg == null) { c.err("$path.bg", "falta"); return }
        val p = "$path.bg"
        unknownKeys(c, bg, setOf("type", "color", "from", "to", "angle", "image", "dim", "fit", "zoom", "pos_x", "pos_y"), p)
        when (bg.opt("type")) {
            "color" -> color(c, bg, "color", p, true)
            "gradient" -> {
                color(c, bg, "from", p, true)
                color(c, bg, "to", p, true)
                if (bg.has("angle")) int(c, bg, "angle", p, 0, 359)
            }
            "image" -> {
                val img = bg.opt("image")
                if (img !is String || !IMAGE.matches(img)) c.err("$p.image", "nombre de imagen no válido (jpg, png o webp)")
                if (bg.has("dim")) int(c, bg, "dim", p, 0, 80)
                if (bg.has("color")) color(c, bg, "color", p, false)
                photoFit(c, bg, p)
            }
            else -> c.err("$p.type", "debe ser color, gradient o image")
        }
        if (bg.opt("type") != "image") for (k in PHOTO_FIT_KEYS) if (bg.has(k)) c.err("$p.$k", "solo se usa con fondo de imagen")
    }

    private fun photoFit(c: Ctx, o: JSONObject, path: String) {
        oneOf(c, o, "fit", path, FITS)
        if (o.has("zoom")) {
            val z = (o.opt("zoom") as? Number)?.toDouble()
            if (z == null || z < 1.0 || z > 3.0) c.err("$path.zoom", "debe ser un número entre 1 y 3")
        }
        if (o.has("pos_x")) int(c, o, "pos_x", path, 0, 100)
        if (o.has("pos_y")) int(c, o, "pos_y", path, 0, 100)
    }

    private fun validateElements(c: Ctx, arr: JSONArray?, path: String, rows: Int, maxPhotos: Int) {
        if (arr == null) { c.err(path, "falta"); return }
        if (arr.length() > 12) c.err(path, "máximo 12 elementos")
        val ids = HashSet<String>()
        val rects = ArrayList<Rect>()
        var photoCount = 0
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i)
            if (e == null) { c.err("$path[$i]", "debe ser un objeto"); continue }
            val id = e.opt("id") as? String
            val validId = id != null && SLUG.matches(id)
            val p = if (validId) "$path.$id" else "$path[$i]"
            if (!validId) c.err("$p.id", "identificador no válido")
            else if (!ids.add(id!!)) c.err(p, "identificador repetido")
            val type = e.opt("type") as? String
            val props = ELEMENT_PROPS[type]
            if (type == null || props == null) { c.err("$p.type", "tipo desconocido"); continue }
            if (type == "photos") photoCount++
            unknownKeys(c, e, COMMON + props, p)
            val x = int(c, e, "x", p, 0, COLS - 1)
            val y = int(c, e, "y", p, 0, rows - 1)
            val w = int(c, e, "w", p, 1, COLS)
            val h = int(c, e, "h", p, 1, rows)
            if (x != null && w != null && x + w > COLS) c.err("$p.w", "se sale de la rejilla (x + w ≤ $COLS)")
            else if (y != null && h != null && y + h > rows) c.err("$p.h", "se sale de la rejilla (y + h ≤ $rows)")
            else if (x != null && y != null && w != null && h != null && validId) rects += Rect(id!!, x, y, w, h)
            oneOf(c, e, "color", p, ROLES)
            oneOf(c, e, "align", p, ALIGNS)
            validateProps(c, e, type, p)
        }
        if (photoCount > maxPhotos) c.err(path, "máximo $maxPhotos elementos de fotos")
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            val a = rects[i]
            val b = rects[j]
            if (a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h) {
                c.err("$path.${b.id}", "se solapa con ${a.id}")
            }
        }
    }

    private fun validateProps(c: Ctx, e: JSONObject, type: String, p: String) {
        when (type) {
            "clock" -> { oneOf(c, e, "style", p, setOf("digital", "analog")); bool(c, e, "seconds", p); bool(c, e, "hour12", p) }
            "date" -> oneOf(c, e, "format", p, setOf("long", "short", "numeric"))
            "weather_now" -> bool(c, e, "detail", p)
            "forecast" -> { oneOf(c, e, "mode", p, setOf("hours", "days")); if (e.has("count")) int(c, e, "count", p, 2, 5) }
            "next_alarm" -> bool(c, e, "show_label", p)
            "photos" -> {
                val imgs = e.optJSONArray("images")
                if (imgs == null || imgs.length() !in 1..20) c.err("$p.images", "debe ser una lista de 1 a 20 fotos")
                else for (k in 0 until imgs.length()) {
                    val s = imgs.opt(k)
                    if (s !is String || !IMAGE.matches(s)) c.err("$p.images[$k]", "nombre de imagen no válido (jpg, png o webp)")
                }
                if (e.has("interval_s")) int(c, e, "interval_s", p, 3, 600)
                oneOf(c, e, "transition", p, setOf("fade", "none"))
                oneOf(c, e, "order", p, setOf("sequential", "random"))
                photoFit(c, e, p)
            }
            "text" -> {
                oneOf(c, e, "mode", p, setOf("fixed", "daily", "alarm_label"))
                if (e.has("text")) text(c, e, "text", p, 0, 120, false)
                if (e.has("quotes")) {
                    val q = e.optJSONArray("quotes")
                    if (q == null || q.length() > 20) c.err("$p.quotes", "debe ser una lista de hasta 20 frases")
                    else for (k in 0 until q.length()) {
                        val s = q.opt(k)
                        if (s !is String || s.length !in 1..120) c.err("$p.quotes[$k]", "debe ser un texto de 1 a 120 caracteres")
                    }
                }
            }
        }
    }

    private fun validateProfile(
        c: Ctx, pr: JSONObject?, i: Int, ids: MutableSet<String>, froms: MutableSet<String>,
        themeIds: Set<String>, screenIds: Set<String>,
    ) {
        val base = "profiles[$i]"
        if (pr == null) { c.err(base, "debe ser un objeto"); return }
        unknownKeys(c, pr, setOf("id", "name", "from", "theme", "screens", "rotate_s", "brightness", "night", "show_bar"), base)
        val id = pr.opt("id") as? String
        val validId = id != null && SLUG.matches(id)
        val p = if (validId) "profiles.$id" else base
        if (!validId) c.err("$p.id", "identificador no válido")
        else if (!ids.add(id!!)) c.err(p, "identificador repetido")
        text(c, pr, "name", p, 1, 30, true)
        val from = pr.opt("from") as? String
        if (from == null || !HM.matches(from)) c.err("$p.from", "debe ser HH:MM")
        else if (!froms.add(from)) c.err("$p.from", "hora repetida en otro perfil")
        val theme = pr.opt("theme") as? String
        if (theme == null || theme !in themeIds) c.err("$p.theme", "el tema no existe")
        val sc = pr.optJSONArray("screens")
        if (sc == null || sc.length() !in 1..6) c.err("$p.screens", "debe tener entre 1 y 6 pantallas")
        else for (k in 0 until sc.length()) {
            val s = sc.opt(k)
            if (s !is String || s !in screenIds) c.err("$p.screens[$k]", "la pantalla no existe")
        }
        if (pr.has("rotate_s")) {
            val r = (pr.opt("rotate_s") as? Number)?.toDouble()
            if (r == null || r % 1.0 != 0.0 || !(r == 0.0 || (r >= 5 && r <= 600))) c.err("$p.rotate_s", "debe ser 0 o un entero entre 5 y 600")
        }
        if (pr.has("brightness")) int(c, pr, "brightness", p, 1, 100)
        bool(c, pr, "night", p)
        bool(c, pr, "show_bar", p)
    }

    // ------------------------------------------------------------------ ayudas de validación

    private fun unknownKeys(c: Ctx, o: JSONObject, allowed: Set<String>, path: String) {
        for (k in o.keys()) if (k !in allowed) c.err(if (path.isEmpty()) k else "$path.$k", "campo desconocido")
    }

    private fun color(c: Ctx, o: JSONObject, key: String, path: String, required: Boolean) {
        if (!o.has(key)) { if (required) c.err("$path.$key", "falta"); return }
        val v = o.opt(key)
        if (v !is String || !COLOR.matches(v)) c.err("$path.$key", "debe ser un color #rrggbb")
    }

    private fun int(c: Ctx, o: JSONObject, key: String, path: String, min: Int, max: Int): Int? {
        val d = (o.opt(key) as? Number)?.toDouble()
        if (d == null || d % 1.0 != 0.0 || d < min || d > max) {
            c.err("$path.$key", "debe ser un entero entre $min y $max")
            return null
        }
        return d.toInt()
    }

    private fun bool(c: Ctx, o: JSONObject, key: String, path: String) {
        if (o.has(key) && o.opt(key) !is Boolean) c.err("$path.$key", "debe ser true o false")
    }

    private fun oneOf(c: Ctx, o: JSONObject, key: String, path: String, allowed: Set<String>, required: Boolean = false) {
        if (!o.has(key)) { if (required) c.err("$path.$key", "falta"); return }
        val v = o.opt(key)
        if (v !is String || v !in allowed) c.err("$path.$key", "debe ser ${allowed.joinToString(", ")}")
    }

    private fun text(c: Ctx, o: JSONObject, key: String, path: String, min: Int, max: Int, required: Boolean) {
        if (!o.has(key)) { if (required) c.err("$path.$key", "falta"); return }
        val v = o.opt(key)
        if (v !is String || v.trim().length < min || v.length > max) c.err("$path.$key", "debe ser un texto de $min a $max caracteres")
    }

    // ------------------------------------------------------------------ perfil activo

    fun minutesOf(hm: String): Int? {
        if (!HM.matches(hm)) return null
        return hm.substring(0, 2).toInt() * 60 + hm.substring(3, 5).toInt()
    }

    /** El perfil de mayor `from` ≤ la hora; si no hay ninguno, el de mayor `from` (de la víspera). */
    fun activeProfile(design: JSONObject, minuteOfDay: Int): JSONObject? {
        val arr = design.optJSONArray("profiles") ?: return null
        var best: JSONObject? = null
        var bestMin = -1
        var latest: JSONObject? = null
        var latestMin = -1
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val m = minutesOf(p.optString("from")) ?: continue
            if (m > latestMin) { latest = p; latestMin = m }
            if (m <= minuteOfDay && m > bestMin) { best = p; bestMin = m }
        }
        return best ?: latest
    }

    // ------------------------------------------------------------------ diseño inicial

    /** Diseño que se ve igual que la carátula de siempre; lo usa quien no ha guardado ninguno. */
    fun initial(dayBrightness: Int, nightBrightness: Int, nightStart: Int, nightEnd: Int): JSONObject {
        val day = dayBrightness.coerceIn(1, 100)
        val night = nightBrightness.coerceIn(1, 100)
        val start = nightStart.coerceIn(0, 23)
        val end = nightEnd.coerceIn(0, 23)

        val themes = JSONObject()
            .put("clasico", theme("Clásico", solid("#000000"), "#ffffff", "#90a4ae", "#4fc3f7", "thin"))
            .put("noche_roja", theme("Noche roja", solid("#000000"), "#8b0000", "#8b0000", "#8b0000", "thin"))
            .put("amanecer", theme("Amanecer", gradient("#1a2a4a", "#ff8a65", 160), "#ffffff", "#cfd8dc", "#ffd180", "thin"))
            .put("minimal", theme("Minimal", solid("#f5f5f5"), "#111111", "#666666", "#0277bd", "normal"))
            .put("digital_verde", theme("Digital verde", solid("#000000"), "#39ff14", "#1f8f0a", "#39ff14", "digital"))

        val principal = JSONArray()
            .put(el("hora", "clock", 0, 0, 8, 3, "style" to "digital"))
            .put(el("fecha", "date", 0, 3, 8, 1, "format" to "long", "color" to "muted"))
            .put(el("tiempo", "weather_now", 0, 4, 8, 1, "detail" to true, "color" to "muted"))
            .put(el("alarma", "next_alarm", 0, 5, 8, 1, "color" to "accent"))
        val noche = JSONArray()
            .put(el("hora", "clock", 0, 0, 8, 4, "style" to "digital"))
            .put(el("fecha", "date", 0, 4, 8, 1, "format" to "long"))
            .put(el("alarma", "next_alarm", 0, 5, 8, 1))
        val screens = JSONObject()
            .put("principal", JSONObject().put("name", "Principal").put("elements", principal))
            .put("noche", JSONObject().put("name", "Noche").put("elements", noche))

        val profiles = JSONArray()
        profiles.put(profile("dia", "Día", if (start == end) "00:00" else hm(end), "clasico", "principal", day, false))
        if (start != end) profiles.put(profile("noche", "Noche", hm(start), "noche_roja", "noche", night, true))

        val alarmElements = JSONArray()
            .put(el("hora", "clock", 0, 0, 8, 3, "style" to "digital"))
            .put(el("nombre", "text", 0, 3, 8, 1, "mode" to "alarm_label", "color" to "accent"))
        val alarm = JSONObject().put("theme", "clasico").put("elements", alarmElements)
            .put("snooze_label", "Posponer {min} min").put("dismiss_label", "Descartar")

        return JSONObject().put("version", 1).put("themes", themes).put("screens", screens)
            .put("profiles", profiles).put("alarm_screen", alarm)
    }

    private fun hm(hour: Int) = String.format(Locale.US, "%02d:00", hour)

    private fun solid(c: String) = JSONObject().put("type", "color").put("color", c)

    private fun gradient(from: String, to: String, angle: Int) =
        JSONObject().put("type", "gradient").put("from", from).put("to", to).put("angle", angle)

    private fun theme(name: String, bg: JSONObject, fg: String, muted: String, accent: String, font: String) =
        JSONObject().put("name", name).put("bg", bg).put("fg", fg).put("muted", muted).put("accent", accent).put("font", font)

    private fun el(id: String, type: String, x: Int, y: Int, w: Int, h: Int, vararg extra: Pair<String, Any>): JSONObject {
        val o = JSONObject().put("id", id).put("type", type).put("x", x).put("y", y).put("w", w).put("h", h)
        for ((k, v) in extra) o.put(k, v)
        return o
    }

    private fun profile(id: String, name: String, from: String, theme: String, screen: String, brightness: Int, night: Boolean) =
        JSONObject().put("id", id).put("name", name).put("from", from).put("theme", theme)
            .put("screens", JSONArray().put(screen)).put("rotate_s", 0).put("brightness", brightness)
            .put("night", night).put("show_bar", true)
}
