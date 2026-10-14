package com.example.spoty.data

import android.content.Context

/**
 * Хранит пользовательские настройки в SharedPreferences.
 */
object ThemeStore {

    private const val PREFS_NAME = "spoty_prefs"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_SHOW_VOLUME = "show_volume_slider"

    /** Тёмная тема включена? По умолчанию — да. */
    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true)

    fun setDark(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, value).apply()
    }

    /** Показывать ли ползунок громкости в полноэкранном плеере. */
    fun isShowVolume(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_VOLUME, true)

    fun setShowVolume(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_VOLUME, value).apply()
    }
}