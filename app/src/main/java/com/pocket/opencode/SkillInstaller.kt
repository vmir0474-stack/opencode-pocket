package com.pocket.opencode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Разобранный frontmatter `SKILL.md`. */
data class SkillMeta(val name: String, val description: String)

object SkillFrontmatter {
    fun parse(text: String): SkillMeta? {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("---")) return null
        val end = trimmed.indexOf("\n---", 3)
        if (end < 0) return null
        val block = trimmed.substring(3, end)
        var name: String? = null
        var description: String? = null
        for (line in block.lineSequence()) {
            val value = line.trim()
            when {
                value.startsWith("name:") ->
                    name = value.substringAfter("name:").trim().trim('"', '\'')

                value.startsWith("description:") ->
                    description = value.substringAfter("description:").trim().trim('"', '\'')
            }
        }
        if (name.isNullOrBlank() || description.isNullOrBlank()) return null
        return SkillMeta(name, description)
    }
}

data class SkillCandidate(
    val id: String,
    val name: String,
    val source: String,
    val installs: Int,
)

data class InstalledSkill(
    val name: String,
    val path: String,
    val source: String,
    val files: Int,
)

interface SkillFetcher {
    suspend fun bytes(url: String, maxBytes: Long): ByteArray
    suspend fun download(url: String, dest: File, maxBytes: Long): Long
}

class OkHttpSkillFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) : SkillFetcher {
    override suspend fun bytes(url: String, maxBytes: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OpenCode-Pocket")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} для $url")
                val body = response.body ?: error("Пустой ответ $url")
                if (body.contentLength() > maxBytes) {
                    error("Ответ больше ${maxBytes / 1024 / 1024} МБ")
                }
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) {
                            error("Ответ больше ${maxBytes / 1024 / 1024} МБ")
                        }
                        out.write(buffer, 0, read)
                    }
                }
                out.toByteArray()
            }
        }

    override suspend fun download(url: String, dest: File, maxBytes: Long): Long =
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OpenCode-Pocket")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} для $url")
                val body = response.body ?: error("Пустой ответ $url")
                if (body.contentLength() > maxBytes) {
                    error("Архив больше ${maxBytes / 1024 / 1024} МБ")
                }
                var total = 0L
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) {
                                error("Архив больше ${maxBytes / 1024 / 1024} МБ")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                }
                total
            }
        }
}

/**
 * Установщик скилов. Кладёт файлы ровно туда, откуда OpenCode их читает:
 * `<config>/opencode/skills/<name>/SKILL.md` или
 * `<workspace>/.opencode/skills/<name>/SKILL.md`. Рядом пишет
 * `.pocket-skill.json`, чтобы скил можно было обновить или удалить.
 */
