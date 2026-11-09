package com.pocket.opencode

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DebugEvent(
    val ts: Long,
    val kind: String,
    val message: String,
    val data: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", ts)
        put("time", SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ts)))
        put("kind", kind)
        put("message", Redaction.redact(message))
        if (data.isNotEmpty()) {
            put("data", JSONObject(data.mapValues { Redaction.redact(it.value) }))
        }
    }

    fun toLine(): String = toJson().toString()
}

object Redaction {
    private val rules = listOf(
        Regex("(?i)(authorization\\s*[:=]\\s*)(?:bearer\\s+)?\\S+") to "$1***",
        Regex("(?i)(password|passwd|token|api[_-]?key|authorization)([\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+") to "$1$2***",
        Regex("(?i)\\b(password)\\s+[^\\s\"',}]+") to "$1 ***",
        Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+") to "Bearer ***",
        Regex("\\bsk-[A-Za-z0-9_-]{8,}") to "sk-***"
    )

    fun redact(value: String): String =
        rules.fold(value) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }
}
