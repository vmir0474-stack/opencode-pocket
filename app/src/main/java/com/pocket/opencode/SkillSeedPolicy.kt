package com.pocket.opencode

/**
 * Чистые правила досева встроенных скилов: существующие файлы не трогаем,
 * небезопасные пути из assets отбрасываем.
 */
object SkillSeedPolicy {
    fun needed(assets: Set<String>, existing: Set<String>): Set<String> =
        assets.filterNot { it in existing }.toSet()

    /**
     * Android asset packaging игнорирует каталоги с ведущим подчёркиванием и
     * распаковывает `.gz`, поэтому в APK лежат `skill-sources` и файлы с
     * суффиксом `.asset`. Здесь возвращаем исходные имена Termux.
     */
    fun targetRelative(assetPath: String): String? {
        val safe = safeRelative(assetPath) ?: return null
        val restored = if (safe.endsWith(".asset")) safe.removeSuffix(".asset") else safe
        return when {
            restored == "skill-sources" -> "_sources"
            restored.startsWith("skill-sources/") ->
                "_sources/" + restored.removePrefix("skill-sources/")
            else -> restored
        }
    }

    fun safeRelative(path: String): String? {
        var clean = path.replace('\\', '/').trim().trimStart('/')
        while (clean.startsWith("./")) clean = clean.removePrefix("./")
        if (clean.isEmpty()) return null
        val parts = clean.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return clean
    }
}
