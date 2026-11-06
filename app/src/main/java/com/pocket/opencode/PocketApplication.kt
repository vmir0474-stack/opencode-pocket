package com.pocket.opencode

import android.app.Application

class PocketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { DebugLog.error("crash", error) }
            previous?.uncaughtException(thread, error)
        }
        runCatching { DebugBridge.start(this) }
            .onFailure { DebugLog.error("debug", it) }
    }
}
