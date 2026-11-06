package com.pocket.opencode

import org.json.JSONArray
import org.json.JSONObject

data class ServerInfo(
    val version: String,
    val pid: Int,
    val hostname: String,
    val urls: List<String>
)

data class LocationInfo(
    val directory: String,
    val projectID: String,
    val canonical: String
)

data class SessionInfo(
    val id: String,
    val title: String,
    val created: Long,
    val updated: Long,
    val idle: Long?,
    val directory: String,
    val agent: String?,
    val modelProvider: String?,
    val modelId: String?,
    val cost: Double,
    val tokensInput: Long,
    val tokensOutput: Long,
    val outcome: String?,
    val archived: Boolean
)

data class ModelRef(
    val id: String,
    val providerID: String,
    val variant: String? = null
)

data class ModelChoice(
    val id: String,
    val providerID: String,
    val name: String,
    val variants: List<String>,
    val status: String,
    val enabled: Boolean
)

data class AgentChoice(
    val id: String,
    val name: String,
    val description: String,
    val mode: String,
    val hidden: Boolean
)

data class PermissionAsk(
    val id: String,
    val sessionID: String,
    val action: String,
    val resources: List<String>,
    val message: String
)

data class FormOptionSpec(
    val value: String,
    val label: String,
    val description: String
)

data class FormCondition(
    val key: String,
    val op: String,
    val value: String
)

data class FormFieldSpec(
    val key: String,
    val type: String,
    val title: String,
    val description: String,
    val required: Boolean,
    val hidden: Boolean,
    val placeholder: String,
    val options: List<FormOptionSpec>,
    val defaultValue: Any?,
    val minimum: Double?,
    val maximum: Double?,
    val url: String,
    val format: String,
    val conditions: List<FormCondition>,
    val custom: Boolean
)

data class FormSpec(
    val id: String,
    val sessionID: String,
    val title: String,
    val fields: List<FormFieldSpec>
)

data class VcsSummary(
    val provider: String,
    val current: String,
    val default: String
)

data class VcsFile(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val status: String
)

data class VcsDiff(
    val path: String,
    val patch: String,
    val additions: Int,
    val deletions: Int,
    val status: String,
    val more: Boolean
)

data class FileEntry(
    val path: String,
    val type: String
)

enum class UiRole { USER, ASSISTANT, SYSTEM, SHELL }

sealed interface UiBlock {
    data class Text(val ordinal: Int = 0, val content: String = "") : UiBlock
    data class Reasoning(
        val ordinal: Int = 0,
        val content: String = "",
        val streaming: Boolean = false
    ) : UiBlock

    data class Tool(
        val callId: String,
        val name: String,
        val input: String = "",
        val status: String = "streaming",
        val output: String = "",
        val metadata: String = "",
        val shellId: String? = null,
        val exit: Int? = null,
        val error: String? = null
    ) : UiBlock

    data class Shell(
        val shellId: String,
        val command: String,
        val status: String = "running",
        val output: String = "",
        val exit: Int? = null
    ) : UiBlock
}

data class UiMessage(
    val id: String,
    val role: UiRole,
    val blocks: List<UiBlock> = emptyList(),
    val agent: String? = null,
    val model: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val outcome: String? = null,
    val error: String? = null
)

internal fun jsonText(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is JSONObject -> value.toString(2)
    is JSONArray -> value.toString(2)
    is String -> value
    else -> value.toString()
}

