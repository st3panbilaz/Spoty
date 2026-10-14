package com.example.spoty.data

import android.content.Context

object ThemeStore {

    private const val PREFS_NAME = "spoty_prefs"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_SHOW_VOLUME = "show_volume_slider"

    fun isDark(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK, true)
    }

    fun setDark(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK, value).apply()
    }

    fun isShowVolume(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_VOLUME, true)
    }

    fun setShowVolume(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_VOLUME, value).apply()
    }
}