package org.openlist.encrypt.android.service

import android.content.Context

object RuntimeServiceStateStore {
    private const val PREFS = "runtime_service_state"
    private const val KEY_STATE = "state"

    private const val STATE_STOPPED = "stopped"
    private const val STATE_STARTING = "starting"
    private const val STATE_RUNNING = "running"
    private const val STATE_DEGRADED = "degraded"
    private const val STATE_STOPPING = "stopping"

    fun markStarting(context: Context) = write(context, STATE_STARTING)

    fun markRunning(context: Context) = write(context, STATE_RUNNING)

    fun markDegraded(context: Context) = write(context, STATE_DEGRADED)

    fun markStopping(context: Context) = write(context, STATE_STOPPING)

    fun markStopped(context: Context) = write(context, STATE_STOPPED)

    fun read(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATE, STATE_STOPPED)
            .orEmpty()
    }

    private fun write(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, value)
            .apply()
    }
}
