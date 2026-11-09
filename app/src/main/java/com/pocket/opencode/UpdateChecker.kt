package com.pocket.opencode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Сведения о релизе, готовые для загрузки и установки. */
data class UpdateInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
    val notes: String,
)

fun interface ReleaseFetcher {
    suspend fun text(url: String): String
}

class HttpReleaseFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : ReleaseFetcher {
    override suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "OpenCode-Pocket")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub ответил HTTP ${response.code}")
            response.body?.string() ?: error("Пустой ответ GitHub")
        }
    }
}

/**
 * Проверяет обновления только по нашим GitHub-релизам. Никакого браузера:
 * APK скачивается внутри приложения и передаётся системному установщику.
 */
object UpdateChecker {
    const val RELEASES_URL =
        "https://api.github.com/repos/vmir0474-stack/opencode-pocket/releases/latest"

    suspend fun latest(currentName: String, currentCode: Int): UpdateInfo? =
        check(currentName, currentCode, HttpReleaseFetcher())

    suspend fun check(
        currentName: String,
        currentCode: Int,
        fetcher: ReleaseFetcher,
    ): UpdateInfo? {
        val release = JSONObject(fetcher.text(RELEASES_URL))
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null

        val tag = release.optString("tag_name").trim()
        if (tag.isBlank()) return null
        var versionName = tag.removePrefix("v").removePrefix("V")
        var versionCode = 0

        val assets = release.optJSONArray("assets") ?: JSONArray()
        val versionAsset = findAsset(assets, "version.json")
        if (versionAsset != null) {
            runCatching {
                val url = versionAsset.optString("browser_download_url")
                val json = JSONObject(fetcher.text(url))
                val name = json.optString("versionName").trim()
                if (name.isNotBlank()) versionName = name.removePrefix("v").removePrefix("V")
                versionCode = json.optInt("versionCode", 0)
            }
        }

        val apk = pickApk(assets) ?: return null
        val digest = apk.optString("digest").trim()
            .removePrefix("sha256:")
            .removePrefix("SHA256:")
            .lowercase()
        val info = UpdateInfo(
            tag = tag,
            versionName = versionName,
            versionCode = versionCode,
            apkUrl = apk.optString("browser_download_url"),
            size = apk.optLong("size"),
            sha256 = digest,
            notes = release.optString("body"),
        )
        if (!isNewer(currentName, currentCode, info)) return null
        return info
    }

    fun isNewer(currentName: String, currentCode: Int, info: UpdateInfo): Boolean {
        if (info.versionCode > 0 && currentCode > 0) {
            return info.versionCode > currentCode
        }
        return compareVersions(info.versionName, currentName) > 0
    }

    /** Сравнение версий вида `0.10.0`, `v1.2.3`, `1.0.0-beta.2`. */
    fun compareVersions(a: String, b: String): Int {
        fun split(value: String): Pair<List<Int>, String> {
            val clean = value.trim().removePrefix("v").removePrefix("V")
            val core = clean.substringBefore('-').substringBefore('+')
            val numbers = core.split('.').map { it.toIntOrNull() ?: 0 }
            val pre = if ('-' in clean) clean.substringAfter('-').substringBefore('+') else ""
            return numbers to pre
        }

        val (left, leftPre) = split(a)
        val (right, rightPre) = split(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return if (l > r) 1 else -1
        }
        // 1.0.0 новее, чем 1.0.0-beta; две beta сравниваем строкой.
        if (leftPre == rightPre) return 0
        if (leftPre.isEmpty()) return 1
        if (rightPre.isEmpty()) return -1
        return leftPre.compareTo(rightPre).coerceIn(-1, 1)
    }

    private fun findAsset(assets: JSONArray, name: String): JSONObject? {
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            if (item.optString("name").equals(name, ignoreCase = true)) return item
        }
        return null
    }

    private fun pickApk(assets: JSONArray): JSONObject? {
        val candidates = mutableListOf<JSONObject>()
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            val name = item.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.contains("debug", ignoreCase = true)) continue
            if (item.optString("browser_download_url").isBlank()) continue
            candidates += item
        }
        return candidates.maxByOrNull { it.optLong("size") }
    }
}
