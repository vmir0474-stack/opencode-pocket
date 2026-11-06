package com.pocket.opencode

import java.io.File
import java.util.Locale

/**
 * Рендер и проверка прав на файлы скилов. Скил — это SKILL.md с
 * frontmatter и markdown-телом.
 */
object SkillWriter {
    fun render(name: String, description: String, body: String): String {
        val safeDescription = description.trim().replace("\n", " ")
        val trimmedBody = body.trim()
        val builder = StringBuilder()
        builder.append("---\n")
        builder.append("name: ").append(name.trim()).append('\n')
        builder.append("description: ").append(safeDescription).append('\n')
        builder.append("---\n\n")
        if (trimmedBody.isNotEmpty()) builder.append(trimmedBody).append('\n')
        return builder.toString()
    }

    fun safeName(name: String): String {
        val dashed = name.trim().lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return dashed.ifBlank { "skill" }
    }

    fun isWritable(path: String, configHome: File, workspace: File): Boolean {
        if (path.isBlank()) return false
        val target = File(path).absolutePath
        val configSkills = File(configHome, "opencode/skills").absolutePath
        val projectSkills = File(workspace, ".opencode/skills").absolutePath
        return target.startsWith(configSkills + File.separator) ||
            target.startsWith(projectSkills + File.separator)
    }
}
