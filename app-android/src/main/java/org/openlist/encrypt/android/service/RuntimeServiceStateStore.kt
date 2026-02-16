package org.openlist.encrypt.android.service

import android.content.Context

object RuntimeServiceStateStore {
    private const val PREFS = "runtime_service_state"
    private const val KEY_STATE = "state"
    private const val KEY_DETAIL = "detail"

    private const val STATE_STOPPED = "stopped"
    private const val STATE_STARTING = "starting"
    private const val STATE_RUNNING = "running"
    private const val STATE_DEGRADED = "degraded"
    private const val STATE_STOPPING = "stopping"

    fun markStarting(context: Context, detail: String? = null) = write(context, STATE_STARTING, detail)

    fun markRunning(context: Context, detail: String? = null) = write(context, STATE_RUNNING, detail)

    fun markDegraded(context: Context, detail: String? = null) = write(context, STATE_DEGRADED, detail)

    fun markStopping(context: Context, detail: String? = null) = write(context, STATE_STOPPING, detail)

    fun markStopped(context: Context, detail: String? = null) = write(context, STATE_STOPPED, detail)

    fun read(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATE, STATE_STOPPED)
            .orEmpty()
    }

    fun readDetail(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DETAIL, "")
            .orEmpty()
    }

    private fun write(context: Context, value: String, detail: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, value)
            .putString(KEY_DETAIL, detail.orEmpty())
            .apply()
    }
}
