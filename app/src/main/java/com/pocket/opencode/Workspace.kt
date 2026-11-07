package com.pocket.opencode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Рабочий каталог агента. По умолчанию — видимая пользователю папка на
 * общем хранилище. На Android 11+ для неё нужен «Доступ ко всем файлам»,
 * на Android 8–10 — обычное разрешение WRITE_EXTERNAL_STORAGE. Без доступа
 * сервер уходит во внутреннюю папку приложения, но продолжает работать.
 */
object Workspace {
    const val DEFAULT_PATH = "/sdcard/OpenCode"

    fun defaultPath(): String = DEFAULT_PATH

    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasLegacyStorageAccess(context)
        }

    /** Android 8–10: WRITE_EXTERNAL_STORAGE + смонтированное хранилище. */
    fun hasLegacyStorageAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        val granted = context.checkSelfPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return granted && Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun needsLegacyStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !hasLegacyStorageAccess(context)

    fun ensure(dir: File): Boolean {
        if (dir.isDirectory) return true
        return runCatching { dir.mkdirs() }.getOrDefault(false) && dir.isDirectory
    }

    /**
     * canWrite() на FUSE может врать при отозванном доступе, поэтому
     * дополнительно пробуем настоящую запись и сразу удаляем пробный файл.
     */
    fun isUsable(dir: File): Boolean {
        if (!ensure(dir) || !dir.canWrite()) return false
        val probe = File(dir, ".pocket-write-probe")
        return runCatching {
            probe.writeText("pocket")
            probe.delete()
        }.isSuccess
    }

    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val app = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
            runCatching { context.startActivity(app) }
                .recoverCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
        } else {
            // На старых Android это обычное разрешение приложения.
            val details = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName)
            )
            runCatching { context.startActivity(details) }
                .recoverCatching {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
        }
    }
}