class SkillInstaller(
    private val fetcher: SkillFetcher = OkHttpSkillFetcher(),
    private val api: String = "https://api.github.com",
) {
    private data class Provenance(
        val source: String,
        val skill: String?,
        val ref: String?,
        val project: Boolean,
        val files: Int,
    )

    companion object {
        private const val MAX_ARCHIVE = 40L * 1024 * 1024
        private const val MAX_RAW = 2L * 1024 * 1024
        private const val MAX_SKILLS_PER_REPO = 20
        private const val META_FILE = ".pocket-skill.json"
    }

    suspend fun search(query: String): List<SkillCandidate> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val out = LinkedHashMap<String, SkillCandidate>()

        runCatching {
            val raw = fetcher.bytes("https://skills.sh/api/search?q=$encoded", 2L * 1024 * 1024)
            val array = JSONObject(String(raw, Charsets.UTF_8)).optJSONArray("skills") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                out[id] = SkillCandidate(
                    id = id,
                    name = item.optString("name", item.optString("skillId")),
                    source = item.optString("source"),
                    installs = item.optInt("installs"),
                )
            }
        }

        if (out.isEmpty()) {
            runCatching {
                val raw = fetcher.bytes(
                    "$api/search/repositories?q=$encoded+topic:agent-skills&per_page=10",
                    2L * 1024 * 1024,
                )
                val array = JSONObject(String(raw, Charsets.UTF_8)).optJSONArray("items") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val full = item.optString("full_name")
                    if (full.isBlank()) continue
                    out[full] = SkillCandidate(
                        id = full,
                        name = full,
                        source = full,
                        installs = item.optInt("stargazers_count"),
                    )
                }
            }
        }

        return out.values.toList()
    }

    suspend fun install(
        source: String,
        paths: ServerPaths,
        project: Boolean = false,
        overwrite: Boolean = false,
    ): List<InstalledSkill> {
        val parsed = SkillSourceParser.parse(source) ?: error("Не понял источник: $source")
        val root = skillsRoot(paths, project)
        root.mkdirs()
        return when (parsed) {
            is SkillSource.Raw -> installRaw(parsed, root, overwrite)
            is SkillSource.SkillsSh ->
                installRepo(
                    parsed.owner, parsed.repo, null, parsed.skill,
                    root, overwrite, project,
                )

            is SkillSource.Repo ->
                installRepo(
                    parsed.owner, parsed.repo, parsed.ref, parsed.skill,
                    root, overwrite, project,
                )
        }
    }

    private suspend fun installRaw(
        source: SkillSource.Raw,
        root: File,
        overwrite: Boolean,
    ): List<InstalledSkill> {
        val text = String(fetcher.bytes(source.url, MAX_RAW), Charsets.UTF_8)
        val meta = SkillFrontmatter.parse(text) ?: error("В SKILL.md нет name и description")
        val name = SkillWriter.safeName(meta.name)
        val dest = File(root, name)
        if (dest.exists() && !overwrite) error("Скил «${meta.name}» уже установлен")

        val temp = File(root, ".install-$name-${System.nanoTime()}")
        try {
            temp.mkdirs()
            File(temp, "SKILL.md").writeText(text)
            copySkill(temp, dest, overwrite)
            writeProvenance(dest, source.url, null, null, false, 1)
            return listOf(
                InstalledSkill(meta.name, File(dest, "SKILL.md").absolutePath, source.url, 1)
            )
        } finally {
            temp.deleteRecursively()
        }
    }

    private suspend fun installRepo(
        owner: String,
        repo: String,
        ref: String?,
        skill: String?,
        root: File,
        overwrite: Boolean,
        project: Boolean,
    ): List<InstalledSkill> {
        val branch = ref ?: defaultBranch(owner, repo)
        val archive = File(root, ".download-$owner-$repo-${System.nanoTime()}.tar.gz")
        val work = File(root, ".extract-$owner-$repo-${System.nanoTime()}")
        try {
            downloadArchive(owner, repo, branch, ref == null, archive)

            val entries = archive.inputStream().use { SkillArchive.entries(it) }
            val allDirs = SkillArchive.allSkillDirs(entries)
            if (allDirs.isEmpty()) error("В репозитории $owner/$repo нет SKILL.md")

            var selected = SkillArchive.skillDirs(entries, skill)
            if (selected.isEmpty()) {
                // Имя может быть frontmatter-именем, а не именем каталога.
                // Распаковываем кандидатов и сверяем после разбора.
                selected = allDirs.take(MAX_SKILLS_PER_REPO)
            }

            work.mkdirs()
            SkillArchive.extract(archive.inputStream(), work, selected.toSet())

            if (!skill.isNullOrBlank()) {
                val wanted = skill.trim().trim('/')
                val matched = selected.filter { dir ->
                    val meta = runCatching {
                        SkillFrontmatter.parse(File(File(work, dir), "SKILL.md").readText())
                    }.getOrNull()
                    dir == wanted ||
                        dir.endsWith("/$wanted") ||
                        dir.substringAfterLast('/') == wanted ||
                        meta?.name?.equals(wanted, ignoreCase = true) == true ||
                        meta?.name?.let { SkillWriter.safeName(it) } == wanted
                }
                if (matched.isEmpty()) {
                    error("Скил «$wanted» не найден в $owner/$repo")
                }
                selected = matched
            }

            val installed = mutableListOf<InstalledSkill>()
            for (dir in selected.take(MAX_SKILLS_PER_REPO)) {
                val sourceDir = File(work, dir)
                val skillFile = File(sourceDir, "SKILL.md")
                if (!skillFile.isFile) continue
                val meta = SkillFrontmatter.parse(skillFile.readText())
                    ?: error("В $dir/SKILL.md нет name и description")
                val name = SkillWriter.safeName(meta.name)
                val dest = File(root, name)
                if (dest.exists() && !overwrite) {
                    error(
                        "Скил «${meta.name}» уже установлен. " +
                            "Добавь --overwrite, чтобы заменить"
                    )
                }
                copySkill(sourceDir, dest, overwrite)
                val files = dest.walkTopDown().count { it.isFile }
                writeProvenance(
                    dest = dest,
                    source = "$owner/$repo",
                    skill = meta.name,
                    ref = branch,
                    project = project,
                    files = files,
                )
                installed += InstalledSkill(
                    meta.name,
                    File(dest, "SKILL.md").absolutePath,
                    "$owner/$repo",
                    files,
                )
            }
            if (installed.isEmpty()) error("Не удалось установить ни один скил")
            return installed
        } finally {
            archive.delete()
            work.deleteRecursively()
        }
    }

    private suspend fun downloadArchive(
        owner: String,
        repo: String,
        branch: String,
        allowMasterFallback: Boolean,
        dest: File,
    ) {
        val url = "https://codeload.github.com/$owner/$repo/tar.gz/$branch"
        val first = runCatching { fetcher.download(url, dest, MAX_ARCHIVE) }
        if (first.isSuccess) return
        if (allowMasterFallback && branch != "master") {
            fetcher.download(
                "https://codeload.github.com/$owner/$repo/tar.gz/master",
                dest,
                MAX_ARCHIVE,
            )
            return
        }
        throw first.exceptionOrNull() ?: IllegalStateException("Не удалось скачать архив")
    }

    private suspend fun defaultBranch(owner: String, repo: String): String {
        val raw = runCatching {
            String(fetcher.bytes("$api/repos/$owner/$repo", 512L * 1024), Charsets.UTF_8)
        }.getOrNull()
        val branch = raw?.let {
            runCatching { JSONObject(it).optString("default_branch") }.getOrNull()
        }
        return branch?.takeIf { it.isNotBlank() } ?: "main"
    }

    private fun copySkill(source: File, dest: File, overwrite: Boolean) {
        if (dest.exists()) {
            if (!overwrite) error("Скил уже установлен: ${dest.name}")
            dest.deleteRecursively()
        }
        dest.mkdirs()
        source.copyRecursively(dest, overwrite = true)
        // Скрипты из чужого скила не должны быть исполняемыми по умолчанию.
        dest.walkTopDown().forEach { file ->
            if (file.isFile) file.setExecutable(false, false)
        }
    }

    private fun writeProvenance(
        dest: File,
        source: String,
        skill: String?,
        ref: String?,
        project: Boolean,
        files: Int,
    ) {
        val json = JSONObject()
            .put("source", source)
            .put("skill", skill ?: "")
            .put("ref", ref ?: "")
            .put("project", project)
            .put("installedAt", System.currentTimeMillis())
            .put("files", files)
        File(dest, META_FILE).writeText(json.toString(2))
    }

    private fun readProvenance(dir: File): Provenance? {
        val meta = File(dir, META_FILE)
        if (!meta.isFile) return null
        val json = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return null
        return Provenance(
            source = json.optString("source"),
            skill = json.optString("skill").ifBlank { null },
            ref = json.optString("ref").ifBlank { null },
            project = json.optBoolean("project"),
            files = json.optInt("files"),
        )
    }

    fun list(paths: ServerPaths): List<InstalledSkill> {
        val out = mutableListOf<InstalledSkill>()
        for (root in listOf(globalRoot(paths), projectRoot(paths))) {
            val dirs = root.listFiles()?.filter { it.isDirectory } ?: continue
            for (dir in dirs) {
                val provenance = readProvenance(dir) ?: continue
                val label = if (provenance.skill != null) {
                    "${provenance.source}@${provenance.skill}"
                } else {
                    provenance.source
                }
                out += InstalledSkill(
                    name = dir.name,
                    path = File(dir, "SKILL.md").absolutePath,
                    source = label,
                    files = provenance.files,
                )
            }
        }
        return out
    }

    suspend fun update(
        paths: ServerPaths,
        name: String?,
        all: Boolean,
    ): List<InstalledSkill> {
        val targets = mutableListOf<Triple<File, Provenance, Boolean>>()
        for ((root, project) in listOf(globalRoot(paths) to false, projectRoot(paths) to true)) {
            val dirs = root.listFiles()?.filter { it.isDirectory } ?: continue
            for (dir in dirs) {
                val provenance = readProvenance(dir) ?: continue
                if (!all && dir.name != SkillWriter.safeName(name.orEmpty())) continue
                targets += Triple(dir, provenance, project)
            }
        }
        if (targets.isEmpty()) error("Нет установленных скилов для обновления")

        val out = mutableListOf<InstalledSkill>()
        for ((_, provenance, project) in targets) {
            val target = if (provenance.skill != null) {
                "${provenance.source}@${provenance.skill}"
            } else {
                provenance.source
            }
            out += install(target, paths, project = project, overwrite = true)
        }
        return out
    }

    fun remove(paths: ServerPaths, name: String): Boolean {
        val safe = SkillWriter.safeName(name)
        for (root in listOf(globalRoot(paths), projectRoot(paths))) {
            val dir = File(root, safe)
            if (!dir.isDirectory) continue
            if (!File(dir, META_FILE).isFile) {
                error("«$name» установлен не через pocket-skill — удали его вручную")
            }
            dir.deleteRecursively()
            return true
        }
        return false
    }

    private fun globalRoot(paths: ServerPaths): File =
        File(File(paths.config, "opencode"), "skills")

    private fun projectRoot(paths: ServerPaths): File =
        File(File(paths.workspace, ".opencode"), "skills")

    private fun skillsRoot(paths: ServerPaths, project: Boolean): File =
        if (project) projectRoot(paths) else globalRoot(paths)
}
