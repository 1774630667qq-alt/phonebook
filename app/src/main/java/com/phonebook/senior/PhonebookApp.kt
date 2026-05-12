package com.phonebook.senior

import android.app.Application
import com.phonebook.senior.data.db.DatabaseInitializer
import com.phonebook.senior.ui.theme.AppearanceMode

class PhonebookApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppearanceMode.applyFromPreferences(this)
        DatabaseInitializer.initialize(this)
    }
}
