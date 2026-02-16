package org.openlist.encrypt.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.openlist.encrypt.android.R

class RuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coordinator by lazy {
        RuntimeCoordinator(DefaultRuntimeProcessController(applicationContext))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                scope.launch {
                    coordinator.stopAll()
                    updateForeground("Runtime stopped")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }

            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Starting runtime"))
                scope.launch {
                    coordinator.startAll()
                    updateForeground("Runtime: ${coordinator.currentState()}")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch {
            coordinator.stopAll()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun updateForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Runtime", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    companion object {
        const val ACTION_START = "org.openlist.encrypt.android.service.action.START"
        const val ACTION_STOP = "org.openlist.encrypt.android.service.action.STOP"

        private const val CHANNEL_ID = "openencrypt_runtime"
        private const val NOTIFICATION_ID = 1001
    }
}
