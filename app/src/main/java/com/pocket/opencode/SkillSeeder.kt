package com.pocket.opencode

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Раскладывает скилы из assets в XDG-конфиг приложения. Копирует только
 * отсутствующие файлы: правки пользователя и скилы из миграции важнее
 * встроенной версии.
 */
object SkillSeeder {
    private const val ROOT = "skills"
    private const val MARKER = ".pocket-seeded"
    private const val COPY_THREADS = 4
    private const val COPY_BUFFER = 64 * 1024

    fun seed(context: Context, paths: ServerPaths): Int {
        val assets = context.assets
        val destination = File(File(paths.config, "opencode"), "skills")

        // Обход 651 файла через AssetManager стоит ~270 мс на каждом старте,
        // а после обновления APK — несколько секунд на копирование. Скилы
        // меняются только вместе с APK, поэтому после успешной раскладки
        // запоминаем версию установки и больше не трогаем каталог.
        val stamp = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        }.getOrNull()
        val marker = File(destination, MARKER)
        if (stamp != null && destination.isDirectory &&
            runCatching { marker.readText() }.getOrNull() == stamp
        ) {
            return 0
        }

        val files = mutableListOf<String>()

        fun collect(relative: String) {
            val assetPath = if (relative.isEmpty()) ROOT else "$ROOT/$relative"
            val children = runCatching { assets.list(assetPath) }.getOrNull()
            if (children.isNullOrEmpty()) {
                if (relative.isNotEmpty()) files += relative
                return
            }
            children.forEach { child ->
                collect(if (relative.isEmpty()) child else "$relative/$child")
            }
        }

        fun copy(relative: String): Boolean {
            val asset = SkillSeedPolicy.safeRelative(relative) ?: return false
            val restored = SkillSeedPolicy.targetRelative(asset) ?: return false
            val target = File(destination, restored)
            if (target.isFile) return false
            return runCatching {
                target.parentFile?.mkdirs()
                assets.open("$ROOT/$asset").use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, COPY_BUFFER)
                    }
                }
            }.isSuccess
        }

        // Копирование 11 МБ из APK упирается в открытие каждого asset.
        // Несколько потоков перекрывают декомпрессию и запись на диск.
        val copied = AtomicInteger()
        val collected = runCatching {
            collect("")
            true
        }.getOrDefault(false)
        if (!collected) return 0

        val pool = Executors.newFixedThreadPool(COPY_THREADS)
        try {
            files.forEach { relative ->
                pool.execute { if (copy(relative)) copied.incrementAndGet() }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(2, TimeUnit.MINUTES)
        }

        if (stamp != null) runCatching { marker.writeText(stamp) }
        return copied.get()
    }
}
