package com.pocket.opencode

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * ВРЕМЕННЫЙ спайк: проверяем, что нативный сервер OpenCode из APK
 * запускается в песочнице приложения и отвечает по HTTP.
 * Удалить после проверки.
 */
object SpikeProbe {
    private const val PORT = 4095
    private val lock = Any()

    fun start(context: Context) {
        thread(name = "spike-probe") {
            val log = File(context.filesDir, "probe.log")
            log.writeText("")
            fun say(line: String) {
                synchronized(lock) { log.appendText(line + "\n") }
            }
            try {
                val bin = File(context.applicationInfo.nativeLibraryDir, "libocserver.so")
                say("bin=${bin.absolutePath}")
                say("exists=${bin.exists()} canExecute=${bin.canExecute()} size=${bin.length()}")
                val home = File(context.filesDir, "probe-home")
                File(home, "data").mkdirs()
                File(home, "config").mkdirs()
                File(home, "cache").mkdirs()
                val pb = ProcessBuilder(
                    bin.absolutePath, "serve",
                    "--hostname", "127.0.0.1", "--port", PORT.toString()
                )
                pb.environment().apply {
                    put("HOME", home.absolutePath)
                    put("XDG_DATA_HOME", File(home, "data").absolutePath)
                    put("XDG_CONFIG_HOME", File(home, "config").absolutePath)
                    put("XDG_CACHE_HOME", File(home, "cache").absolutePath)
                    put("TMPDIR", context.cacheDir.absolutePath)
                    put("PATH", "/system/bin:/system/xbin")
                }
                pb.directory(home)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                say("started")
                thread(name = "spike-reader") {
                    proc.inputStream.bufferedReader().lineSequence().forEach { say("OUT $it") }
                }
                repeat(60) { i ->
                    Thread.sleep(500)
                    val code = runCatching {
                        val conn = URL("http://127.0.0.1:$PORT/api/info").openConnection() as HttpURLConnection
                        conn.connectTimeout = 1000
                        conn.readTimeout = 1000
                        val result = conn.responseCode
                        conn.disconnect()
                        result
                    }.getOrElse { -1 }
                    if (i % 4 == 0) say("health=$code")
                    if (code == 200 || code == 401) {
                        say("ALIVE code=$code")
                        return@thread
                    }
                }
                say("NOT_ALIVE")
            } catch (t: Throwable) {
                say("FAIL ${t.javaClass.name}: ${t.message}")
                t.stackTrace.take(10).forEach { say("  at $it") }
            }
        }
    }
}
