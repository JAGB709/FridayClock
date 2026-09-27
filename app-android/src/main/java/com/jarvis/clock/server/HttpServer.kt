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
import java.util.concurrent.Executors

/**
 * Servidor HTTP/1.1 mínimo (sin NanoHTTPD ni dependencias). Suficiente para
 * unas pocas peticiones JSON desde Hermes/Home Assistant por Tailscale.
 */
class HttpServer(
    private val port: Int,
    private val bindHost: String? = null,
    private val handler: (Request) -> Response,
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
    private val pool = Executors.newFixedThreadPool(3)
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                val addr = if (bindHost != null) InetSocketAddress(bindHost, port) else InetSocketAddress(port)
                val ss = ServerSocket().apply { reuseAddress = true; bind(addr) }
                socket = ss
                Log.i(TAG, "Escuchando en $addr")
                while (running) {
                    val s = ss.accept()
                    pool.execute { serve(s) }
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
            sock.soTimeout = 15_000
            val resp = try {
                val req = parse(BufferedInputStream(sock.getInputStream()))
                if (req == null) Response.error(400, "petición inválida")
                else if (req.method == "OPTIONS") Response(204, ByteArray(0))
                else handler(req)
            } catch (e: Exception) {
                Log.w(TAG, "Error atendiendo petición", e)
                Response.error(500, e.message ?: "error")
            }
            val out = sock.getOutputStream()
            val head = buildString {
                append("HTTP/1.1 ${resp.status} ${reason(resp.status)}\r\n")
                append("Content-Type: ${resp.contentType}\r\n")
                append("Content-Length: ${resp.body.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Headers: Content-Type, X-Auth-Token, Authorization\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(head.toByteArray())
            out.write(resp.body)
            out.flush()
        }
    }

    private fun parse(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > MAX_BODY) return null
        val body = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(body, read, len - read)
            if (n < 0) break
            read += n
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return Request(parts[0].uppercase(), path, query, headers, body)
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
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
        private const val MAX_BODY = 8 * 1024 * 1024 // imágenes en base64
    }
}
