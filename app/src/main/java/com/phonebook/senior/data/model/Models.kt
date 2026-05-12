package com.phonebook.senior.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String,
    val relationship: String = "",
    val photoUri: String = "",
    val voiceUri: String = "",
    val orderIndex: Int = 0
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val guideVideoUri: String = "",
    val guideContentEnabled: Boolean = false,
    val guideContentMode: String = GUIDE_MODE_PHOTO,
    val guideImageUri: String = "",
    val guideAudioUri: String = "",
    val autoRecoveryEnabled: Boolean = true,
    val timeoutSeconds: Int = 10,
    val appearanceMode: String = APPEARANCE_LIGHT
) {
    companion object {
        const val GUIDE_MODE_PHOTO = "photo"
        const val GUIDE_MODE_VIDEO = "video"
        const val APPEARANCE_LIGHT = "light"
        const val APPEARANCE_DARK = "dark"
    }
}
