package com.pocket.opencode

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class EmbeddedStatus(
    val state: EmbeddedState = EmbeddedState.STOPPED,
    val port: Int = 0,
    val password: String = "",
    val url: String = "",
    val error: String = "",
    val version: String = "",
    val workspace: String = "",
)

/**
 * Управляет дочерним процессом OpenCode: запускает бинарник из
 * nativeLibraryDir, читает stdout, ждёт готовности HTTP и держит статус
 * для UI. Один экземпляр на процесс приложения.
 */
object EmbeddedServer {
    private const val HEALTH_TIMEOUT_MS = 30_000L
    private const val HEALTH_INTERVAL_MS = 500L
    private const val LOG_MAX_BYTES = 2L * 1024 * 1024

    private val _status = MutableStateFlow(EmbeddedStatus())
    val status: StateFlow<EmbeddedStatus> = _status.asStateFlow()

    private val ring = LogRing(400)
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val lock = Any()
    private var process: Process? = null
    private var logFile: File? = null

    /** Поколение запуска: старые потоки не должны портить новый статус. */
    private var generation = 0

    fun start(context: Context, workspace: File) {
        val app = context.applicationContext
        val gen: Int
        synchronized(lock) {
            val state = _status.value.state
            if (state == EmbeddedState.STARTING || state == EmbeddedState.RUNNING) return
            gen = ++generation
            ring.clear()
            _log.value = emptyList()
            _status.value = EmbeddedStatus(state = EmbeddedState.STARTING)
        }
        thread(name = "embedded-server-start") {
            runStart(app, workspace, gen)
        }
    }

    fun stop() {
        val proc: Process?
        synchronized(lock) {
            generation++
            proc = process
            process = null
        }
        destroy(proc)
        _status.value = EmbeddedStatus(state = EmbeddedState.STOPPED)
    }

    fun restart(context: Context, workspace: File) {
        stop()
        start(context, workspace)
    }

    private fun destroy(proc: Process?) {
        proc ?: return
        runCatching {
            proc.destroy()
            if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroyForcibly()
        }
    }

    /**
     * Если общий каталог недоступен (нет «Доступа ко всем файлам»), сервер
     * всё равно должен запуститься — уходим во внутреннюю папку.
     */
    private fun pickWorkspace(root: File, requested: File): File {
        if (requested.isDirectory) return requested
        if (runCatching { requested.mkdirs() }.getOrDefault(false) && requested.isDirectory) {
            return requested
        }
        val fallback = File(root, "workspace")
        runCatching { fallback.mkdirs() }
        return fallback
    }

