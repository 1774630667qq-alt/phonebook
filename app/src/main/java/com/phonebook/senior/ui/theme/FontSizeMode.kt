package com.phonebook.senior.ui.theme

import android.content.Context
import android.content.res.Configuration
import com.phonebook.senior.data.model.AppSettings

object FontSizeMode {
    private const val PREFS_NAME = "display"
    private const val KEY_FONT_SIZE_MODE = "font_size_mode"
    private const val LARGE_FONT_SCALE = 1.22f

    fun wrap(context: Context): Context {
        val mode = current(context)
        val fontScale = if (mode == AppSettings.FONT_SIZE_LARGE) LARGE_FONT_SCALE else 1f
        val configuration = Configuration(context.resources.configuration).apply {
            this.fontScale = fontScale
        }
        return context.createConfigurationContext(configuration)
    }

    fun current(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FONT_SIZE_MODE, AppSettings.FONT_SIZE_STANDARD)
            ?: AppSettings.FONT_SIZE_STANDARD
    }

    fun persist(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FONT_SIZE_MODE, mode)
            .apply()
    }
}
