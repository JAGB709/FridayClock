package com.jarvis.clock.design

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Imágenes de fondo subidas desde el panel (en filesDir/backgrounds, así se ven sin red). */
object Backgrounds {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_SIDE = 2048
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** Nombre seguro para disco (solo [A-Za-z0-9_-] + extensión admitida) o null. */
    fun sanitizeName(raw: String): String? {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val ext = base.substringAfterLast('.', "").lowercase()
        if (ext !in EXTENSIONS) return null
        val stem = base.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(60)
        return if (stem.isEmpty()) null else "$stem.$ext"
    }

    /** Archivo del fondo si existe en [dir]; null en cualquier otro caso (el tema cae a su color). */
    fun fileFor(dir: File, name: String?): File? {
        if (name == null) return null
        val safe = sanitizeName(name) ?: return null
        return File(dir, safe).takeIf { it.isFile }
    }

    fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "backgrounds").apply { mkdirs() }

    fun list(ctx: Context): JSONArray {
        val out = JSONArray()
        dir(ctx).listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name }?.forEach {
            out.put(JSONObject().put("name", it.name).put("size", it.length()))
        }
        return out
    }

    /** Guarda una imagen subida de forma atómica y comprueba que se puede leer. Devuelve su nombre. */
    fun save(ctx: Context, rawName: String, bytes: ByteArray): String {
        val name = sanitizeName(rawName) ?: throw IllegalArgumentException("nombre no válido: usa jpg, png o webp")
        require(bytes.isNotEmpty()) { "archivo vacío" }
        require(bytes.size <= MAX_BYTES) { "la imagen supera 2 MB" }
        val dir = dir(ctx)
        val tmp = File(dir, ".$name.tmp")
        try {
            tmp.writeBytes(bytes)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, o)
            require(o.outWidth > 0 && o.outHeight > 0) { "el reloj no puede leer esa imagen" }
            require(maxOf(o.outWidth, o.outHeight) <= MAX_SIDE) { "la imagen es demasiado grande (máx. $MAX_SIDE px)" }
            if (!tmp.renameTo(File(dir, name))) throw IllegalArgumentException("no se pudo guardar la imagen")
            return name
        } finally {
            tmp.delete()
        }
    }

    fun delete(ctx: Context, name: String): Boolean = fileFor(dir(ctx), name)?.delete() ?: false
}
