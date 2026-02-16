package org.openlist.encrypt.android.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeModeStore {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val PREFS = "ui_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun read(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    fun write(context: Context, mode: Int): Boolean {
        val clamped = when (mode) {
            MODE_LIGHT, MODE_DARK -> mode
            else -> MODE_SYSTEM
        }
        val prev = read(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, clamped)
            .apply()
        return prev != clamped
    }

    fun apply(context: Context) {
        val mode = read(context)
        val delegateMode = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode)
    }
}
