package com.pocket.opencode

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Скачивание и установка APK из нашего GitHub-релиза. Rootless-установка
 * всегда показывает системное подтверждение; приложение лишь доводит до
 * него одним нажатием и проверяет SHA-256.
 */
object UpdateInstaller {
    const val APK_NAME = "opencode-pocket.apk"
    const val EXTRA_SESSION_ID = "session_id"

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Настройки «Установка неизвестных приложений» для нашего пакета. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal fun digestMatches(expected: String, actual: String): Boolean =
        expected.isNotBlank() && actual.equals(expected, ignoreCase = true)

    fun apkFileName(info: UpdateInfo): String {
        val safe = info.versionName
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .replace(Regex("\\.{2,}"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '.')
        return if (safe.isBlank()) APK_NAME else "opencode-pocket-$safe.apk"
    }

    suspend fun download(
        info: UpdateInfo,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(info.apkUrl.startsWith("https://")) { "Ссылка на APK должна быть https" }
        require(info.sha256.isNotBlank()) { "В релизе нет SHA-256 — установка отменена" }
        dest.parentFile?.mkdirs()

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "OpenCode-Pocket")
            .build()
        val temp = File(dest.parentFile, "${dest.name}.part")
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} при скачивании APK")
                val body = response.body ?: error("Пустой ответ при скачивании APK")
                val total = body.contentLength().takeIf { it > 0 } ?: info.size
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    temp.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!digestMatches(info.sha256, actual)) {
                    error("SHA-256 не совпал: файл повреждён или подменён")
                }
            }
            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) error("Не удалось сохранить APK")
            dest
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * Передаёт APK системному установщику. Сессия сама показывает диалог
     * подтверждения (STATUS_PENDING_USER_ACTION) — тихо установить нельзя.
     */
    fun install(context: Context, apk: File) {
        val manager = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply { setAppPackageName(context.packageName) }
        val sessionId = manager.createSession(params)
        try {
            manager.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java)
                    .putExtra(EXTRA_SESSION_ID, sessionId)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
        } catch (t: Throwable) {
            runCatching { manager.abandonSession(sessionId) }
            throw t
        }
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { DebugLog.error("update", it) }
                }
            }

            PackageInstaller.STATUS_SUCCESS ->
                DebugLog.event("update", "apk installed")

            else -> DebugLog.event(
                "update",
                "install status $status: " +
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
            )
        }
    }
}
