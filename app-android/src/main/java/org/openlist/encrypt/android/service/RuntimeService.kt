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
import org.openlist.encrypt.android.diagnostics.RuntimeLogStore

class RuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logStore by lazy { RuntimeLogStore(applicationContext) }
    private val coordinator by lazy {
        RuntimeCoordinator(DefaultRuntimeProcessController(applicationContext))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                RuntimeServiceStateStore.markStopping(applicationContext, "stop requested")
                logStore.appendApp("runtime.service", "stopping")
                scope.launch {
                    coordinator.stopAll()
                    RuntimeServiceStateStore.markStopped(applicationContext, "stopped by request")
                    logStore.appendApp("runtime.service", "stopped")
                    updateForeground("Runtime stopped")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }

            ACTION_START -> {
                RuntimeServiceStateStore.markStarting(applicationContext, "start requested")
                logStore.appendApp("runtime.service", "starting")
                startForeground(NOTIFICATION_ID, buildNotification("Starting runtime"))
                scope.launch {
                    coordinator.startAll()
                    val state = coordinator.currentState()
                    if (state == RuntimeState.Running) {
                        RuntimeServiceStateStore.markRunning(applicationContext, "openlist healthy")
                    } else {
                        val detail = coordinator.lastErrorDetail().ifBlank { "runtime degraded" }
                        RuntimeServiceStateStore.markDegraded(applicationContext, detail)
                    }
                    logStore.appendApp("runtime.service", "state:$state")
                    updateForeground("Runtime: $state")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        RuntimeServiceStateStore.markStopped(applicationContext, "service destroyed")
        logStore.appendApp("runtime.service", "destroyed")
        scope.launch {
            coordinator.stopAll()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun updateForeground(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
