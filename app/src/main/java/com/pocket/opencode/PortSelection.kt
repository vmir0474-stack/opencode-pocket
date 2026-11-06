package com.pocket.opencode

import java.net.InetSocketAddress
import java.net.ServerSocket

/** Ищет свободный локальный порт, по умолчанию начиная с 4096. */
object PortSelection {
    fun choose(preferred: Int = 4096, span: Int = 32): Int {
        for (offset in 0 until span.coerceAtLeast(1)) {
            val port = preferred + offset
            if (port > 65535) break
            runCatching {
                ServerSocket().use { socket ->
                    socket.bind(InetSocketAddress("127.0.0.1", port))
                }
                return port
            }
        }
        return ServerSocket(0).use { it.localPort }
    }
}
