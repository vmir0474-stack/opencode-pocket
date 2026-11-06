package com.pocket.opencode

/** Кольцевой буфер событий без Android-зависимостей. */
class DebugRing(private val capacity: Int = 500) {
    private val items = ArrayDeque<DebugEvent>()

    @Synchronized
    fun add(event: DebugEvent) {
        items.addLast(event)
        while (items.size > capacity) items.removeFirst()
    }

    @Synchronized
    fun recent(limit: Int): List<DebugEvent> {
        if (limit <= 0) return emptyList()
        return items.takeLast(limit)
    }

    @Synchronized
    fun clear() = items.clear()

    @Synchronized
    fun size(): Int = items.size
}
