package com.jarvis.clock.server

import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Servidor HTTP/1.1 mínimo (sin NanoHTTPD ni dependencias). Suficiente para
 * unas pocas peticiones JSON desde Hermes/Home Assistant por Tailscale.
 */
class HttpServer(
    private val port: Int,
    private val bindHost: String? = null,
    private val handler: (Request) -> Response,
    /** Se llama con la cabecera ya leída y antes de leer el cuerpo; si devuelve algo, se responde eso. */
    private val preAuth: (Request) -> Response? = { null },
    workers: Int = 4,
    queueSize: Int = 16,
    private val requestTimeoutMs: Long = 10_000,
    private val socketFactory: (InetSocketAddress) -> ServerSocket =
        { addr -> ServerSocket().apply { reuseAddress = true; bind(addr) } },
) {

    data class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        fun header(name: String) = headers[name.lowercase()]
        fun json(): JSONObject = if (body.isEmpty()) JSONObject() else JSONObject(String(body, Charsets.UTF_8))
    }

    class Response(val status: Int, val body: ByteArray, val contentType: String = "application/json; charset=utf-8") {
        companion object {
            fun json(status: Int, o: JSONObject) = Response(status, o.toString().toByteArray())
            fun ok(o: JSONObject = JSONObject()) = json(200, o.put("ok", true))
            fun error(status: Int, msg: String) = json(status, JSONObject().put("ok", false).put("error", msg))
        }
    }

    private var socket: ServerSocket? = null
    // Pool acotado con cola corta: lo que no cabe se cierra en el acto en vez de acumularse.
    private val pool = ThreadPoolExecutor(
        workers, workers, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(queueSize), ThreadPoolExecutor.AbortPolicy(),
    )
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                val addr = if (bindHost != null) InetSocketAddress(bindHost, port) else InetSocketAddress(port)
                val ss = socketFactory(addr)
                socket = ss
                Log.i(TAG, "Escuchando en $addr")
                while (running) {
                    val s = try {
                        ss.accept()
                    } catch (e: IOException) {
                        // Errores transitorios (EMFILE, etc.): no se mata el bucle.
                        if (!running) break
                        Log.w(TAG, "accept falló, reintento", e)
                        Thread.sleep(200)
                        continue
                    }
                    try {
                        pool.execute { serve(s) }
                    } catch (e: RejectedExecutionException) {
                        runCatching { s.close() }
                    }
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "Servidor caído", e)
            }
        }, "http-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        pool.shutdownNow()
    }

    private fun serve(s: Socket) {
        s.use { sock ->
            sock.soTimeout = 5_000
            val deadline = System.nanoTime() + requestTimeoutMs * 1_000_000
            val resp = try {
                val input = BufferedInputStream(sock.getInputStream())
                val head = parseHead(input, deadline)
                if (head == null) Response.error(400, "petición inválida")
                else {
                    // Autenticación antes de leer el cuerpo: sin token no se reserva memoria.
                    val rejected = preAuth(head)
                    if (rejected != null) rejected
                    else {
                        val len = head.headers["content-length"]?.toIntOrNull() ?: 0
                        if (len < 0 || len > maxBodyFor(head)) Response.error(400, "cuerpo no válido")
                        else handler(head.copy(body = readBody(input, len, deadline)))
                    }
                }
            } catch (e: Throwable) {
                // Throwable: un OutOfMemoryError no debe tumbar todo el proceso.
                Log.w(TAG, "Error atendiendo petición", e)
                Response.error(500, "error interno")
            }
            val out = sock.getOutputStream()
            val head = buildString {
                append("HTTP/1.1 ${resp.status} ${reason(resp.status)}\r\n")
                append("Content-Type: ${resp.contentType}\r\n")
                append("Content-Length: ${resp.body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            // El cliente puede haberse ido (cortado por plazo, etc.): no es un error nuestro.
            runCatching {
                out.write(head.toByteArray())
                out.write(resp.body)
                out.flush()
            }
        }
    }

    /** Lee la línea de petición y las cabeceras (con límites). El cuerpo queda vacío. */
    private fun parseHead(input: InputStream, deadline: Long): Request? {
        val requestLine = readLine(input, deadline) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        var count = 0
        var bytes = 0
        while (true) {
            val line = readLine(input, deadline) ?: return null
            if (line.isEmpty()) break
            bytes += line.length
            if (++count > MAX_HEADERS || bytes > MAX_HEADER_BYTES) return null
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = try {
            target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
        } catch (e: IllegalArgumentException) {
            return null
        }
        return Request(parts[0].uppercase(), path, query, headers, ByteArray(0))
    }

    private fun readBody(input: InputStream, len: Int, deadline: Long): ByteArray {
        val body = ByteArray(len)
        var read = 0
        while (read < len) {
            if (System.nanoTime() > deadline) throw IOException("plazo agotado")
            val n = input.read(body, read, len - read)
            if (n < 0) break
            read += n
        }
        return body
    }

    // Solo las subidas de fotos y sonidos admiten cuerpos grandes.
    private fun maxBodyFor(r: Request) =
        if (r.path == "/backgrounds" || r.path == "/sounds") MAX_BODY else MAX_BODY_SMALL

    private fun readLine(input: InputStream, deadline: Long): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            if (System.nanoTime() > deadline) throw IOException("plazo agotado")
            val c = input.read()
            if (c < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (c == '\n'.code) break
            if (c != '\r'.code) buf.write(c)
            if (buf.size() > 8192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"
        404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error"
    }

    companion object {
        private const val TAG = "HttpServer"
        private const val MAX_BODY = 8 * 1024 * 1024 // fotos en bruto (hasta 8 MB)
        private const val MAX_BODY_SMALL = 256 * 1024
        private const val MAX_HEADERS = 50
        private const val MAX_HEADER_BYTES = 16 * 1024
    }
}
