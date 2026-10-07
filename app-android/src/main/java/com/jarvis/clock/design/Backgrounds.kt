package com.jarvis.clock.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Imágenes de fondo subidas desde el panel (en filesDir/backgrounds, así se ven sin red). */
object Backgrounds {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_FILES = 60
    const val TARGET_SIDE = 1600
    const val MAX_PIXELS = 100_000_000L
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

    /** Potencia de 2 para `inSampleSize`: la mayor que deja el lado largo en `target` o más. */
    fun sampleSize(w: Int, h: Int, target: Int = TARGET_SIDE): Int {
        var s = 1
        val side = maxOf(w, h)
        while (side / (s * 2) >= target) s *= 2
        return s
    }

    /** Tamaño final: lado largo a `target` como máximo, misma proporción, nunca amplía. */
    fun targetSize(w: Int, h: Int, target: Int = TARGET_SIDE): Pair<Int, Int> {
        val side = maxOf(w, h)
        if (side <= target) return w to h
        val k = target.toDouble() / side
        return maxOf(1, Math.round(w * k).toInt()) to maxOf(1, Math.round(h * k).toInt())
    }

    /** Guarda una imagen subida (reducida a [TARGET_SIDE] px) de forma atómica. Devuelve su nombre. */
    fun save(ctx: Context, rawName: String, bytes: ByteArray): String {
        val name = sanitizeName(rawName) ?: throw IllegalArgumentException("nombre no válido: usa jpg, png o webp")
        require(bytes.isNotEmpty()) { "archivo vacío" }
        require(bytes.size <= MAX_BYTES) { "la imagen supera 8 MB" }
        val dir = dir(ctx)
        val existing = File(dir, name).isFile
        val count = dir.listFiles()?.count { !it.name.startsWith(".") } ?: 0
        require(existing || count < MAX_FILES) { "la biblioteca está llena ($MAX_FILES fotos): borra alguna" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        require(w > 0 && h > 0) { "el reloj no puede leer esa imagen" }
        require(w.toLong() * h <= MAX_PIXELS) { "la imagen es demasiado grande" }
        val tmp = File(dir, ".$name.tmp")
        try {
            try {
            if (maxOf(w, h) <= TARGET_SIDE) {
                tmp.writeBytes(bytes)
            } else {
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(w, h)
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) inPreferredConfig = Bitmap.Config.RGB_565
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: throw IllegalArgumentException("el reloj no puede leer esa imagen")
                val (tw, th) = targetSize(w, h)
                val scaled = if (decoded.width == tw && decoded.height == th) decoded
                    else Bitmap.createScaledBitmap(decoded, tw, th, true)
                if (scaled !== decoded) decoded.recycle()
                val (format, quality) = when (name.substringAfterLast('.')) {
                    "png" -> Bitmap.CompressFormat.PNG to 100
                    "webp" -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP to 90
                    else -> Bitmap.CompressFormat.JPEG to 85
                }
                val ok = try {
                    tmp.outputStream().use { scaled.compress(format, quality, it) }
                } finally {
                    scaled.recycle()
                }
                if (!ok) throw IllegalArgumentException("no se pudo reducir la imagen")
            }
            } catch (e: OutOfMemoryError) {
                throw IllegalArgumentException("la imagen es demasiado grande para el reloj")
            }
            if (!tmp.renameTo(File(dir, name))) throw IllegalArgumentException("no se pudo guardar la imagen")
            return name
        } finally {
            tmp.delete()
        }
    }

    fun delete(ctx: Context, name: String): Boolean = fileFor(dir(ctx), name)?.delete() ?: false
}
