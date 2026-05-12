package com.phonebook.senior.data.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.phonebook.senior.data.model.AppSettings

object DatabaseInitializer {

    fun initialize(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val settings = db.settingsDao().getSettingsOnce()
            if (settings == null) {
                db.settingsDao().insertOrUpdate(AppSettings(timeoutSeconds = 10))
            }
        }
    }
}