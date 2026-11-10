package com.pocket.opencode

import android.content.Context
import android.os.StatFs
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Pocket Dev: proot и Linux-rootfs внутри приватного каталога приложения.
 *
 * Приложение собирается с targetSdk 28, поэтому Android разрешает запускать
 * ELF из app-data. Rootfs ставится в files/dev/rootfs, proot, его
 * библиотеки и ELF-загрузчик — в files/dev/bin. Команда `dev` попадает
 * в PATH сервера.
 */
object DevEnvironment {
    const val UBUNTU_VERSION = "24.04.5"
    const val ARCHIVE_NAME = "ubuntu-base-$UBUNTU_VERSION-base-arm64.tar.gz"
    const val ARCHIVE_URL =
        "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$ARCHIVE_NAME"
    const val ARCHIVE_SHA256 =
        "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"
    const val ARCHIVE_SIZE = 29_936_675L

    /** Минимум свободного места перед установкой: архив + распаковка + запас. */
    private const val MIN_FREE_BYTES = 512L * 1024 * 1024
    private const val COPY_BUFFER = 64 * 1024

    data class DevState(
        val toolsReady: Boolean = false,
        val installed: Boolean = false,
        val busy: Boolean = false,
        val phase: String = "",
        val progress: Float = 0f,
        val entries: Int = 0,
        val version: String = "",
        val rootfs: String = "",
        val freeBytes: Long = 0,
        val error: String = "",
        val test: String = "",
        val checked: Boolean = false,
        val broken: Boolean = false,
    )

    /** Результат одной проверки внутри гостя. */
    internal data class Probe(val name: String, val status: String, val detail: String)

    internal data class CheckReport(val probes: List<Probe>) {
        /** `fail` — команда есть, но не запускается; `absent` — просто не установлена. */
        val broken: Boolean get() = probes.any { it.status.startsWith("fail") }
    }

    private val _state = MutableStateFlow(DevState())
    val state: StateFlow<DevState> = _state.asStateFlow()

    fun installDir(context: Context): File = File(context.filesDir, "dev")
    fun binDir(context: Context): File = File(installDir(context), "bin")
    fun rootfs(context: Context): File = File(installDir(context), "rootfs")
    fun toolsDir(context: Context): File =
        File(File(context.filesDir, "opencode"), "tools")
    fun wrapper(context: Context): File = File(toolsDir(context), "dev")

    /**
     * Раскладывает proot и библиотеки из assets и обновляет обёртку `dev`.
     * Файлы меняются только с APK, поэтому повторная распаковка кэшируется
     * по времени установки.
     */
    fun ensureTools(context: Context, paths: ServerPaths? = null): Boolean {
        val bin = binDir(context)
        val tools = paths?.let { File(it.root, "tools") } ?: toolsDir(context)
        val stamp = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        }.getOrNull()
        val marker = File(bin, ".tools")
        val cached = stamp != null &&
            runCatching { marker.readText() }.getOrNull() == stamp &&
            File(bin, "proot").canExecute() &&
            File(bin, "loader").canExecute() &&
            File(bin, "loader32").canExecute() &&
            File(bin, "libtalloc.so.2").isFile &&
            File(bin, "libandroid-shmem.so").isFile

