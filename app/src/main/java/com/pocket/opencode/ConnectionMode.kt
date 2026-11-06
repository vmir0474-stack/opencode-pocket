package com.pocket.opencode

/**
 * Выбирает режим подключения. Свежая установка идёт во встроенный сервер,
 * но существующая внешняя настройка не должна ломаться после обновления.
 */
object ConnectionModeResolver {
    fun resolve(hasSavedMode: Boolean, savedMode: String?, hasExternalUrl: Boolean): ServerMode {
        if (hasSavedMode) {
            when (savedMode) {
                "embedded" -> return ServerMode.EMBEDDED
                "external" -> return ServerMode.EXTERNAL
            }
        }
        return if (hasExternalUrl) ServerMode.EXTERNAL else ServerMode.EMBEDDED
    }
}

/**
 * В версии 0.2.0 появились вкладки «Модели» и «Скилы», поэтому старый
 * индекс «Связи» (2) переезжает на 4. Схема хранится рядом с tab.
 */
object TabMigration {
    const val SCHEMA = 2

    fun map(oldTab: Int, schema: Int): Int {
        if (schema < SCHEMA && oldTab == 2) return 4
        return oldTab
    }
}
