package com.pocket.opencode

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, body: String) :
    IOException("HTTP $code: ${body.take(500)}")

/** Файл для prompt: сервер читает file:// uri и сам кодирует его. */
data class PromptFile(val uri: String, val name: String? = null)

class OpenCodeApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun root(base: String) = base.trimEnd('/')

    private fun request(url: String, password: String): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (password.isNotBlank()) {
            val token = Base64.encodeToString(
                "opencode:$password".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            builder.header("Authorization", "Basic $token")
        }
        return builder
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    DebugLog.event(
                        "api",
                        "HTTP ${response.code} ${request.method} ${request.url.encodedPath}",
                        mapOf("body" to body.take(400))
                    )
                    throw ApiException(response.code, body)
                }
                body
            }
        } catch (t: Throwable) {
            if (t !is ApiException) DebugLog.error("api", t)
            throw t
        }
    }

    /** Сырой запрос к OpenCode API для отладочного моста. */
    suspend fun raw(
        base: String,
        password: String,
        method: String,
        path: String,
        body: JSONObject?
    ): String {
        val url = root(base) + if (path.startsWith("/")) path else "/$path"
        val builder = request(url, password)
        val payload = (body ?: JSONObject()).toString().toRequestBody(jsonType)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "POST" -> builder.post(payload)
            "PATCH" -> builder.patch(payload)
            "PUT" -> builder.put(payload)
            else -> throw IllegalArgumentException("unsupported method: $method")
        }
        return call(builder.build())
    }

    private fun post(url: String, password: String, body: JSONObject): Request =
        request(url, password)
            .post(body.toString().toRequestBody(jsonType))
            .build()

    private fun modelJson(model: ModelRef): JSONObject = JSONObject().apply {
        put("id", model.id)
        put("providerID", model.providerID)
        model.variant?.takeIf { it.isNotBlank() }?.let { put("variant", it) }
    }

    suspend fun info(base: String, password: String): ServerInfo =
        parseServerInfo(JSONObject(call(request("${root(base)}/api/info", password).get().build())))

    suspend fun location(base: String, password: String): LocationInfo =
        parseLocation(JSONObject(call(request("${root(base)}/api/location", password).get().build())))

    suspend fun sessions(base: String, password: String): List<SessionInfo> {
        val raw = call(
            request("${root(base)}/api/session?limit=50&order=desc", password).get().build()
        )
        val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<SessionInfo>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseSession(it) }
        return out
    }

    suspend fun session(base: String, password: String, sessionID: String): SessionInfo {
        val raw = call(
            request("${root(base)}/api/session/$sessionID", password).get().build()
        )
        val data = JSONObject(raw).optJSONObject("data") ?: JSONObject()
        return parseSession(data)
    }

    suspend fun messages(base: String, password: String, sessionID: String): List<UiMessage> {
        val raw = call(
            request(
                "${root(base)}/api/session/$sessionID/message?order=asc&limit=200",
                password
            ).get().build()
        )
        val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        return parseServerMessages(arr)
    }

    suspend fun createSession(
        base: String,
        password: String,
        title: String?,
        agent: String?,
        model: ModelRef?,
        directory: String? = null
    ): SessionInfo {
        val body = JSONObject()
        title?.takeIf { it.isNotBlank() }?.let { body.put("title", it) }
        agent?.takeIf { it.isNotBlank() }?.let { body.put("agent", it) }
        model?.let { body.put("model", modelJson(it)) }
        directory?.takeIf { it.isNotBlank() }?.let {
            body.put("location", JSONObject().put("directory", it))
        }
        val raw = call(post("${root(base)}/api/session", password, body))
        val data = JSONObject(raw).optJSONObject("data") ?: JSONObject()
        return parseSession(data)
    }

    suspend fun prompt(
        base: String,
        password: String,
        sessionID: String,
        text: String,
        files: List<PromptFile> = emptyList()
    ) {
        val body = JSONObject().put("text", text).put("delivery", "steer")
        if (files.isNotEmpty()) {
            val arr = JSONArray()
            files.forEach { file ->
                val item = JSONObject().put("uri", file.uri)
                file.name?.takeIf { it.isNotBlank() }?.let { item.put("name", it) }
                arr.put(item)
            }
            body.put("files", arr)
        }
        call(post("${root(base)}/api/session/$sessionID/prompt", password, body))
    }

    suspend fun interrupt(base: String, password: String, sessionID: String) {
        call(
            request("${root(base)}/api/session/$sessionID/interrupt", password)
                .post("{}".toRequestBody(jsonType)).build()
        )
    }

    suspend fun models(base: String, password: String): List<ModelChoice> {
        val raw = call(request("${root(base)}/api/model", password).get().build())
        val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<ModelChoice>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseModel(it) }
        return out
    }

    suspend fun agents(base: String, password: String): List<AgentChoice> {
        val raw = call(request("${root(base)}/api/agent", password).get().build())
        val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<AgentChoice>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseAgent(it) }
        return out
    }

    suspend fun setModel(base: String, password: String, sessionID: String, model: ModelRef) {
        call(
            post(
                "${root(base)}/api/session/$sessionID/model",
                password,
                JSONObject().put("model", modelJson(model))
            )
        )
    }

    suspend fun setAgent(base: String, password: String, sessionID: String, agent: String) {
        call(
            post(
                "${root(base)}/api/session/$sessionID/agent",
                password,
                JSONObject().put("agent", agent)
            )
        )
    }

    suspend fun activeSessions(base: String, password: String): Set<String> {
        val raw = call(request("${root(base)}/api/session/active", password).get().build())
        val data = JSONObject(raw).optJSONObject("data") ?: return emptySet()
        val result = mutableSetOf<String>()
        val keys = data.keys()
        while (keys.hasNext()) result += keys.next()
        return result
    }

    suspend fun permissions(base: String, password: String, sessionID: String): List<PermissionAsk> {
        val raw = call(
            request("${root(base)}/api/session/$sessionID/permission", password).get().build()
        )
        val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<PermissionAsk>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parsePermission(it) }
        return out
    }

    suspend fun replyPermission(
        base: String,
        password: String,
        sessionID: String,
        requestID: String,
        decision: String
    ) {
        call(
            post(
                "${root(base)}/api/session/$sessionID/permission/$requestID/reply",
                password,
                JSONObject().put("decision", decision)
            )
        )
    }

    suspend fun forms(base: String, password: String, sessionID: String): List<FormSpec> {
        val raw = call(
            request("${root(base)}/api/session/$sessionID/form", password).get().build()
        )
        val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<FormSpec>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseForm(it) }
        return out
    }

    suspend fun replyForm(
        base: String,
        password: String,
        sessionID: String,
        formID: String,
        answer: JSONObject
    ) {
        call(
            post(
                "${root(base)}/api/session/$sessionID/form/$formID/reply",
                password,
                JSONObject().put("answer", answer)
            )
        )
    }

    suspend fun cancelForm(base: String, password: String, sessionID: String, formID: String) {
        call(
            request("${root(base)}/api/session/$sessionID/form/$formID", password)
                .delete().build()
        )
    }

    suspend fun shellOutput(base: String, password: String, shellID: String): String {
        val raw = call(
            request("${root(base)}/api/shell/$shellID/output", password).get().build()
        )
        val data = JSONObject(raw).optJSONObject("data") ?: return ""
        return data.optString("output")
    }

    suspend fun vcs(base: String, password: String): VcsSummary? {
        val raw = call(request("${root(base)}/api/vcs", password).get().build())
        val data = JSONObject(raw).optJSONObject("data") ?: return null
        val branch = data.optJSONObject("branch")
        return VcsSummary(
            provider = data.optString("provider"),
            current = branch?.optString("current").orEmpty(),
            default = branch?.optString("default").orEmpty()
        )
    }

    suspend fun vcsStatus(base: String, password: String): List<VcsFile> {
        val raw = call(request("${root(base)}/api/vcs/status", password).get().build())
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<VcsFile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += VcsFile(
                path = o.optString("file"),
                additions = o.optInt("additions"),
                deletions = o.optInt("deletions"),
                status = o.optString("status")
            )
        }
        return out
    }

    suspend fun vcsDiff(base: String, password: String): List<VcsDiff> {
        val raw = call(
            request(
                "${root(base)}/api/vcs/diff?mode=working&untracked=files",
                password
            ).get().build()
        )
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<VcsDiff>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += VcsDiff(
                path = o.optString("file"),
                patch = o.optString("patch"),
                additions = o.optInt("additions"),
                deletions = o.optInt("deletions"),
                status = o.optString("status"),
                more = o.optBoolean("more", false)
            )
        }
        return out
    }

    suspend fun readFile(base: String, password: String, path: String): String {
        val clean = path.trimStart('/')
        val encoded = clean.split('/').joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return call(request("${root(base)}/api/fs/read/$encoded", password).get().build())
    }

    suspend fun files(base: String, password: String, path: String = "."): List<FileEntry> {
        val encoded = java.net.URLEncoder.encode(path, "UTF-8")
        val raw = call(
            request("${root(base)}/api/fs/list?path=$encoded", password).get().build()
        )
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<FileEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += FileEntry(path = o.optString("path"), type = o.optString("type"))
        }
        return out
    }

    suspend fun integrations(base: String, password: String): List<IntegrationInfo> {
        val raw = call(request("${root(base)}/api/integration", password).get().build())
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<IntegrationInfo>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseIntegration(it) }
        return out
    }

    suspend fun credentials(base: String, password: String): List<CredentialInfo> {
        val raw = call(request("${root(base)}/api/credential", password).get().build())
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<CredentialInfo>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseCredential(it) }
        return out
    }

    suspend fun addKeyCredential(
        base: String,
        password: String,
        integrationID: String,
        key: String,
        label: String = "default"
    ): CredentialInfo {
        val body = CredentialPayload.key(integrationID, key, label)
        val raw = call(post("${root(base)}/api/credential", password, body))
        val data = JSONObject(raw).optJSONObject("data") ?: JSONObject()
        return parseCredential(data)
    }

    suspend fun deleteCredential(base: String, password: String, id: String) {
        call(
            request("${root(base)}/api/credential/$id", password)
                .delete()
                .build()
        )
    }

    suspend fun skills(base: String, password: String): List<SkillInfo> {
        val raw = call(request("${root(base)}/api/skill", password).get().build())
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<SkillInfo>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseSkill(it) }
        return out
    }

    suspend fun mcpServers(base: String, password: String): List<McpInfo> {
        val raw = call(request("${root(base)}/api/mcp", password).get().build())
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<McpInfo>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += parseMcp(it) }
        return out
    }

    suspend fun addRemoteMcp(base: String, password: String, name: String, url: String) {
        val body = JSONObject().put(
            "config",
            JSONObject().put("type", "remote").put("url", url)
        )
        call(
            request(
                "${root(base)}/api/experimental/mcp/${mcpPath(name)}",
                password
            ).put(body.toString().toRequestBody(jsonType)).build()
        )
    }

    suspend fun removeMcp(base: String, password: String, name: String) {
        call(
            request(
                "${root(base)}/api/experimental/mcp/${mcpPath(name)}",
                password
            ).delete().build()
        )
    }

    suspend fun connectMcp(base: String, password: String, name: String, connect: Boolean) {
        val suffix = if (connect) "connect" else "disconnect"
        call(
            request(
                "${root(base)}/api/experimental/mcp/${mcpPath(name)}/$suffix",
                password
            ).post("{}".toRequestBody(jsonType)).build()
        )
    }

    private fun mcpPath(name: String): String =
        java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    suspend fun reload(base: String, password: String) {
        call(
            request("${root(base)}/api/location/reload", password)
                .post("{}".toRequestBody(jsonType))
                .build()
        )
    }

    /**
     * SSE-поток событий OpenCode. Читается в отдельном потоке, потому что
     * OkHttp блокируется на readUtf8Line(). При отмене коллектора call.cancel()
     * гарантированно освобождает поток.
     */
    fun events(base: String, password: String): Flow<JSONObject> = callbackFlow {
        val request = request("${root(base)}/api/event", password)
            .header("Accept", "text/event-stream")
            .build()
        val call = sseClient.newCall(request)
        val thread = Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ApiException(
                            response.code,
                            response.body?.string().orEmpty()
                        )
                    }
                    val source = response.body?.source()
                        ?: throw IOException("Сервер не прислал поток событий")
                    while (!Thread.currentThread().isInterrupted) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.substring(5).trim()
                        if (payload.length < 3 || !payload.startsWith("{")) continue
                        val event = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                        trySend(event)
                    }
                    close()
                }
            } catch (e: Exception) {
                if (!call.isCanceled()) close(e)
            }
        }.apply {
            name = "opencode-pocket-sse"
            isDaemon = true
            start()
        }
        awaitClose {
            call.cancel()
            thread.interrupt()
        }
    }.buffer(Channel.UNLIMITED)
}
