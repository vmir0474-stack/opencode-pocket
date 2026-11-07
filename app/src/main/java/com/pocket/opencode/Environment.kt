package com.pocket.opencode

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Реальные возможности устройства, на котором запущен сервер.
 *
 * В APK нет git/node/python и Debian-окружения: на телефоне без root они
 * недоступны. Вместо того чтобы притворяться, что они есть, приложение
 * проверяет PATH сервера, показывает результат в настройках и кладёт тот же
 * список в instructions для агента. Так агент не тратит ходы на команды,
 * которых нет, и сразу выбирает рабочий путь (rg, файловые операции,
 * удалённые MCP).
 */
data class EnvCommand(val name: String, val path: String)

data class EnvReport(
    val sdk: Int,
    val release: String,
    val abi: String,
    val available: List<EnvCommand>,
    val missing: List<String>,
    /** all_files — MANAGE_EXTERNAL_STORAGE, legacy — runtime permission до Android 11. */
    val storageMode: String,
    val batteryExempt: Boolean,
    val rootBinary: Boolean,
    val workspace: String,
    val serverPath: String,
) {
    val storageAccess: Boolean get() = storageMode != "none"
    val availableNames: List<String> get() = available.map { it.name }
}

object Environment {
    const val INSTRUCTIONS_FILE = "pocket-environment.md"

    private const val STORAGE_ALL_FILES = "all_files"
    private const val STORAGE_LEGACY = "legacy"
    private const val STORAGE_NONE = "none"

    /** Команды, которые реально встречаются в PATH встроенного сервера. */
    val PROBES = listOf(
        // Базовые shell-утилиты Android (toybox). Без них агенту не обойтись.
        "sh", "echo", "printf", "ls", "cat", "cp", "mv", "rm", "mkdir", "rmdir",
        "touch", "head", "tail", "wc", "sort", "uniq", "tr", "cut", "tee",
        "base64", "sha256sum", "md5sum", "date", "sleep", "chmod", "ln",
        "readlink", "realpath", "dirname", "basename", "stat", "du", "df",
        "ps", "kill", "id", "whoami", "uname", "env", "printenv", "which", "toybox",
        // Сеть и архивы.
        "curl", "wget", "tar", "unzip", "gzip", "zip", "ping",
        // Поиск и текст.
        "sed", "awk", "grep", "find", "xargs", "rg", "pocket-rs", "diff",
        // Инструменты разработки: на rootless-телефоне их обычно нет.
        "git", "node", "npm", "npx", "python", "python3", "pip", "pip3",
        "jq", "bash", "make", "gcc", "clang", "cc", "sqlite3", "openssl",
    )