internal fun JSONObject.optText(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

internal fun parseToolContent(content: JSONArray?): String {
    if (content == null) return ""
    val parts = mutableListOf<String>()
    for (i in 0 until content.length()) {
        val item = content.optJSONObject(i) ?: continue
        when (item.optString("type")) {
            "text" -> item.optText("text")?.let { parts += it }
            "file" -> {
                val name = item.optText("name") ?: item.optText("uri") ?: "файл"
                val mime = item.optText("mime").orEmpty()
                parts += "[файл] $name${if (mime.isNotBlank()) " ($mime)" else ""}"
            }
            else -> item.optText("text")?.let { parts += it }
        }
    }
    return parts.joinToString("\n")
}

fun parseServerInfo(raw: JSONObject): ServerInfo {
    val urls = mutableListOf<String>()
    raw.optJSONArray("urls")?.let { arr ->
        for (i in 0 until arr.length()) urls += arr.optString(i)
    }
    return ServerInfo(
        version = raw.optString("version"),
        pid = raw.optInt("pid"),
        hostname = raw.optString("hostname"),
        urls = urls
    )
}

fun parseLocation(raw: JSONObject): LocationInfo {
    val project = raw.optJSONObject("project")
    return LocationInfo(
        directory = raw.optString("directory"),
        projectID = project?.optString("id").orEmpty(),
        canonical = project?.optString("canonical").orEmpty()
    )
}

fun parseSession(raw: JSONObject): SessionInfo {
    val time = raw.optJSONObject("time")
    val tokens = raw.optJSONObject("tokens")
    val model = raw.optJSONObject("model")
    val location = raw.optJSONObject("location")
    return SessionInfo(
        id = raw.optString("id"),
        title = raw.optString("title").ifBlank { "Без названия" },
        created = time?.optLong("created") ?: 0L,
        updated = time?.optLong("updated") ?: 0L,
        idle = if (time?.has("idle") == true) time.optLong("idle") else null,
        directory = location?.optString("directory").orEmpty(),
        agent = raw.optText("agent"),
        modelProvider = model?.optText("providerID"),
        modelId = model?.optText("id"),
        cost = raw.optDouble("cost", 0.0),
        tokensInput = tokens?.optLong("input") ?: 0L,
        tokensOutput = tokens?.optLong("output") ?: 0L,
        outcome = raw.optText("outcome"),
        archived = time?.has("archived") == true
    )
}

fun parseModel(raw: JSONObject): ModelChoice {
    val variants = mutableListOf<String>()
    raw.optJSONArray("variants")?.let { arr ->
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { variants += it }
        }
    }
    return ModelChoice(
        id = raw.optString("modelID").ifBlank { raw.optString("id") },
        providerID = raw.optString("providerID"),
        name = raw.optString("name").ifBlank { raw.optString("modelID") },
        variants = variants,
        status = raw.optString("status", "active"),
        enabled = raw.optBoolean("enabled", true)
    )
}

fun parseAgent(raw: JSONObject): AgentChoice = AgentChoice(
    id = raw.optString("id"),
    name = raw.optString("name").ifBlank { raw.optString("id") },
    description = raw.optString("description"),
    mode = raw.optString("mode", "primary"),
    hidden = raw.optBoolean("hidden", false)
)

fun parsePermission(raw: JSONObject): PermissionAsk {
    val resources = mutableListOf<String>()
    raw.optJSONArray("resources")?.let { arr ->
        for (i in 0 until arr.length()) resources += arr.optString(i)
    }
    return PermissionAsk(
        id = raw.optString("id"),
        sessionID = raw.optString("sessionID"),
        action = raw.optString("action"),
        resources = resources,
        message = raw.optString("message")
    )
}

private fun parseFormField(raw: JSONObject): FormFieldSpec {
    val options = mutableListOf<FormOptionSpec>()
    raw.optJSONArray("options")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            options += FormOptionSpec(
                value = o.optString("value"),
                label = o.optString("label").ifBlank { o.optString("value") },
                description = o.optString("description")
            )
        }
    }
    val defaultValue = when (val v = raw.opt("default")) {
        JSONObject.NULL -> null
        is JSONArray -> {
            val set = mutableSetOf<String>()
            for (i in 0 until v.length()) set += v.optString(i)
            set
        }
        else -> v
    }
    val conditions = mutableListOf<FormCondition>()
    raw.optJSONArray("when")?.let { arr ->
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            conditions += FormCondition(
                key = c.optString("key"),
                op = c.optString("op", "eq"),
                value = when (val v = c.opt("value")) {
                    null, JSONObject.NULL -> ""
                    is String -> v
                    is Number -> v.toString()
                    is Boolean -> v.toString()
                    else -> v.toString()
                }
            )
        }
    }
    return FormFieldSpec(
        key = raw.optString("key"),
        type = raw.optString("type", "string"),
        title = raw.optString("title").ifBlank { raw.optString("key") },
        description = raw.optString("description"),
        required = raw.optBoolean("required", false),
        hidden = raw.optBoolean("hidden", false),
        placeholder = raw.optString("placeholder"),
        options = options,
        defaultValue = defaultValue,
        minimum = if (raw.has("minimum") && !raw.isNull("minimum")) raw.optDouble("minimum") else null,
        maximum = if (raw.has("maximum") && !raw.isNull("maximum")) raw.optDouble("maximum") else null,
        url = raw.optString("url"),
        format = raw.optString("format"),
        conditions = conditions,
        custom = raw.optBoolean("custom", false)
    )
}

