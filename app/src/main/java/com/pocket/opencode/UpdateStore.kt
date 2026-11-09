package com.pocket.opencode

import android.content.Context

/**
 * Состояние проверки обновлений: чтобы не дёргать GitHub при каждом старте
 * и не показывать один и тот же релиз после «Позже».
 */
class UpdateStore(context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var lastCheck: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    var skippedTag: String
        get() = prefs.getString(KEY_SKIPPED, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SKIPPED, value).apply()

    fun shouldCheck(now: Long = System.currentTimeMillis()): Boolean =
        now - lastCheck >= INTERVAL_MS

    fun markChecked(now: Long = System.currentTimeMillis()) {
        lastCheck = now
    }

    fun skip(tag: String) {
        skippedTag = tag
    }

    fun clearSkip() {
        skippedTag = ""
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_tag"
        const val INTERVAL_MS = 12L * 60 * 60 * 1000
    }
}
