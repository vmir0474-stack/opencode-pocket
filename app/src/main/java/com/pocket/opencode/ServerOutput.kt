package com.pocket.opencode

/**
 * OpenCode печатает пароль и адрес в stdout. Строки могут приходить с
 * префиксами от обёртки/логгера, поэтому ищем подстроку, а не якорим строку.
 */
object ServerOutput {
    private val password = Regex("""server password\s+(\S+)""")
    private val listening = Regex("""server listening on (http://\S+)""")

    fun password(line: String): String? =
        password.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    fun listeningUrl(line: String): String? =
        listening.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}