fun parseForm(raw: JSONObject): FormSpec {
    val fields = mutableListOf<FormFieldSpec>()
    raw.optJSONArray("fields")?.let { arr ->
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { fields += parseFormField(it) }
        }
    }
    return FormSpec(
        id = raw.optString("id"),
        sessionID = raw.optString("sessionID"),
        title = raw.optString("title").ifBlank { "Форма" },
        fields = fields
    )
}

fun parseServerMessages(data: JSONArray): List<UiMessage> {
    val out = mutableListOf<UiMessage>()
    for (i in 0 until data.length()) {
        val raw = data.optJSONObject(i) ?: continue
        val id = raw.optString("id")
        val time = raw.optJSONObject("time")?.optLong("created") ?: 0L
        when (raw.optString("type")) {
            "user" -> out += UiMessage(
                id = id,
                role = UiRole.USER,
                blocks = listOf(UiBlock.Text(content = raw.optString("text"))),
                createdAt = time
            )

            "assistant" -> {
                val blocks = mutableListOf<UiBlock>()
                val content = raw.optJSONArray("content") ?: JSONArray()
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    when (part.optString("type")) {
                        "text" -> blocks += UiBlock.Text(
                            ordinal = j,
                            content = part.optString("text")
                        )

                        "reasoning" -> blocks += UiBlock.Reasoning(
                            ordinal = j,
                            content = part.optString("text"),
                            streaming = false
                        )

                        "tool" -> {
                            val state = part.optJSONObject("state")
                            val meta = state?.optJSONObject("metadata")
                            blocks += UiBlock.Tool(
                                callId = part.optString("id"),
                                name = part.optString("name"),
                                input = jsonText(state?.opt("input")),
                                status = state?.optString("status", "completed") ?: "completed",
                                output = parseToolContent(state?.optJSONArray("content")),
                                metadata = meta?.toString(2).orEmpty(),
                                exit = if (meta?.has("exit") == true) meta.optInt("exit") else null,
                                error = state?.optJSONObject("error")?.optString("message")
                            )
                        }
                    }
                }
                val model = raw.optJSONObject("model")
                val error = raw.optJSONObject("error")?.optString("message")
                out += UiMessage(
                    id = id,
                    role = UiRole.ASSISTANT,
                    blocks = blocks,
                    agent = raw.optText("agent"),
                    model = model?.optText("id"),
                    createdAt = time,
                    error = error
                )
            }

            "shell" -> {
                val output = raw.optJSONObject("output")
                out += UiMessage(
                    id = id,
                    role = UiRole.SHELL,
                    blocks = listOf(
                        UiBlock.Shell(
                            shellId = raw.optString("shellID"),
                            command = raw.optString("command"),
                            status = raw.optString("status", "running"),
                            output = output?.optString("output").orEmpty(),
                            exit = if (raw.has("exit") && !raw.isNull("exit")) raw.optInt("exit") else null
                        )
                    ),
                    createdAt = time
                )
            }

            "system", "synthetic", "skill" -> out += UiMessage(
                id = id,
                role = UiRole.SYSTEM,
                blocks = listOf(UiBlock.Text(content = raw.optString("text"))),
                createdAt = time
            )

            // "idle" намеренно пропускается: итог уже виден по статусу выполнения
            // и по карточке ошибки ассистента, дублировать его в ленте не нужно.
        }
    }
    // Сервер может переиспользовать ID сообщения (например, повторный ход
    // тестового сервера). LazyColumn падает на неуникальных ключах, поэтому
    // оставляем последнее сообщение с каждым ID.
    val unique = LinkedHashMap<String, UiMessage>(out.size)
    out.forEach { unique[it.id] = it }
    return unique.values.toList()
}

