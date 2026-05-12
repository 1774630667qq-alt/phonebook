package com.phonebook.senior.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.phonebook.senior.data.model.AppSettings

object AppearanceMode {
    private const val PREFS_NAME = "appearance"
    private const val KEY_MODE = "mode"

    fun applyFromPreferences(context: Context) {
        val mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, AppSettings.APPEARANCE_LIGHT)
            ?: AppSettings.APPEARANCE_LIGHT
        apply(mode)
    }

    fun persistAndApply(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode)
            .apply()
        apply(mode)
    }

    private fun apply(mode: String) {
        val nightMode = if (mode == AppSettings.APPEARANCE_DARK) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
