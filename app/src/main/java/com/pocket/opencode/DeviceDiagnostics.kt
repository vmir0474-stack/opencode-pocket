package com.pocket.opencode

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Факты об устройстве для диагностики: без догадок и без root.
 * Размер страницы важен для proot: на 16 КБ-страницах старые сборки
 * могут не запуститься, и это нужно видеть сразу.
 */
object DeviceDiagnostics {
    data class Info(
        val android: String,
        val sdk: Int,
        val abi: String,
        val pageSize: Long,
        val model: String,
        val freeBytes: Long,
        val rootfs: String,
        val proot: String,
    )

    fun collect(context: Context): Info {
        val free = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }
            .getOrDefault(0L)
        val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(0L)
        val bin = DevEnvironment.binDir(context)
        val proot = File(bin, "proot")
        val loader = File(bin, "loader")
        val rootfs = File(DevEnvironment.rootfs(context), "etc/os-release")
        return Info(
            android = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
            pageSize = pageSize,
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            freeBytes = free,
            rootfs = if (rootfs.isFile) DevEnvironment.state.value.version.ifBlank { "installed" } else "not installed",
            proot = when {
                !proot.canExecute() -> "missing"
                !loader.canExecute() -> "missing loader"
                else -> "ready"
            },
        )
    }
}
