package com.pocket.opencode

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DirectoryRewriter {
    fun rewrite(value: String, oldRoot: String, newRoot: String): String {
        val old = oldRoot.trimEnd('/')
        val new = newRoot.trimEnd('/')
        if (old.isEmpty()) return value
        if (value == old) return new
        if (!value.startsWith("$old/")) return value
        val rest = value.substring(old.length)
        val joined = new + rest
        return joined.replace(Regex("/{2,}"), "/").trimEnd('/').ifEmpty { "/" }
    }
}

object ArchiveLayout {
    private const val DATA_PREFIX = ".local/share/opencode/"
    private const val CONFIG_PREFIX = ".config/opencode/"

    fun destinationFor(entry: String, paths: ServerPaths): File? {
        val clean = entry.removePrefix("./").trimStart('/')
        return when {
            clean.startsWith(DATA_PREFIX) ->
                File(paths.data, "opencode/" + clean.removePrefix(DATA_PREFIX))
            clean.startsWith(CONFIG_PREFIX) ->
                File(paths.config, "opencode/" + clean.removePrefix(CONFIG_PREFIX))
            else -> null
        }
    }
}

data class MigrationPreview(
    val sessions: Int,
    val skills: Int,
    val credentials: Int,
    val problems: List<String>
)

data class MigrationResult(
    val sessions: Int,
    val skills: Int,
    val credentials: Int,
    val backupDir: File,
    val warnings: List<String>
)

object MigrationManager {
    private const val DB_NAME = "opencode.db"

    fun preview(archive: File, paths: ServerPaths): MigrationPreview {
        val problems = mutableListOf<String>()
        if (!archive.isFile) {
            return MigrationPreview(0, 0, 0, listOf("Архив не найден: ${archive.absolutePath}"))
        }
        val temp = File(paths.cache, "migration/preview-${System.currentTimeMillis()}")
        try {
            if (!extract(archive, temp)) {
                return MigrationPreview(0, 0, 0, listOf("Не удалось распаковать архив"))
            }
            val dbFile = temp.walkTopDown().firstOrNull { it.isFile && it.name == DB_NAME }
            if (dbFile == null) {
                problems += "В архиве нет базы opencode.db"
            }
            var sessions = 0
            var credentials = 0
            if (dbFile != null) {
                runCatching {
                    SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    ).use { db ->
                        sessions = count(db, "session_v2")
                        credentials = count(db, "credential")
                    }
                }.onFailure {
                    problems += "База не читается: ${it.message}"
                }
            }
            val skills = temp.walkTopDown().count { it.isFile && it.name == "SKILL.md" }
            return MigrationPreview(sessions, skills, credentials, problems)
        } finally {
            runCatching { temp.deleteRecursively() }
        }
    }

    fun import(
        context: Context,
        archive: File,
        paths: ServerPaths,
        workspace: File,
        oldRoot: String = "/data/data/com.termux/files/home"
    ): MigrationResult {
        require(archive.isFile) { "Архив не найден" }
        val warnings = mutableListOf<String>()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val backup = File(paths.root, "backup-$stamp")

        stopServerAndWait()
        if (paths.home.isDirectory) {
            paths.home.copyRecursively(backup, overwrite = true)
        }

        val temp = File(paths.cache, "migration/import-$stamp")
        try {
            require(extract(archive, temp)) { "Не удалось распаковать архив" }

            // Конфиг и скилы.
            temp.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(temp).path.replace('\\', '/')
                val destination = ArchiveLayout.destinationFor(relative, paths) ?: return@forEach
                if (destination.name == DB_NAME ||
                    destination.name == "$DB_NAME-wal" ||
                    destination.name == "$DB_NAME-shm"
                ) {
                    return@forEach
                }
                destination.parentFile?.mkdirs()
                if (destination.name in setOf(ConfigStore.FILE_NAME, ConfigStore.LEGACY_FILE_NAME) &&
                    destination.isFile
                ) {
                    runCatching {
                        destination.copyTo(
                            File(destination.parentFile, destination.name + ".bak"),
                            overwrite = true
                        )
                    }
                }
                runCatching { file.copyTo(destination, overwrite = true) }
                    .onFailure { warnings += "Не скопирован ${destination.name}: ${it.message}" }
            }

            // База: старые wal/shm удаляем, иначе SQLite подхватит чужие страницы.
            val dataDir = File(paths.data, "opencode")
            dataDir.mkdirs()
            val dbTarget = File(dataDir, DB_NAME)
            listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm").forEach {
                File(dataDir, it).delete()
            }
            val dbSource = temp.walkTopDown().firstOrNull { it.isFile && it.name == DB_NAME }
                ?: error("В архиве нет opencode.db")
            dbSource.copyTo(dbTarget, overwrite = true)
            listOf("$DB_NAME-wal", "$DB_NAME-shm").forEach { name ->
                val source = File(dbSource.parentFile, name)
                if (source.isFile) source.copyTo(File(dataDir, name), overwrite = true)
            }

            var sessions = 0
            var credentials = 0
            SQLiteDatabase.openDatabase(
                dbTarget.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                val newRoot = workspace.absolutePath.trimEnd('/')
                val roots = linkedSetOf(oldRoot.trimEnd('/'), oldRoot.trimEnd('/') + "/")
                updateDirectory(db, "session_v2", "directory", roots, newRoot)
                updateDirectory(db, "project", "worktree", roots, newRoot)
                updateDirectory(db, "project_directory", "directory", roots, newRoot)
                rewriteMessageData(db, oldRoot.trimEnd('/'), newRoot)
                sessions = count(db, "session_v2")
                credentials = count(db, "credential")
            }

            val skills = paths.config.walkTopDown().count { it.isFile && it.name == "SKILL.md" }
            return MigrationResult(sessions, skills, credentials, backup, warnings)
        } finally {
            runCatching { temp.deleteRecursively() }
        }
    }

    private fun stopServerAndWait() {
        EmbeddedServer.stop()
        val deadline = System.currentTimeMillis() + 10_000
        while (EmbeddedServer.status.value.state != EmbeddedState.STOPPED &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(100)
        }
    }

    private fun extract(archive: File, destination: File): Boolean {
        destination.mkdirs()
        return runCatching {
            val process = ProcessBuilder(
                "/system/bin/tar",
                "-xzf",
                archive.absolutePath,
                "-C",
                destination.absolutePath
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor()
            if (finished != 0 && output.isNotBlank()) {
                error(output.take(200))
            }
            finished == 0
        }.getOrDefault(false)
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        runCatching {
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }.getOrDefault(0)

    private fun updateDirectory(
        db: SQLiteDatabase,
        table: String,
        column: String,
        roots: Set<String>,
        newRoot: String
    ) {
        runCatching {
            roots.forEach { root ->
                db.execSQL(
                    "UPDATE $table SET $column=? WHERE $column=? OR $column LIKE ?",
                    arrayOf<Any>(newRoot, root, "$root/%")
                )
            }
        }
    }

    private fun rewriteMessageData(db: SQLiteDatabase, oldRoot: String, newRoot: String) {
        runCatching {
            db.execSQL(
                "UPDATE session_message SET data=replace(data, ?, ?) WHERE data LIKE ?",
                arrayOf<Any>(oldRoot, newRoot, "%$oldRoot%")
            )
        }
    }
}