fun UiMessage.toCacheJson(): JSONObject {
    val o = JSONObject()
    o.put("id", id)
    o.put("role", role.name)
    o.put("created", createdAt)
    agent?.let { o.put("agent", it) }
    model?.let { o.put("model", it) }
    outcome?.let { o.put("outcome", it) }
    error?.let { o.put("error", it) }
    val arr = JSONArray()
    for (block in blocks) {
        val b = JSONObject()
        when (block) {
            is UiBlock.Text -> {
                b.put("type", "text")
                b.put("ordinal", block.ordinal)
                b.put("content", block.content)
            }
            is UiBlock.Reasoning -> {
                b.put("type", "reasoning")
                b.put("ordinal", block.ordinal)
                b.put("content", block.content)
                b.put("streaming", block.streaming)
            }
            is UiBlock.Tool -> {
                b.put("type", "tool")
                b.put("callId", block.callId)
                b.put("name", block.name)
                b.put("input", block.input)
                b.put("status", block.status)
                b.put("output", block.output)
                b.put("metadata", block.metadata)
                block.shellId?.let { b.put("shellId", it) }
                block.exit?.let { b.put("exit", it) }
                block.error?.let { b.put("error", it) }
            }
            is UiBlock.Shell -> {
                b.put("type", "shell")
                b.put("shellId", block.shellId)
                b.put("command", block.command)
                b.put("status", block.status)
                b.put("output", block.output)
                block.exit?.let { b.put("exit", it) }
            }
        }
        arr.put(b)
    }
    o.put("blocks", arr)
    return o
}

fun cacheFromJson(raw: String): List<UiMessage> {
    val root = JSONObject(raw)
    val messages = mutableListOf<UiMessage>()
    val arr = root.optJSONArray("messages") ?: return messages
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val role = runCatching { UiRole.valueOf(o.optString("role")) }.getOrDefault(UiRole.SYSTEM)
        val blocks = mutableListOf<UiBlock>()
        val bArr = o.optJSONArray("blocks") ?: JSONArray()
        for (j in 0 until bArr.length()) {
            val b = bArr.optJSONObject(j) ?: continue
            when (b.optString("type")) {
                "text" -> blocks += UiBlock.Text(
                    ordinal = b.optInt("ordinal"),
                    content = b.optString("content")
                )

                "reasoning" -> blocks += UiBlock.Reasoning(
                    ordinal = b.optInt("ordinal"),
                    content = b.optString("content"),
                    streaming = b.optBoolean("streaming", false)
                )

                "tool" -> blocks += UiBlock.Tool(
                    callId = b.optString("callId"),
                    name = b.optString("name"),
                    input = b.optString("input"),
                    status = b.optString("status"),
                    output = b.optString("output"),
                    metadata = b.optString("metadata"),
                    shellId = b.optText("shellId"),
                    exit = if (b.has("exit")) b.optInt("exit") else null,
                    error = b.optText("error")
                )

                "shell" -> blocks += UiBlock.Shell(
                    shellId = b.optString("shellId"),
                    command = b.optString("command"),
                    status = b.optString("status"),
                    output = b.optString("output"),
                    exit = if (b.has("exit")) b.optInt("exit") else null
                )
            }
        }
        messages += UiMessage(
            id = o.optString("id"),
            role = role,
            blocks = blocks,
            agent = o.optText("agent"),
            model = o.optText("model"),
            createdAt = o.optLong("created"),
            outcome = o.optText("outcome"),
            error = o.optText("error")
        )
    }
    return messages
}