    /** su может быть установлен, но приложение всё равно работает от uid. */
    private val ROOT_BINARIES = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
    )

    fun instructionsFile(paths: ServerPaths): File =
        File(paths.config, "opencode/$INSTRUCTIONS_FILE")

    fun configFile(paths: ServerPaths): File =
        File(paths.config, "opencode/${ConfigStore.FILE_NAME}")

    fun legacyConfigFile(paths: ServerPaths): File =
        File(paths.config, "opencode/${ConfigStore.LEGACY_FILE_NAME}")

    fun storageMode(context: Context): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            if (Workspace.hasAllFilesAccess(context)) STORAGE_ALL_FILES else STORAGE_NONE
        Workspace.hasAllFilesAccess(context) -> STORAGE_LEGACY
        else -> STORAGE_NONE
    }

    fun batteryExempt(context: Context): Boolean = runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        power.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    fun hasRootBinary(): Boolean = ROOT_BINARIES.any { runCatching { File(it).exists() }.getOrDefault(false) }

    /**
     * Проверяет только то, что увидит сам сервер: его PATH, а не PATH
     * Termux. Поэтому rootless-телефон честно показывает отсутствие git.
     */
    fun report(context: Context, workspace: File, pathDirs: List<File>): EnvReport {
        val found = LinkedHashMap<String, String>()
        PROBES.forEach { name ->
            val hit = pathDirs.asSequence()
                .map { File(it, name) }
                .firstOrNull { runCatching { it.isFile && it.canExecute() }.getOrDefault(false) }
            if (hit != null) found[name] = hit.absolutePath
        }
        return EnvReport(
            sdk = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE.orEmpty(),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            available = found.map { EnvCommand(it.key, it.value) },
            missing = PROBES.filter { it !in found },
            storageMode = storageMode(context),
            batteryExempt = batteryExempt(context),
            rootBinary = hasRootBinary(),
            workspace = workspace.absolutePath,
            serverPath = pathDirs.joinToString(":") { it.absolutePath },
        )
    }

    /** Текст, который агент читает как дополнительные инструкции. */
    fun instructions(report: EnvReport): String = buildString {
        appendLine("# Окружение OpenCode Pocket")
        appendLine()
        appendLine("Файл создан приложением автоматически при запуске сервера.")
        appendLine("Он описывает только то, что реально доступно в PATH сервера.")
        appendLine()
        appendLine("## Устройство")
        appendLine("- Android ${report.release.ifBlank { "?" }} (API ${report.sdk}), ${report.abi.ifBlank { "ABI ?" }}.")
        appendLine("- Рабочий каталог сервера (по умолчанию для новых сессий): `${report.workspace}`.")
        appendLine("- PATH сервера: `${report.serverPath}`.")
        appendLine("- Текущая сессия могла быть создана раньше в другом каталоге. Всегда проверяй `pwd`; путь выше — не гарантия cwd этой сессии.")
        appendLine(
            when (report.storageMode) {
                STORAGE_ALL_FILES -> "- Общее хранилище: доступ ко всем файлам выдан."
                STORAGE_LEGACY -> "- Общее хранилище: доступ выдан через разрешение Android (до API 30)."
                else -> "- Общее хранилище: доступа нет, работай только в рабочем каталоге."
            }
        )
        appendLine(
            if (report.batteryExempt) {
                "- Батарея: приложение исключено из оптимизации."
            } else {
                "- Батарея: оптимизация включена, в фоне Android может ограничивать сеть."
            }
        )
        if (report.rootBinary) {
            appendLine("- Бинарник `su` в системе есть, но сервер запущен от обычного uid приложения, не от root.")
        } else {
            appendLine("- `su` не найден. Root, Debian chroot, apt и systemd недоступны; они и не нужны для работы приложения.")
        }
        appendLine()
        appendLine("## Команды")
        appendLine(
            if (report.availableNames.isEmpty()) {
                "- В PATH не найдено ни одной из проверенных команд."
            } else {
                "- Доступно: ${report.availableNames.joinToString(", ")}."
            }
        )
        if (report.missing.isNotEmpty()) {
            appendLine("- Недоступно: ${report.missing.joinToString(", ")}.")
        }
        val builtins = listOf("rg", "pocket-rs").filter { it in report.availableNames }
        appendLine(
            if (builtins.isEmpty()) {
                "- Встроенные инструменты `rg` и `pocket-rs` не найдены в PATH."
            } else {
                "- Встроенные инструменты: ${builtins.joinToString(" и ") { "`$it`" }} уже в PATH."
            }
        )
        appendLine()
        appendLine("## Поиск в интернете")
        appendLine("- Встроенный `websearch` может вернуть HTTP 403 без VPN: поисковые провайдеры (Tavily, Exa, Parallel) блокируют часть регионов и IP. Это не поломка сети приложения: `webfetch` и `curl` при этом работают.")
        appendLine("- Если `websearch` недоступен, не сдавайся: открой `webfetch` на `https://html.duckduckgo.com/html/?q=ЗАПРОС` (пробелы замени на `+`). DuckDuckGo отдаёт обычный HTML, ссылки находятся в блоках `result__a`, описания — в `result__snippet`.")
        appendLine("- Ключ Tavily можно добавить в разделе «Модели» (поиск: tavily). Он может обойти лимит keyless-режима, но при блокировке по IP всё равно нужен VPN. Без ключа и без VPN используй резервный способ выше.")
        appendLine()
        appendLine("## Как работать")
        appendLine("- В списке «Доступно» — всё, что реально нашлось в PATH. Shell-встроенные команды (`cd`, `export`, `if`, `for`, `echo`, `printf`, `test`) доступны всегда.")
        appendLine("- Если команды нет в списке — не вызывай её и не пытайся устанавливать пакеты.")
        appendLine("- Внешние окружения (Termux, Debian) используй только если пользователь сам о них сказал; сервер их не подключает.")
        if ("git" !in report.availableNames) {
            if (report.rootBinary) {
                appendLine("- `git` нет в PATH сервера. Если пользователь явно настроил Termux/Debian, бери git оттуда; сам ничего не устанавливай.")
            } else {
                appendLine("- Нет `git` в PATH: не предлагай git-операции, работай с файлами напрямую.")
            }
        }
        val scriptMissing = listOf("node", "python", "python3").none { it in report.availableNames }
        if (scriptMissing) {
            if (report.rootBinary) {
                appendLine("- `node`/`npm`/`python` нет в PATH сервера. Локальные stdio MCP запустятся только из внешнего окружения, если пользователь его настроил; иначе используй remote MCP.")
            } else {
                appendLine("- Нет `node`/`npm`/`python` в PATH: локальные stdio MCP и скрипты на этих языках не запустятся. Используй remote MCP и shell-утилиты.")
            }
        }
        if ("rg" in report.availableNames) {
            appendLine("- Для поиска бери `rg`; для diff и подсветки — `pocket-rs`.")
        } else if ("grep" in report.availableNames) {
            appendLine("- `rg` не найден: для поиска используй `grep -R` и `find`.")
        }
        appendLine("- Если задача требует отсутствующий инструмент, скажи об этом прямо и предложи доступный путь.")
    }

    /**
     * Добавляет путь к instructions, не затирая инструкции пользователя.
     * Возвращает null, если конфиг не JSON-объект или instructions имеет
     * неожиданный тип: в этом случае лучше ничего не менять.
     */
    fun mergeInstructions(configText: String, path: String): String? {
        val root = runCatching {
            if (configText.isBlank()) JSONObject() else JSONObject(configText)
        }.getOrNull() ?: return null
        val current = root.opt("instructions")
        val array = when (current) {
            null, JSONObject.NULL -> JSONArray()
            is JSONArray -> current
            is String -> JSONArray().put(current)
            else -> return null
        }
        if ((0 until array.length()).none { array.optString(it) == path }) {
            array.put(path)
        }
        root.put("instructions", array)
        return root.toString(2)
    }

    fun instructionsRegistered(configText: String, path: String): Boolean {
        val root = runCatching {
            if (configText.isBlank()) JSONObject() else JSONObject(configText)
        }.getOrNull() ?: return false
        val array = root.opt("instructions") as? JSONArray ?: return false
        return (0 until array.length()).any { array.optString(it) == path }
    }

    /**
     * Готовит instructions до старта сервера. Если opencode.json ещё нет,
     * берём старый config.json, чтобы не потерять настройки пользователя.
     */
    fun prepare(context: Context, paths: ServerPaths, workspace: File): EnvReport {
        val tools = Toolchain.toolsDir(paths)
        val dirs = listOf(tools, File("/system/bin"), File("/system/xbin"))
        val report = report(context, workspace, dirs)

        val note = instructionsFile(paths)
        val text = instructions(report)
        writeIfChanged(note, text)

        val config = configFile(paths)
        val source = when {
            config.isFile -> config
            legacyConfigFile(paths).isFile -> legacyConfigFile(paths)
            else -> null
        }
        val current = source?.let { runCatching { it.readText() }.getOrNull() }.orEmpty()
        // Если путь уже прописан, не переформатируем пользовательский JSON.
        if (!instructionsRegistered(current, note.absolutePath)) {
            val merged = mergeInstructions(current, note.absolutePath)
            if (merged != null) {
                runCatching { ConfigStore.write(config, merged) }
                    .onFailure { DebugLog.error("environment", it) }
            } else {
                // Не ломаем JSONC/нестандартный конфиг: просто сообщаем в журнал.
                DebugLog.event("environment", "config merge skipped", mapOf("config" to config.absolutePath))
            }
        }
        return report
    }

    private fun writeIfChanged(file: File, text: String) {
        if (file.isFile && runCatching { file.readText() == text }.getOrDefault(false)) return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }.onFailure { DebugLog.error("environment", it) }
    }

    fun openBatterySettings(context: Context) {
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName)
        )
        // На MIUI/HiOS на этот intent откликаются несколько системных
        // экранов, и Android показывает «чем открыть». Если среди них есть
        // штатный RequestIgnoreBatteryOptimizations, открываем его напрямую:
        // это тот самый диалог «Разрешить работу в фоне?».
        val direct = runCatching {
            context.packageManager.queryIntentActivities(request, 0)
                .firstOrNull { it.activityInfo.name.endsWith("RequestIgnoreBatteryOptimizations") }
                ?.let {
                    Intent(request).setComponent(
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    )
                }
        }.getOrNull()
        runCatching { context.startActivity(direct ?: request) }
            .recoverCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
    }
}
