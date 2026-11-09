package com.pocket.opencode

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Встроенные CLI-инструменты сервера.
 *
 * Android (targetSdk 29+) запрещает исполнять ELF из app-data, но разрешает
 * запускать файлы из nativeLibraryDir. PackageManager распаковывает туда
 * только `*.so`, поэтому бинарник лежит как `librg.so`, а рядом в
 * app-owned каталоге создаётся symlink с обычным именем `rg` — opencode
 * ищет его через PATH. Цель symlink меняется после обновления APK, поэтому
 * ссылка пересоздаётся при каждом старте.
 */
object Toolchain {
    private const val RG_LIB = "librg.so"
    private const val PCRE_LIB = "libpcre2-8.so"
    private const val SELF_TEST_TIMEOUT_MS = 8_000L

    data class Status(
        val dir: File,
        val rg: File? = null,
        val version: String = "",
        val error: String = "",
        val pocketRust: File? = null,
    ) {
        val ok: Boolean get() = rg != null && error.isBlank()
    }

    fun toolsDir(paths: ServerPaths): File = File(paths.root, "tools")

    /**
     * Готовит каталог инструментов и проверяет, что `rg` действительно
     * запускается. Ошибка не валит сервер: grep просто станет недоступен.
     */
    fun ensure(context: Context, paths: ServerPaths): Status {
        val dir = toolsDir(paths)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val source = File(nativeDir, RG_LIB)
        if (!source.isFile) {
            return Status(dir, error = "нет $RG_LIB в ${nativeDir.absolutePath}")
        }
        val pcre = File(nativeDir, PCRE_LIB)
        if (!pcre.isFile) {
            return Status(dir, error = "нет $PCRE_LIB в ${nativeDir.absolutePath}")
        }
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            return Status(dir, error = "не удалось создать ${dir.absolutePath}")
        }
        val link = File(dir, "rg")
        runCatching { linkTo(link, source) }
            .onFailure { return Status(dir, error = "symlink: ${it.message}") }

        // Агент ставит скилы одной командой, а не угадывает пути и curl.
        runCatching {
            val script = File(dir, "pocket-skill")
            script.writeText(skillScript(File(context.filesDir, "opencode/debug/token")))
            script.setExecutable(true, false)
        }.onFailure { DebugLog.error("tools", it) }

        // Нативный Rust-инструмент: find/diff/highlight для чата.
        val rustSource = File(nativeDir, "libpocket_rs.so")
        val rustLink = File(dir, "pocket-rs")
        val pocketRust = if (rustSource.isFile) {
            runCatching { linkTo(rustLink, rustSource); rustLink }.getOrNull()
        } else {
            null
        }

        // Самопроверка `rg --version` занимает больше секунды на холодном
        // старте: динамическая линковка большого Rust-бинарника. Результат
        // зависит только от файлов APK, поэтому кэшируем его до обновления.
        val marker = File(dir, ".rg-ok")
        val signature = signature(source, pcre)
        cachedVersion(marker, signature, link)?.let { version ->
            return Status(dir, link, version, "", pocketRust)
        }

