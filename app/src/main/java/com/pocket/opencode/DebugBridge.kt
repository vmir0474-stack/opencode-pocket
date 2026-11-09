package com.pocket.opencode

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Скрытый HTTP-канал отладки. Слушает только loopback, требует токен из
 * приватной папки приложения. Позволяет читать состояние и ошибки, а также
 * дергать сервер и API без открытия UI.
 */
object DebugBridge {
    private const val FIRST_PORT = 4097
    private const val LAST_PORT = 4100

    private val actions = setOf(
        "restart", "start", "stop", "reload", "skills", "mcp", "config", "prompt", "api",
        "apps", "launch",
    )
    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "debug-bridge-client").apply { isDaemon = true }
    }

    @Volatile private var socket: ServerSocket? = null
    @Volatile private var running = false
    @Volatile private var token = ""
    @Volatile private var port = 0
    @Volatile private var logsDir: File? = null

    @Volatile var stateProvider: (() -> JSONObject)? = null
    @Volatile var actionHandler: ((String, JSONObject) -> JSONObject)? = null

    fun start(context: Context) {
        if (running) return
        val dir = File(context.filesDir, "opencode/debug")
        runCatching { dir.mkdirs() }
        logsDir = File(context.filesDir, "opencode/logs")
        token = loadToken(File(dir, "token"))
        var bound: ServerSocket? = null
        synchronized(this) {
            if (running) return
            val candidate = bind()
            if (candidate == null) {
                DebugLog.event("debug", "bridge port busy")
                return
            }
            socket = candidate
            port = candidate.localPort
            running = true
            bound = candidate
        }
        val server = bound ?: return
        DebugLog.event("debug", "bridge listening", mapOf("port" to port.toString()))
        thread(name = "debug-bridge") { acceptLoop(server) }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        port = 0
    }

    fun info(): JSONObject = JSONObject().apply {
        put("enabled", running)
        put("port", port)
        put("url", if (running) "http://127.0.0.1:$port" else "")
        put("token", token)
    }

    fun authorized(header: String?, expected: String): Boolean {
        val presented = when {
            header == null -> return false
            header.startsWith("Bearer ", ignoreCase = true) ->
                header.substring(7).trim()
            else -> header.trim()
        }
        if (presented.isEmpty() || expected.isEmpty()) return false
        return MessageDigest.isEqual(
            presented.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    fun actionAllowed(name: String): Boolean = name in actions

    private fun loadToken(file: File): String {
        runCatching { if (file.isFile) return file.readText().trim() }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val fresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(fresh)
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
        return fresh
    }

    private fun bind(): ServerSocket? {
        for (p in FIRST_PORT..LAST_PORT) {
            val candidate = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), p))
                }
            }.getOrNull()
            if (candidate != null) return candidate
        }
        return null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            pool.execute { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            runCatching {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return respond(output, 400, error("bad request"))
                val method = parts[0].uppercase()
                val target = parts[1]
                val path = target.substringBefore('?')
                val query = target.substringAfter('?', "")

                var contentLength = 0
                var authHeader: String? = null
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val name = line.substringBefore(':', "").trim().lowercase()
                    val value = line.substringAfter(':', "").trim()
                    when (name) {
                        "content-length" -> contentLength = value.toIntOrNull() ?: 0
                        "authorization" -> authHeader = value
                    }
                }
                val body = readBody(input, contentLength)
                val supplied = authHeader ?: queryToken(query)
                if (!authorized(supplied, token)) {
                    return respond(output, 401, error("unauthorized"))
                }

                val response = when {
                    method == "GET" && path == "/health" ->
                        JSONObject().put("ok", true).put("port", port)
                    method == "GET" && path == "/state" ->
                        stateProvider?.invoke() ?: JSONObject().put("ok", true)
                    method == "GET" && path == "/events" ->
                        eventsJson(limitParam(query, 100))
                    method == "GET" && path == "/logs" ->
                        logsJson(limitParam(query, 200))
                    method == "POST" && path == "/action" ->
                        runAction(JSONObject(body.ifBlank { "{}" }))
                    method == "POST" && path == "/api" ->
                        actionHandler?.invoke("api", JSONObject(body.ifBlank { "{}" }))
                            ?: error("action handler is not ready")
                    else -> return respond(output, 404, error("not found"))
                }
                respond(output, 200, response.toString())
            }.onFailure {
                DebugLog.error("bridge", it)
                runCatching { respond(socket.getOutputStream(), 500, error(it.message ?: "error")) }
            }
        }
    }

    private fun runAction(body: JSONObject): JSONObject {
        val name = body.optString("name")
        if (!actionAllowed(name)) return error("unknown action: $name")
        val handler = actionHandler ?: return error("action handler is not ready")
        return runCatching { handler(name, body) }.getOrElse { error(it.message ?: "action failed") }
    }

    private fun eventsJson(limit: Int): JSONObject {
        val array = JSONArray()
        DebugLog.recent(limit).forEach { array.put(it.toJson()) }
        return JSONObject().put("data", array)
    }

    private fun logsJson(limit: Int): JSONObject {
        val file = logsDir?.let { File(it, "server.log") }
        val lines = runCatching {
            if (file != null && file.isFile) file.readLines().takeLast(limit) else emptyList()
        }.getOrDefault(emptyList()).map { Redaction.redact(it) }
        return JSONObject().put("data", JSONArray(lines))
    }

    private fun limitParam(query: String, fallback: Int): Int =
        Regex("(?:^|&)limit=(\\d+)").find(query)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(1, 1000) ?: fallback

    private fun queryToken(query: String): String? =
        Regex("(?:^|&)token=([^&]+)").find(query)?.groupValues?.get(1)

    private fun error(message: String): JSONObject =
        JSONObject().put("error", Redaction.redact(message))

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (out.size() == 0) null else out.toString("UTF-8")
            if (byte == '\n'.code) break
            if (byte != '\r'.code) out.write(byte)
        }
        return out.toString("UTF-8")
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val out = ByteArrayOutputStream(length)
        val buffer = ByteArray(4096)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toString("UTF-8")
    }

    private fun respond(output: java.io.OutputStream, code: Int, body: Any) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val head = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}
