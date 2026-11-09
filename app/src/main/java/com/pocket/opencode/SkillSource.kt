package com.pocket.opencode

import java.net.URI
import java.net.URLDecoder

/**
 * Источник скила. Скилы в OpenCode — это каталоги с `SKILL.md`, поэтому
 * установщик принимает и репозиторий целиком, и отдельный скил внутри него.
 */
sealed class SkillSource {
    abstract val label: String

    data class Repo(
        val owner: String,
        val repo: String,
        val ref: String? = null,
        val skill: String? = null,
    ) : SkillSource() {
        override val label: String
            get() = buildString {
                append(owner).append('/').append(repo)
                if (!skill.isNullOrBlank()) append('@').append(skill)
            }
    }

    data class Raw(val url: String) : SkillSource() {
        override val label: String get() = url
    }

    data class SkillsSh(
        val owner: String,
        val repo: String,
        val skill: String,
    ) : SkillSource() {
        override val label: String get() = "$owner/$repo/$skill"
    }
}

/**
 * Разбирает то, что пользователь или агент реально пишет в запросе:
 * `owner/repo`, `owner/repo@skill`, id skills.sh, ссылку GitHub или прямой
 * `SKILL.md`. Всё остальное считается прямым URL и проверяется загрузчиком.
 */
object SkillSourceParser {
    private val namePart = Regex("[A-Za-z0-9_.-]+")

    fun parse(input: String): SkillSource? {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty()) return null
        val lower = text.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:")
        ) {
            return null
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) return parseUrl(text)
        if (text.contains("://")) return null
        return parseShorthand(text)
    }

    private fun parseUrl(text: String): SkillSource? {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val parts = uri.path.orEmpty()
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .map { decode(it) }

        return when {
            host == "skills.sh" && parts.size >= 3 ->
                SkillSource.SkillsSh(parts[0], parts[1], parts[2])

            host == "raw.githubusercontent.com" -> SkillSource.Raw(text)

            host == "github.com" || host == "www.github.com" -> parseGitHub(parts)

            host.endsWith("githubusercontent.com") -> SkillSource.Raw(text)

            else -> SkillSource.Raw(text)
        }
    }

    private fun parseGitHub(parts: List<String>): SkillSource? {
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1]
        if (!namePart.matches(owner) || !namePart.matches(repo)) return null
        if (parts.size == 2) return SkillSource.Repo(owner, repo)

        return when (parts[2]) {
            "tree" -> {
                val ref = parts.getOrNull(3)
                if (ref == null) return SkillSource.Repo(owner, repo)
                val path = parts.drop(4).joinToString("/").ifBlank { null }
                SkillSource.Repo(owner, repo, ref, path)
            }

            "blob" -> {
                val ref = parts.getOrNull(3)
                if (ref == null) return SkillSource.Repo(owner, repo)
                val path = parts.drop(4).joinToString("/")
                    .removeSuffix("/SKILL.md")
                    .ifBlank { null }
                SkillSource.Repo(owner, repo, ref, path)
            }

            "raw" -> {
                val ref = parts.getOrNull(3)
                if (ref == null) return SkillSource.Repo(owner, repo)
                val path = parts.drop(4).joinToString("/")
                if (path.isBlank()) {
                    null
                } else {
                    SkillSource.Raw("https://raw.githubusercontent.com/$owner/$repo/$ref/$path")
                }
            }

            else -> SkillSource.Repo(owner, repo)
        }
    }

    private fun parseShorthand(text: String): SkillSource? {
        val at = text.indexOf('@')
        val repoPart = if (at > 0) text.substring(0, at) else text
        val atSkill = if (at > 0) text.substring(at + 1).trim().ifBlank { null } else null
        val parts = repoPart.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1]
        if (!namePart.matches(owner) || !namePart.matches(repo)) return null
        val extra = parts.drop(2).joinToString("/").ifBlank { null }
        return SkillSource.Repo(owner, repo, null, atSkill ?: extra)
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