        val test = selfTest(link, nativeDir)
        return if (test.first == 0) {
            val version = test.second.trim()
            runCatching { marker.writeText(signature + "\n" + version) }
            Status(dir, link, version, "", pocketRust)
        } else {
            runCatching { marker.delete() }
            Status(dir, link, "", test.second.trim().ifBlank { "rg завершился с кодом ${test.first}" }, pocketRust)
        }
    }

    private fun signature(source: File, pcre: File): String =
        "${source.absolutePath}:${source.length()}:${source.lastModified()}:" +
            "${pcre.length()}:${pcre.lastModified()}"

    private fun cachedVersion(marker: File, signature: String, link: File): String? {
        if (!link.exists()) return null
        val text = runCatching { marker.readText() }.getOrNull() ?: return null
        val first = text.substringBefore('\n')
        if (first != signature) return null
        return text.substringAfter('\n', "").trim().ifBlank { null }
    }

    /** PATH для сервера: сначала наш `rg`, затем системные утилиты. */
    fun path(status: Status): String {
        val prefix = status.dir.absolutePath
        return "$prefix:/system/bin:/system/xbin"
    }

    /**
     * Обёртка `pocket-skill` над локальным мостом. Порт моста может
     * сместиться (4097–4100), поэтому скрипт сам находит живой `/health`.
     * Токен читается из приватного файла приложения, как и у остальных
     * инструментов.
     */
    internal fun skillScript(tokenFile: File): String {
        val token = tokenFile.absolutePath
        val script = """
            #!/system/bin/sh
            # pocket-skill — поиск и установка скилов OpenCode Pocket.
            # Файл создаётся приложением; не редактируй вручную.
            TOKEN_FILE='$token'
            BASE=''
            if [ -r '$token' ]; then
              TOKEN=~(cat '$token')
              for P in 4097 4098 4099 4100; do
                if curl -fsS --max-time 2 -H "Authorization: Bearer ~TOKEN" \
                  "http://127.0.0.1:~P/health" >/dev/null 2>&1; then
                  BASE="http://127.0.0.1:~P"
                  break
                fi
              done
            fi
            if [ -z "~BASE" ]; then
              echo '{"error":"мост OpenCode Pocket недоступен: открой приложение и запусти сервер"}' >&2
              exit 1
            fi

            json_escape() {
              printf '%s' "~1" | sed 's/\\/\\\\/g; s/"/\\"/g'
            }

            call() {
              curl -sS --max-time 300 -H "Authorization: Bearer ~TOKEN" \
                -H 'Content-Type: application/json' -X POST "~BASE/action" -d "~1"
              echo
            }

            usage() {
              echo "usage: pocket-skill search ЗАПРОС" >&2
              echo "       pocket-skill install ИСТОЧНИК [--project] [--overwrite]" >&2
              echo "       pocket-skill update [ИМЯ|--all]" >&2
              echo "       pocket-skill remove ИМЯ" >&2
              echo "       pocket-skill list" >&2
            }

            CMD="~{1:-}"
            if [ -n "~CMD" ]; then shift; fi

            case "~CMD" in
              search)
                if [ ~# -lt 1 ]; then usage; exit 2; fi
                call "{\"name\":\"skill-search\",\"query\":\"~(json_escape "~*")\"}"
                ;;
              install)
                SOURCE=''
                PROJECT=false
                OVERWRITE=false
                for arg in "~@"; do
                  case "~arg" in
                    --project) PROJECT=true ;;
                    --overwrite) OVERWRITE=true ;;
                    -*) echo "pocket-skill: неизвестный флаг ~arg" >&2; exit 2 ;;
                    *) SOURCE="~arg" ;;
                  esac
                done
                if [ -z "~SOURCE" ]; then usage; exit 2; fi
                call "{\"name\":\"skill-install\",\"source\":\"~(json_escape "~SOURCE")\",\"project\":~PROJECT,\"overwrite\":~OVERWRITE}"
                ;;
              update)
                NAME=''
                ALL=false
                for arg in "~@"; do
                  case "~arg" in
                    --all) ALL=true ;;
                    -*) echo "pocket-skill: неизвестный флаг ~arg" >&2; exit 2 ;;
                    *) NAME="~arg" ;;
                  esac
                done
                if [ "~ALL" = true ]; then
                  call '{"name":"skill-update","all":true}'
                elif [ -n "~NAME" ]; then
                  call "{\"name\":\"skill-update\",\"name\":\"~(json_escape "~NAME")\",\"all\":false}"
                else
                  call '{"name":"skill-update","all":true}'
                fi
                ;;
              remove)
                if [ ~# -lt 1 ]; then usage; exit 2; fi
                call "{\"name\":\"skill-remove\",\"name\":\"~(json_escape "~1")\"}"
                ;;
              list)
                call '{"name":"skill-list"}'
                ;;
              *)
                usage
                exit 2
                ;;
            esac
        """.trimIndent()
        return script.replace("~", "$").plus("\n")
    }

    private fun linkTo(link: File, target: File) {
        val linkPath = link.toPath()
        Files.deleteIfExists(linkPath)
        Files.createSymbolicLink(linkPath, target.toPath())
    }

    private fun selfTest(rg: File, nativeDir: File): Pair<Int, String> {
        return runCatching {
            val builder = ProcessBuilder(rg.absolutePath, "--version")
            builder.redirectErrorStream(true)
            builder.environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
            builder.environment()["PATH"] = "/system/bin:/system/xbin"
            val proc = builder.start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            if (!proc.waitFor(SELF_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
                return@runCatching -1 to "$out\ntimeout"
            }
            proc.exitValue() to out
        }.getOrElse { -1 to (it.message ?: it.toString()) }
    }
}
