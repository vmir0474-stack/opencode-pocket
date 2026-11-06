package com.pocket.opencode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Держит встроенный сервер живым, пока пользователь явно не остановит его
 * из уведомления или приложения. Свайп из recents сервис не убивает.
 */
class ServerService : Service() {
    companion object {
        const val ACTION_START = "com.pocket.opencode.action.START"
        const val ACTION_STOP = "com.pocket.opencode.action.STOP"
        const val ACTION_RESTART = "com.pocket.opencode.action.RESTART"
        private const val CHANNEL_ID = "opencode_server"
        private const val NOTIFICATION_ID = 4096

        fun start(context: Context) = send(context, ACTION_START)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun restart(context: Context) = send(context, ACTION_RESTART)

        private fun send(context: Context, action: String) {
            val intent = Intent(context, ServerService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing: Job? = null
    private var explicitStop = false

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenCode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Встроенный сервер OpenCode"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                explicitStop = true
                EmbeddedServer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RESTART -> {
                explicitStop = false
                startForeground(NOTIFICATION_ID, buildNotification(EmbeddedServer.status.value))
                observe()
                EmbeddedServer.restart(this, workspace())
            }

            else -> {
                explicitStop = false
                startForeground(NOTIFICATION_ID, buildNotification(EmbeddedServer.status.value))
                observe()
                EmbeddedServer.start(this, workspace())
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observing?.cancel()
        scope.cancel()
        // При явной остановке процесс уже погашен. Если сервис убила система,
        // START_STICKY поднимет его снова — не трогаем дочерний процесс зря.
        if (explicitStop) EmbeddedServer.stop()
        super.onDestroy()
    }

    private fun workspace(): File =
        File(SettingsStore(this).workspacePath.ifBlank { "/sdcard/OpenCode" })

    private fun observe() {
        observing?.cancel()
        observing = scope.launch {
            EmbeddedServer.status.collect { status ->
                runCatching {
                    NotificationManagerCompat.from(this@ServerService)
                        .notify(NOTIFICATION_ID, buildNotification(status))
                }
            }
        }
    }

    private fun buildNotification(status: EmbeddedStatus): Notification {
        val text = when (status.state) {
            EmbeddedState.STARTING -> "Запускаю OpenCode…"
            EmbeddedState.RUNNING -> "OpenCode работает · порт ${status.port}"
            EmbeddedState.ERROR -> "Ошибка: ${status.error.lineSequence().firstOrNull().orEmpty().take(160)}"
            EmbeddedState.STOPPED -> "OpenCode остановлен"
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OpenCode Pocket")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Стоп", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
