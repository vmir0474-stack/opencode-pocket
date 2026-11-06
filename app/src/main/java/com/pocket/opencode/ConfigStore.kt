package com.pocket.opencode

import org.json.JSONObject
import java.io.File

object ConfigStore {
    fun validate(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "Конфиг пуст"
        return try {
            JSONObject(trimmed)
            null
        } catch (t: Throwable) {
            "Некорректный JSON: ${t.message}"
        }
    }

    fun read(file: File): String {
        if (!file.isFile) return "{\n}\n"
        return runCatching { file.readText() }.getOrDefault("{\n}\n")
    }

    fun write(file: File, text: String) {
        val error = validate(text)
        require(error == null) { error.orEmpty() }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (file.isFile) {
            runCatching { file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true) }
        }
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