        if (!cached) {
            runCatching {
                bin.mkdirs()
                listOf("proot", "loader", "loader32", "libtalloc.so.2", "libandroid-shmem.so")
                    .forEach { name ->
                        val target = File(bin, name)
                        context.assets.open("dev/$name").use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output, COPY_BUFFER)
                            }
                        }
                    }
                Os.chmod(File(bin, "proot").absolutePath, 0x1C0) // 0700
                Os.chmod(File(bin, "loader").absolutePath, 0x1C0)
                Os.chmod(File(bin, "loader32").absolutePath, 0x1C0)
                if (stamp != null) marker.writeText(stamp)
            }.onFailure { error ->
                DebugLog.error("dev", error)
                return false
            }
        }

        val root = rootfs(context)
        // Рабочий каталог по умолчанию — /sdcard/OpenCode. Без «Доступа ко
        // всем файлам» его не пробросить, поэтому внутри гостя используем
        // приватный каталог, который точно читается.
        val requested = paths?.workspace ?: File(context.filesDir, "workspace")
        val workspace = guestWorkspace(requested, File(context.filesDir, "workspace"))
        workspace.mkdirs()
        return runCatching {
            tools.mkdirs()
            File(installDir(context), "tmp").mkdirs()
            writeWrapper(File(tools, "dev"), bin, root, workspace, installDir(context))
            true
        }.getOrElse {
            DebugLog.error("dev", it)
            false
        }
    }

    private fun writeWrapper(target: File, bin: File, root: File, workspace: File, dev: File) {
        val text = wrapperScript(bin, root, workspace, dev)
        target.writeText(text)
        Os.chmod(target.absolutePath, 0x1ED) // 0755
    }

    /** Текст обёртки `dev`; вынесен отдельно, чтобы проверять тестами. */
    internal fun wrapperScript(bin: File, root: File, workspace: File, dev: File): String {
        val text = """
            #!/system/bin/sh
            # Сгенерировано OpenCode Pocket Dev. Запускает команду в Ubuntu
            # через proot; без аргументов открывает bash.
            BIN=${quote(bin.absolutePath)}
            ROOTFS=${quote(root.absolutePath)}
            WORKSPACE=${quote(workspace.absolutePath)}
            if [ ! -x "${'$'}ROOTFS/bin/bash" ]; then
              echo "Pocket Dev: rootfs не установлен. Откройте Settings -> Pocket Dev." >&2
              exit 127
            fi
            export PROOT_TMP_DIR=${quote(File(dev, "tmp").absolutePath)}
            # Termux-сборка proot ищет ELF-загрузчик по своему пути в
            # /data/data/com.termux, недоступному другим UID. Указываем
            # на копию, распакованную рядом с proot.
            export PROOT_LOADER="${'$'}BIN/loader"
            export PROOT_LOADER_32="${'$'}BIN/loader32"
            export LD_LIBRARY_PATH="${'$'}BIN"
            if [ "${'$'}#" -eq 0 ]; then
              set -- /bin/bash -l
            fi
            # Общие каталоги пробрасываем только если они доступны на чтение:
            # без «Доступа ко всем файлам» proot иначе ругается на /sdcard.
            BINDS="-b /dev -b /proc -b /sys"
            for dir in /sdcard /storage /mnt; do
              if [ -d "${'$'}dir" ] && [ -r "${'$'}dir" ]; then
                BINDS="${'$'}BINDS -b ${'$'}dir"
              fi
            done
            exec "${'$'}BIN/proot" --link2symlink --kill-on-exit -0 -r "${'$'}ROOTFS" \
              ${'$'}BINDS -b "${'$'}WORKSPACE:/workspace" -w /root \
              /usr/bin/env -i HOME=/root \
              PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
              TERM=xterm-256color LANG=C.UTF-8 TMPDIR=/tmp "${'$'}@"
        """.trimIndent() + "\n"
        return text
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Каталог, который реально можно пробросить в `/workspace`. Внешний
     * `/sdcard/OpenCode` доступен только с разрешением на все файлы;
     * иначе берём приватный каталог приложения.
     */
    internal fun guestWorkspace(requested: File, fallback: File): File =
        if (runCatching { requested.isDirectory && requested.canRead() }.getOrDefault(false)) {
            requested
        } else {
            fallback
        }

    /** Перечитывает состояние rootfs и свободное место. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val root = rootfs(context)
        val bin = binDir(context)
        val version = readVersion(root)
        val free = runCatching { StatFs(installDir(context).absolutePath).availableBytes }
            .getOrDefault(0L)
        _state.update {
            it.copy(
                toolsReady = File(bin, "proot").canExecute() &&
                    File(bin, "loader").canExecute(),
                installed = version.isNotBlank(),
                version = version,
                rootfs = root.absolutePath,
                freeBytes = free,
            )
        }
    }

    private fun readVersion(root: File): String {
        val release = File(root, "etc/os-release")
        if (!release.isFile) return ""
        val name = runCatching {
            release.readLines()
                .firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')
                ?.trim('"')
        }.getOrNull()
        return name ?: "Ubuntu"
    }

    /**
     * Скачивает, проверяет и распаковывает Ubuntu base. Частичный архив
     * докачивается через HTTP Range; rootfs распаковывается в rootfs.part
     * и переименовывается только после успеха.
     */
    suspend fun install(context: Context) = withContext(Dispatchers.IO) {
        if (_state.value.busy) return@withContext
        _state.update { it.copy(busy = true, error = "", phase = "Подготовка", progress = 0f) }
        try {
            if (!ensureTools(context)) throw IOException("Не удалось распаковать proot")
            val archive = File(File(context.cacheDir, "dev"), ARCHIVE_NAME)
            checkSpace(context, archive)
            if (!archive.isFile || archive.length() != ARCHIVE_SIZE) {
                download(archive)
            }
            _state.update { it.copy(phase = "Проверка SHA-256", progress = 0f) }
            val hash = sha256(archive)
            if (!hash.equals(ARCHIVE_SHA256, ignoreCase = true)) {
                throw IOException("SHA-256 не совпал: $hash")
            }

            val root = rootfs(context)
            if (readVersion(root).isNotBlank()) {
                _state.update { it.copy(installed = true, version = readVersion(root), phase = "Уже установлено", busy = false) }
                return@withContext
            }
            val part = File(root.parentFile, "rootfs.part")
            // Недокачанная/недораспакованная копия от прошлой попытки.
            if (part.exists()) part.deleteRecursively()
            _state.update { it.copy(phase = "Распаковка", entries = 0) }
            val entries = DevArchive.extract(archive, part) { count ->
                _state.update { it.copy(entries = count, phase = "Распаковка: $count файлов") }
            }
            prepareGuest(part)
            if (root.exists()) throw IOException("Каталог rootfs уже существует")
            if (!part.renameTo(root)) throw IOException("Не удалось переименовать rootfs")
            File(root, ".pocket-dev").writeText(
                "ubuntu=$UBUNTU_VERSION\nsha256=$ARCHIVE_SHA256\nentries=$entries\n",
            )
            val version = readVersion(root)
            _state.update {
                it.copy(
                    busy = false,
                    installed = true,
                    version = version,
                    phase = "Готово",
                    progress = 1f,
                    entries = entries,
                    error = "",
                )
            }
            DebugLog.event(
                "dev",
                "rootfs installed",
                mapOf("version" to version, "entries" to entries.toString()),
            )
            runCatching { test(context) }
        } catch (t: Throwable) {
            DebugLog.error("dev", t)
            _state.update {
                it.copy(busy = false, error = t.message ?: t.javaClass.simpleName, phase = "Ошибка")
            }
        }
    }

    /**
     * Полная переустановка: текущий rootfs откладывается в rootfs.old и
     * возвращается, если распаковка не удалась. Нужна, когда файлы
     * окружения потеряли SELinux-метки (обычно после apt из root-оболочки)
     * и приложение больше не может их прочитать.
     */
    suspend fun reinstall(context: Context) = withContext(Dispatchers.IO) {
        if (_state.value.busy) return@withContext
        val root = rootfs(context)
        val backup = File(root.parentFile, "rootfs.old")
        if (backup.exists()) backup.deleteRecursively()
        if (root.exists() && !root.renameTo(backup)) {
            _state.update {
                it.copy(
                    busy = false,
                    phase = "Ошибка",
                    error = "Не удалось отложить текущее окружение",
                )
            }
            return@withContext
        }
        _state.update { it.copy(test = "", checked = false, broken = false, error = "") }
        install(context)
        if (_state.value.installed) {
            runCatching { backup.deleteRecursively() }
            DebugLog.event("dev", "rootfs reinstalled")
        } else if (backup.exists() && !root.exists()) {
            // Новая распаковка не удалась — возвращаем рабочее окружение.
            runCatching { backup.renameTo(root) }
        }
    }

    private fun checkSpace(context: Context, archive: File) {
        val dir = installDir(context)
        dir.mkdirs()
        val free = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)
        val needed = MIN_FREE_BYTES - if (archive.isFile) archive.length() else 0L
        if (free < needed) {
            throw IOException(
                "Мало места: свободно ${mb(free)} МБ, нужно ${mb(needed)} МБ",
            )
        }
    }

    private fun mb(bytes: Long): Long = bytes / (1024 * 1024)

    private fun download(target: File) {
        target.parentFile?.mkdirs()
        var existing = if (target.isFile) target.length() else 0L
        if (existing > ARCHIVE_SIZE) {
            target.delete()
            existing = 0L
        }
        val connection = URL(ARCHIVE_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "PocketDev/1.0")
        if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
        connection.connect()
        val code = connection.responseCode
        val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
        if (code != HttpURLConnection.HTTP_OK && !append) {
            connection.disconnect()
            throw IOException("HTTP $code при скачивании Ubuntu")
        }
        if (!append) existing = 0L
        val total = ARCHIVE_SIZE
        var done = existing
        FileOutputStream(target, append).use { output ->
            connection.inputStream.use { input ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    _state.update {
                        it.copy(
                            phase = "Скачивание ${mb(done)} / ${mb(total)} МБ",
                            progress = (done.toFloat() / total).coerceIn(0f, 1f),
                        )
                    }
                }
            }
            output.fd.sync()
        }
        connection.disconnect()
        if (target.length() != ARCHIVE_SIZE) {
            throw IOException("Размер архива ${target.length()} вместо $ARCHIVE_SIZE")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun prepareGuest(root: File) {
        listOf("dev", "proc", "sys", "tmp", "root", "workspace", "sdcard", "storage", "mnt")
            .forEach { File(root, it).mkdirs() }
        runCatching { Os.chmod(File(root, "tmp").absolutePath, 0x1FF) } // 0777, sticky нет
        val resolv = File(root, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
    }

    /**
     * Проверяет proot и окружение фиксированным набором команд: оболочку,
     * разрешение симлинка python3, сам python3, его модули и git. Ошибка
     * запуска при наличии файла означает потерянные SELinux-метки, а не
     * отсутствие пакета — такие случаи помечаются как `fail`.
     */
    suspend fun test(context: Context): String = withContext(Dispatchers.IO) {
        val bin = binDir(context)
        val proot = File(bin, "proot")
        if (!proot.canExecute()) {
            val message = "proot не распакован"
            _state.update { it.copy(test = message, checked = true, broken = false) }
            return@withContext message
        }
        val version = prootVersion(
            run(bin, proot.absolutePath, "--version", timeoutSeconds = 15),
        )
        val installed = readVersion(rootfs(context)).isNotBlank()
        val report = if (installed) {
            val output = run(
                bin,
                wrapper(context).absolutePath,
                "/bin/sh",
                "-c",
                checkScript(),
                timeoutSeconds = 60,
            )
            parseCheck(output)
        } else {
            null
        }
        val text = buildString {
            appendLine("proot: $version")
            if (report == null) {
                appendLine("guest: rootfs не установлен")
            } else if (report.probes.isEmpty()) {
                appendLine("guest: нет ответа")
            } else {
                report.probes.forEach { probe ->
                    append("${probe.name}: ${probe.status}")
                    if (probe.detail.isNotBlank()) append(" · ${probe.detail}")
                    appendLine()
                }
            }
        }.trimEnd()
        val broken = report != null &&
            (report.broken || report.probes.none { it.name == "shell" && it.status == "ok" })
        _state.update {
            it.copy(test = text, checked = true, broken = broken)
        }
        text
    }

    /**
     * Скрипт проверок для гостя. Пишется под POSIX sh, без bash-расширений.
     * `absent` отличается от `fail`: первый значит «пакет не установлен»,
     * второй — «файл есть, но запуск запрещён» (симптом SELinux).
     */
    internal fun checkScript(): String = """
        probe() {
          label=${'$'}1
          shift
          out=${'$'}("${'$'}@" 2>&1)
          code=${'$'}?
          out=${'$'}(printf '%s' "${'$'}out" | tr '\n' ' ' | tr -s ' ' | cut -c1-240)
          case "${'$'}out" in
            *"not found"*|*"No such file"*|*"not exist"*)
              printf '%s=absent %s\n' "${'$'}label" "${'$'}out" ;;
            *)
              if [ "${'$'}code" -eq 0 ]; then
                printf '%s=ok %s\n' "${'$'}label" "${'$'}out"
              else
                printf '%s=fail(%s) %s\n' "${'$'}label" "${'$'}code" "${'$'}out"
              fi ;;
          esac
        }
        probe shell /bin/sh -c 'echo pocket-dev'
        probe link stat -L -c '%n' /usr/bin/python3
        probe python python3 -V
        probe imports python3 -c 'import json,ssl,sqlite3,urllib.request,zlib,bz2,lzma'
        probe git git --version
    """.trimIndent() + "\n"

    /** Из вывода `proot --version` берёт номер версии, а не ASCII-баннер. */
    internal fun prootVersion(output: String): String =
        Regex("""\b\d+\.\d+\.\d+\b""").find(output)?.value
            ?: output.trim().lineSequence().firstOrNull().orEmpty().take(60)

    /** Разбирает вывод [checkScript] в список проверок. */
    internal fun parseCheck(output: String): CheckReport {
        val probes = output.lineSequence()
            .mapNotNull { line ->
                val at = line.indexOf('=')
                if (at <= 0) return@mapNotNull null
                val name = line.substring(0, at).trim()
                val rest = line.substring(at + 1).trim()
                if (name.isEmpty() || rest.isEmpty()) return@mapNotNull null
                val status = rest.substringBefore(' ')
                val detail = rest.substringAfter(' ', "").trim()
                if (status != "ok" && status != "absent" && !status.startsWith("fail")) {
                    return@mapNotNull null
                }
                Probe(name, status, detail)
            }
            .toList()
        return CheckReport(probes)
    }

    private fun run(libDir: File, vararg command: String, timeoutSeconds: Long): String {
        return runCatching {
            val builder = ProcessBuilder(*command)
            builder.redirectErrorStream(true)
            builder.environment()["LD_LIBRARY_PATH"] = libDir.absolutePath
            builder.environment()["PROOT_TMP_DIR"] =
                File(libDir.parentFile ?: libDir, "tmp").absolutePath
            builder.environment()["PROOT_LOADER"] = File(libDir, "loader").absolutePath
            builder.environment()["PROOT_LOADER_32"] = File(libDir, "loader32").absolutePath
            val process = builder.start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching "timeout: $output"
            }
            output
        }.getOrElse { it.message ?: it.javaClass.simpleName }
    }
}
