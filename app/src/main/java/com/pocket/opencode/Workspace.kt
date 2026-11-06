package com.pocket.opencode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Рабочий каталог агента. По умолчанию — видимая пользователю папка на
 * общем хранилище. Без «Доступа ко всем файлам» Android не даёт писать в
 * /sdcard, поэтому сервер уходит во внутреннюю папку приложения.
 */
object Workspace {
    const val DEFAULT_PATH = "/sdcard/OpenCode"

    fun defaultPath(): String = DEFAULT_PATH

    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    fun ensure(dir: File): Boolean {
        if (dir.isDirectory) return true
        return runCatching { dir.mkdirs() }.getOrDefault(false) && dir.isDirectory
    }

    fun isUsable(dir: File): Boolean = ensure(dir) && dir.canWrite()

    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
        runCatching { context.startActivity(app) }
            .recoverCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
    }
}