    private fun runStart(context: Context, workspace: File, gen: Int) {
        val root = File(context.filesDir, "opencode")
        val actualWorkspace = pickWorkspace(root, workspace)
        val paths = ServerPaths.forContext(context, actualWorkspace)
        try {
            listOf(
                paths.home, paths.data, paths.config, paths.cache,
                paths.logs, paths.tmp, actualWorkspace,
            ).forEach { it.mkdirs() }
        } catch (t: Throwable) {
            fail(gen, "Не удалось создать каталоги: ${t.message}")
            return
        }
        synchronized(lock) {
            if (gen == generation) {
                _status.value = _status.value.copy(workspace = actualWorkspace.absolutePath)
            }
        }

        // Скилы из assets должны оказаться в конфиге до старта процесса,
        // иначе первый /api/skill не увидит встроенный набор.
        runCatching { SkillSeeder.seed(context, paths) }

        // ripgrep для grep/glob: symlink в app-owned каталоге на ELF из
        // nativeLibraryDir (exec из app-data запрещён начиная с API 29).
        val tools = runCatching { Toolchain.ensure(context, paths) }.getOrElse {
            Toolchain.Status(Toolchain.toolsDir(paths), error = it.message ?: it.toString())
        }
        DebugLog.event(
            "toolchain",
            if (tools.ok) "rg ready" else "rg unavailable",
            buildMap {
                put("dir", tools.dir.absolutePath)
                tools.rg?.let { put("rg", it.absolutePath) }
                tools.pocketRust?.let { put("pocket-rs", it.absolutePath) }
                if (tools.version.isNotBlank()) put("version", tools.version.lineSequence().first())
                if (tools.error.isNotBlank()) put("error", tools.error)
            },
        )

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val bin = File(nativeDir, "libocserver.so")
        if (!bin.isFile) {
            fail(gen, "Не найден встроенный сервер: ${bin.absolutePath}")
            return
        }
        if (!bin.canExecute()) bin.setExecutable(true)

        val file = File(paths.logs, "server.log")
        synchronized(lock) { logFile = file }

        val port = PortSelection.choose()
        val passwordRef = arrayOf("")
        val urlRef = arrayOf("")
        var proc: Process? = null

        try {
            val builder = ProcessBuilder(
                bin.absolutePath,
                "serve",
                "--hostname", "127.0.0.1",
                "--port", port.toString(),
            )
            builder.directory(actualWorkspace)
            builder.redirectErrorStream(true)
            // Наследуем системное окружение (ANDROID_ROOT и т.п.) и
            // перекрываем только то, что относится к OpenCode.
            builder.environment().putAll(ServerEnv.build(paths, tools.dir, nativeDir))
            val started = builder.start()
            proc = started
            synchronized(lock) {
                if (gen != generation) {
                    destroy(started)
                    return
                }
                process = started
            }

            val capturing = started
            thread(name = "embedded-server-reader") {
                runCatching {
                    capturing.inputStream.bufferedReader().forEachLine { line ->
                        appendLog(line)
                        ServerOutput.password(line)?.let { value ->
                            passwordRef[0] = value
                            synchronized(lock) {
                                if (gen == generation) {
                                    _status.value = _status.value.copy(
                                        password = value,
                                        port = port,
                                        url = urlRef[0].ifBlank { "http://127.0.0.1:$port" },
                                    )
                                }
                            }
                        }
                        ServerOutput.listeningUrl(line)?.let { value ->
                            urlRef[0] = value
                            synchronized(lock) {
                                if (gen == generation) {
                                    _status.value = _status.value.copy(
                                        state = EmbeddedState.STARTING,
                                        port = portOf(value),
                                        url = value,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            waitForHealth(capturing, port, passwordRef, gen, file)
        } catch (t: Throwable) {
            destroy(proc)
            fail(gen, "Не удалось запустить сервер: ${t.message}")
        }
    }

    private fun waitForHealth(
        proc: Process,
        port: Int,
        passwordRef: Array<String>,
        gen: Int,
        file: File,
    ) {
        val deadline = System.currentTimeMillis() + HEALTH_TIMEOUT_MS
        var lastCode = -1
        while (System.currentTimeMillis() < deadline) {
            if (gen != generation) return
            if (!proc.isAlive) {
                val exit = runCatching { proc.exitValue() }.getOrDefault(-1)
                fail(gen, "Сервер завершился (код $exit).\n${tail(file)}")
                return
            }
            val code = probeInfo(port, passwordRef[0])
            lastCode = code
            if (code == 200) {
                val version = readVersion(port, passwordRef[0])
                synchronized(lock) {
                    if (gen != generation) return
                    _status.value = _status.value.copy(
                        state = EmbeddedState.RUNNING,
                        port = port,
                        password = passwordRef[0],
                        url = "http://127.0.0.1:$port",
                        error = "",
                        version = version,
                    )
                }
                DebugLog.event("server", "running", mapOf("port" to port.toString(), "version" to version))
                return
            }
            Thread.sleep(HEALTH_INTERVAL_MS)
        }
        if (gen != generation) return
        fail(gen, "Сервер не ответил за 30 секунд (последний код $lastCode).\n${tail(file)}")
    }

    private fun probeInfo(port: Int, password: String): Int = runCatching {
        val conn = URL("http://127.0.0.1:$port/api/info").openConnection() as HttpURLConnection
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        conn.requestMethod = "GET"
        if (password.isNotBlank()) conn.setRequestProperty("Authorization", basic(password))
        val code = conn.responseCode
        conn.disconnect()
        code
    }.getOrDefault(-1)

    private fun readVersion(port: Int, password: String): String = runCatching {
        val conn = URL("http://127.0.0.1:$port/api/info").openConnection() as HttpURLConnection
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        if (password.isNotBlank()) conn.setRequestProperty("Authorization", basic(password))
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        JSONObject(text).optString("version")
    }.getOrDefault("")

    private fun basic(password: String): String =
        "Basic " + Base64.encodeToString("opencode:$password".toByteArray(), Base64.NO_WRAP)

    private fun portOf(url: String): Int =
        runCatching { URL(url).port }.getOrDefault(0)

    private fun fail(gen: Int, message: String) {
        synchronized(lock) {
            if (gen != generation) return
            _status.value = _status.value.copy(state = EmbeddedState.ERROR, error = message)
        }
        appendLog("ERROR $message")
    }

    private fun appendLog(line: String) {
        ring.append(line)
        _log.value = ring.snapshot()
        val file = synchronized(lock) { logFile } ?: return
        runCatching {
            synchronized(lock) {
                if (file.exists() && file.length() > LOG_MAX_BYTES) {
                    val old = File(file.parentFile, "server.log.1")
                    old.delete()
                    file.renameTo(old)
                }
                file.appendText(line + "\n")
            }
        }
    }

    private fun tail(file: File): String = runCatching {
        if (!file.isFile) return@runCatching ""
        file.readLines().takeLast(20).joinToString("\n")
    }.getOrDefault("")
}
