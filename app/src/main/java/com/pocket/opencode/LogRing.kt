package com.pocket.opencode

/**
 * Кольцевой буфер строк лога сервера для экрана «Связь».
 * Потокобезопасен: пишет reader-поток процесса, читает UI.
 */
class LogRing(private val capacity: Int = 400) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun append(line: String) {
        if (capacity <= 0) return
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    @Synchronized
    fun appendAll(newLines: List<String>) {
        newLines.forEach { append(it) }
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() = lines.clear()
}
