package com.jarvis.clock.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sonidos de alarma: tonos del sistema y archivos subidos (en filesDir/sounds, así
 * suenan sin red). Ids: "system:<uri>" o "file:<nombre>".
 */
object Sounds {
    private const val TAG = "Sounds"
    const val MAX_BYTES = 5 * 1024 * 1024
    private val EXTENSIONS = setOf("mp3", "ogg", "wav", "m4a")
    private val main = Handler(Looper.getMainLooper())
    private var previewPlayer: MediaPlayer? = null

    /** Nombre seguro para disco (solo [A-Za-z0-9_-] + extensión admitida) o null. */
    fun sanitizeName(raw: String): String? {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val ext = base.substringAfterLast('.', "").lowercase()
        if (ext !in EXTENSIONS) return null
        val stem = base.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(60)
        return if (stem.isEmpty()) null else "$stem.$ext"
    }

    /** Solo URIs locales (nunca red): content:// o android.resource://. */
    fun isLocalSystemUri(s: String): Boolean =
        s.length > 10 && (s.startsWith("content://") || s.startsWith("android.resource://"))

    /** Archivo de un id "file:…" si existe en [dir]; null en cualquier otro caso. */
    fun fileFor(dir: File, id: String?): File? {
        if (id == null || !id.startsWith("file:")) return null
        val name = sanitizeName(id.removePrefix("file:")) ?: return null
        return File(dir, name).takeIf { it.isFile }
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "sounds").apply { mkdirs() }

    /** URI reproducible para [id], o null (=> el que llama usa el tono por defecto). */
    fun uri(ctx: Context, id: String?): Uri? = when {
        id == null -> null
        id.startsWith("system:") -> id.removePrefix("system:").takeIf { isLocalSystemUri(it) }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        else -> fileFor(dir(ctx), id)?.let { Uri.fromFile(it) }
    }

    fun list(ctx: Context): JSONArray {
        val out = JSONArray()
        runCatching {
            val rm = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_ALARM) }
            val c = rm.cursor
            while (c.moveToNext()) {
                val uri = rm.getRingtoneUri(c.position)
                out.put(JSONObject().put("id", "system:$uri").put("name", c.getString(RingtoneManager.TITLE_COLUMN_INDEX)).put("kind", "system"))
            }
        }.onFailure { Log.w(TAG, "Sin tonos del sistema", it) }
        dir(ctx).listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name }?.forEach {
            out.put(JSONObject().put("id", "file:${it.name}").put("name", it.name).put("kind", "file").put("size", it.length()))
        }
        return out
    }

    /** Guarda un sonido subido y comprueba que se puede reproducir. Devuelve su id. */
    fun save(ctx: Context, rawName: String, bytes: ByteArray): String {
        val name = sanitizeName(rawName) ?: throw IllegalArgumentException("nombre no válido: usa mp3, ogg, wav o m4a")
        require(bytes.isNotEmpty()) { "archivo vacío" }
        require(bytes.size <= MAX_BYTES) { "el sonido supera 5 MB" }
        val d = dir(ctx)
        val tmp = File(d, ".$name.tmp")
        try {
            tmp.writeBytes(bytes)
            val playable = runCatching {
                val mp = MediaPlayer()
                try { mp.setDataSource(tmp.path); mp.prepare() } finally { mp.release() }
            }.isSuccess
            if (!playable) throw IllegalArgumentException("el reloj no puede reproducir ese archivo")
            val f = File(d, name)
            if (!tmp.renameTo(f)) throw IllegalArgumentException("no se pudo guardar el sonido")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return "file:$name"
    }

    fun delete(ctx: Context, name: String): Boolean {
        val f = fileFor(dir(ctx), "file:$name") ?: return false
        return f.delete()
    }

    /** Suena 5 s por el canal de alarma. false si hay una alarma sonando. */
    @Synchronized fun preview(ctx: Context, id: String?): Boolean {
        if (AlarmRingService.ringing) return false
        stopPreview()
        val uri = uri(ctx, id)
            ?: RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            mp.setDataSource(ctx, uri)
            mp.prepare()
            if (AlarmRingService.ringing) { mp.release(); return false }
            mp.start()
            previewPlayer = mp
            main.postDelayed({ stopPreview() }, 5_000)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Preescucha fallida", e)
            mp.release()
            throw IllegalArgumentException("no se pudo reproducir el sonido")
        }
    }

    @Synchronized fun stopPreview() {
        main.removeCallbacksAndMessages(null)
        previewPlayer?.let { runCatching { it.stop() }; it.release() }
        previewPlayer = null
    }
}
