package com.jarvis.clock

import com.jarvis.clock.server.HttpServer
import com.jarvis.clock.server.HttpServer.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** Tests JVM del servidor HTTP con sockets reales en localhost. */
class HttpServerTest {
    private var server: HttpServer? = null

    @After fun cierra() { server?.stop() }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun arranca(
        handler: (HttpServer.Request) -> Response = { Response.ok() },
        preAuth: (HttpServer.Request) -> Response? = { null },
        workers: Int = 2, queue: Int = 4, timeoutMs: Long = 10_000,
        socketFactory: ((InetSocketAddress) -> ServerSocket)? = null,
    ): Int {
        val port = freePort()
        val s = if (socketFactory == null)
            HttpServer(port, "127.0.0.1", handler, preAuth, workers, queue, timeoutMs)
        else HttpServer(port, "127.0.0.1", handler, preAuth, workers, queue, timeoutMs, socketFactory)
        s.start()
        server = s
        // espera a que escuche
        repeat(50) {
            if (runCatching { Socket("127.0.0.1", port).close() }.isSuccess) return port
            Thread.sleep(40)
        }
        return port
    }

    private fun peticion(port: Int, raw: String): String =
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().apply { write(raw.toByteArray()); flush() }
            s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }

    @Test fun `atiende una peticion normal y no manda CORS`() {
        val port = arranca()
        val r = peticion(port, "GET /x HTTP/1.1\r\nHost: a\r\n\r\n")
        assertTrue(r.startsWith("HTTP/1.1 200"))
        assertFalse(r.contains("Access-Control", ignoreCase = true))
    }

    @Test fun `demasiadas cabeceras dan 400 y no llegan al handler`() {
        val llamadas = AtomicInteger()
        val port = arranca(handler = { llamadas.incrementAndGet(); Response.ok() })
        val cab = (1..200).joinToString("") { "X$it: a\r\n" }
        val r = peticion(port, "GET /x HTTP/1.1\r\n$cab\r\n")
        assertTrue(r.startsWith("HTTP/1.1 400"))
        assertEquals(0, llamadas.get())
    }

    @Test fun `el cuerpo no se lee si la autenticacion falla`() {
        val llamadas = AtomicInteger()
        val port = arranca(
            handler = { llamadas.incrementAndGet(); Response.ok() },
            preAuth = { Response.error(401, "token inválido") },
        )
        // Anuncia 1 MB de cuerpo y no manda nada: debe responder 401 sin esperarlo.
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 3_000
            s.getOutputStream().apply {
                write("POST /backgrounds HTTP/1.1\r\nContent-Length: 1000000\r\n\r\n".toByteArray()); flush()
            }
            val head = s.getInputStream().bufferedReader().readLine()
            assertTrue(head, head.startsWith("HTTP/1.1 401"))
        }
        assertEquals(0, llamadas.get())
    }

    @Test fun `content-length negativo se rechaza`() {
        val port = arranca()
        val r = peticion(port, "POST /x HTTP/1.1\r\nContent-Length: -5\r\n\r\n")
        assertTrue(r.startsWith("HTTP/1.1 400"))
    }

    @Test fun `un cliente lento se corta por plazo total`() {
        val port = arranca(timeoutMs = 600)
        val t0 = System.nanoTime()
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5_000
            val out = s.getOutputStream()
            // Un byte cada 200 ms: cada lectura sería válida, pero el plazo total no.
            val cortado = runCatching {
                repeat(40) { out.write('G'.code); out.flush(); Thread.sleep(200) }
            }.isFailure
            val fin = runCatching { s.getInputStream().read() }.getOrDefault(-1)
            assertTrue(cortado || fin != 0)
        }
        assertTrue("tardó demasiado", (System.nanoTime() - t0) / 1_000_000 < 4_000)
    }

    @Test fun `con el pool y la cola llenos se cierran las conexiones sobrantes`() {
        val port = arranca(workers = 1, queue = 1, timeoutMs = 10_000)
        Thread.sleep(300) // deja terminar la conexión de sondeo
        val ocupadas = (1..2).map { Socket("127.0.0.1", port).also { s -> s.getOutputStream().write('G'.code) } }
        Thread.sleep(300)
        val extra = Socket("127.0.0.1", port).apply { soTimeout = 2_000 }
        val leido = try { extra.getInputStream().read() } catch (e: java.net.SocketTimeoutException) { -2 } catch (e: java.io.IOException) { -1 }
        assertEquals(-1, leido) // cerrada por el servidor, no encolada
        extra.close(); ocupadas.forEach { it.close() }
    }

    @Test fun `accept sobrevive a un error y sigue sirviendo`() {
        val fallos = AtomicInteger()
        val port = arranca(socketFactory = { addr ->
            object : ServerSocket() {
                override fun accept(): Socket {
                    if (fallos.getAndIncrement() == 0) throw java.io.IOException("EMFILE simulado")
                    return super.accept()
                }
            }.apply { reuseAddress = true; bind(addr) }
        })
        val r = peticion(port, "GET /x HTTP/1.1\r\n\r\n")
        assertTrue(r.startsWith("HTTP/1.1 200"))
    }
}
