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
 * общем хранилище. Приложение собирается с targetSdk 28, поэтому на всех
 * версиях Android работает legacy-режим хранилища: доступ к /sdcard даёт
 * обычное разрешение WRITE_EXTERNAL_STORAGE. Без доступа сервер уходит во
 * внутреннюю папку приложения, но продолжает работать.
 */
object Workspace {
    const val DEFAULT_PATH = "/sdcard/OpenCode"

    fun defaultPath(): String = DEFAULT_PATH

    /**
     * Приложение с targetSdk < 30 использует legacy-хранилище даже на
     * Android 11+. Для такого режима достаточно runtime-разрешения, а
     * MANAGE_EXTERNAL_STORAGE не выдаётся и не нужен.
     */
    fun usesLegacyStorage(context: Context): Boolean =
        context.applicationInfo.targetSdkVersion < Build.VERSION_CODES.R

    fun hasAllFilesAccess(context: Context): Boolean =
        if (usesLegacyStorage(context)) {
            hasLegacyStorageAccess(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasLegacyStorageAccess(context)
        }

    /** WRITE_EXTERNAL_STORAGE + смонтированное хранилище (API 26+). */
    fun hasLegacyStorageAccess(context: Context): Boolean {
        val granted = context.checkSelfPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return granted && Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun needsLegacyStoragePermission(context: Context): Boolean =
        usesLegacyStorage(context) && !hasLegacyStorageAccess(context)

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

    /**
     * Открывает системный экран выдачи «Доступа ко всем файлам».
     *
     * Важно: контекст здесь обычно Application, а startActivity из него
     * требует FLAG_ACTIVITY_NEW_TASK — без флага Android бросает исключение,
     * и кнопка молча ничего не делает. Поэтому флаг ставим всегда, а если
     * экран не нашёлся (OEM его прячет), пробуем следующие варианты.
     *
     * @return true, если удалось открыть хоть один системный экран.
     */
    fun openAllFilesSettings(context: Context): Boolean {
        // В legacy-режиме «Доступ ко всем файлам» не существует: нужное
        // разрешение выдаётся на карточке приложения.
        if (usesLegacyStorage(context)) return openAppDetails(context)
        val packageUri = Uri.parse("package:" + context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val candidates = listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
                Intent(Settings.ACTION_SETTINGS),
            )
            return openFirst(context, candidates)
        }
        // На старых Android это обычное разрешение приложения.
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_SETTINGS),
        )
        return openFirst(context, candidates)
    }

    /** Карточка приложения в настройках: там же видно выданные разрешения. */
    fun openAppDetails(context: Context): Boolean =
        openFirst(
            context,
            listOf(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                ),
                Intent(Settings.ACTION_SETTINGS),
            ),
        )

    private fun openFirst(context: Context, intents: List<Intent>): Boolean =
        intents.any { intent ->
            runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
}
