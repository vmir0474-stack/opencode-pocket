package com.pocket.opencode

import android.content.Context
import java.io.File

/**
 * Раскладывает скилы из assets в XDG-конфиг приложения. Копирует только
 * отсутствующие файлы: правки пользователя и скилы из миграции важнее
 * встроенной версии.
 */
object SkillSeeder {
    private const val ROOT = "skills"

    fun seed(context: Context, paths: ServerPaths): Int {
        val assets = context.assets
        val destination = File(File(paths.config, "opencode"), "skills")
        var copied = 0

        fun copyFile(relative: String) {
            val asset = SkillSeedPolicy.safeRelative(relative) ?: return
            val restored = SkillSeedPolicy.targetRelative(asset) ?: return
            val target = File(destination, restored)
            if (target.isFile) return
            runCatching {
                target.parentFile?.mkdirs()
                assets.open("$ROOT/$asset").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onSuccess { copied++ }
        }

        fun walk(relative: String) {
            val assetPath = if (relative.isEmpty()) ROOT else "$ROOT/$relative"
            val children = runCatching { assets.list(assetPath) }.getOrNull()
            if (children.isNullOrEmpty()) {
                if (relative.isNotEmpty()) copyFile(relative)
                return
            }
            children.forEach { child ->
                walk(if (relative.isEmpty()) child else "$relative/$child")
            }
        }

        runCatching { walk("") }
        return copied
    }
}
