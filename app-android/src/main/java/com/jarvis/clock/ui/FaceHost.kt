package com.jarvis.clock.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jarvis.clock.design.Backgrounds
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Aloja el motor de pantallas (assets/face) en un WebView. Solo se carga esa página, servida por
 * la propia app; cualquier otra petición responde 404 y la navegación externa está bloqueada.
 * Si el WebView no existe, se cae o no carga, avisa con [Listener.onFailure] para volver a la
 * carátula nativa.
 */
class FaceHost private constructor(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        fun onTap()
        fun onDoubleTap()
        fun onLongPress()
        fun onPermission()
        /** [kind] es uno de REASON_*: solo REASON_RENDERER merece un reintento. */
        fun onFailure(kind: String, reason: String)
    }

    val view: WebView = WebView(ctx)
    private val main = Handler(Looper.getMainLooper())
    private class Job(val js: String, val check: String?)
    // Solo se conserva el último render y el último setData hasta que el motor esté listo.
    private var pendingRender: Job? = null
    private var pendingData: Job? = null
    private var ready = false
    private var released = false
    @Volatile private var dead = false

    init {
        configure()
        view.loadUrl(PAGE)
        // Si onPageFinished nunca llega, la carátula nativa no puede quedarse esperando para siempre.
        main.postDelayed({ if (!ready && !dead && !released) fail(REASON_LOAD, "tiempo de carga agotado") }, LOAD_TIMEOUT_MS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        view.setBackgroundColor(Color.BLACK)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.isHapticFeedbackEnabled = false
        view.setOnLongClickListener { true }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        view.addJavascriptInterface(Bridge(), "Native")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = true
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse =
                intercept(request.url)
            override fun onPageFinished(v: WebView, url: String) {
                // La página puede cargar y el motor no arrancar (error de JS): se comprueba antes de usarlo.
                v.evaluateJavascript("typeof window.Face") { r ->
                    if (released || dead) return@evaluateJavascript
                    if (r != "\"object\"") { fail(REASON_ENGINE, "el motor no cargó"); return@evaluateJavascript }
                    ready = true
                    pendingRender?.let { run(it) }
                    pendingData?.let { run(it) }
                    pendingRender = null
                    pendingData = null
                }
            }
            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) fail(REASON_LOAD, "carga: ${error.description}")
            }
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                fail(REASON_RENDERER, "el proceso de dibujo murió")
                return true
            }
        }
    }

    private fun fail(kind: String, reason: String) {
        if (dead) return
        dead = true
        if (!released) main.post { if (!released) listener.onFailure(kind, reason) }
    }

    private fun notFound() =
        WebResourceResponse("text/plain", "utf-8", 404, "No encontrado", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun intercept(uri: Uri): WebResourceResponse = try {
        interceptOrNull(uri) ?: notFound()
    } catch (e: Exception) {
        Log.w(TAG, "intercept falló para $uri", e)
        notFound()
    }

    private fun interceptOrNull(uri: Uri): WebResourceResponse? {
        if (uri.host == HOST) {
            val path = uri.path.orEmpty()
            if (path.startsWith("/assets/face/")) {
                val name = path.removePrefix("/assets/face/")
                if (name in FILES) {
                    val mime = when (name.substringAfterLast('.')) {
                        "html" -> "text/html"
                        "css" -> "text/css"
                        else -> "application/javascript"
                    }
                    return WebResourceResponse(mime, "utf-8", ctx.assets.open("face/$name"))
                }
            } else if (path.startsWith("/bg/")) {
                val f = Backgrounds.fileFor(Backgrounds.dir(ctx), path.removePrefix("/bg/"))
                if (f != null) return WebResourceResponse(Backgrounds.mimeOf(f.name), null, FileInputStream(f))
            }
        }
        return null
    }

    /** Puente hacia Android: solo existe en la página del motor. */
    inner class Bridge {
        @JavascriptInterface fun tap() { main.post { listener.onTap() } }
        @JavascriptInterface fun doubleTap() { main.post { listener.onDoubleTap() } }
        @JavascriptInterface fun longPress() { main.post { listener.onLongPress() } }
        @JavascriptInterface fun permission() { main.post { listener.onPermission() } }
        @JavascriptInterface fun error(msg: String) { Log.w(TAG, "motor: $msg") }
    }

    fun render(design: JSONObject, opts: JSONObject) {
        val js = "(function(){try{Face.render(JSON.parse(${JSONObject.quote(design.toString())}), " +
            "JSON.parse(${JSONObject.quote(opts.toString())}));return 'ok'}catch(e){return 'error: '+e.message}})()"
        eval(Job(js, "render"), true)
    }

    fun setData(data: JSONObject) = eval(Job("Face.setData(JSON.parse(${JSONObject.quote(data.toString())}))", null), false)

    private fun eval(job: Job, isRender: Boolean) {
        if (released || dead) return
        if (!ready) { if (isRender) pendingRender = job else pendingData = job; return }
        run(job)
    }

    private fun run(job: Job) {
        if (released || dead) return
        if (job.check == null) { view.evaluateJavascript(job.js, null); return }
        view.evaluateJavascript(job.js) { r -> if (r != "\"ok\"") fail(REASON_ENGINE, "${job.check}: $r") }
    }

    fun release() {
        if (released) return
        released = true
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    companion object {
        private const val TAG = "FaceHost"
        const val REASON_RENDERER = "renderer"
        const val REASON_LOAD = "load"
        const val REASON_ENGINE = "engine"
        private const val LOAD_TIMEOUT_MS = 10_000L
        private const val HOST = "appassets.androidplatform.net"
        private const val PAGE = "https://$HOST/assets/face/index.html"
        private val FILES = setOf("index.html", "face.css", "face-core.js", "face.js")

        /** null si el dispositivo no tiene WebView o no se puede crear. */
        fun create(ctx: Context, listener: Listener): FaceHost? = try {
            FaceHost(ctx, listener)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear el WebView de la carátula", e)
            null
        }
    }
}
