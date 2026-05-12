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
    val orderIndex: Int = 0,
    val mediaMode: String = MEDIA_MODE_PHOTO,
    val imageUris: String = "",
    val videoUri: String = "",
    val photoDisplayMode: String = PHOTO_DISPLAY_CAROUSEL,
    val photoCarouselIntervalSeconds: Int = 9
) {
    companion object {
        const val MEDIA_MODE_PHOTO = "photo"
        const val MEDIA_MODE_VIDEO = "video"
        const val PHOTO_DISPLAY_CAROUSEL = "carousel"
        const val PHOTO_DISPLAY_RANDOM = "random"
    }
}

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val guideVideoUri: String = "",
    val guideContentEnabled: Boolean = false,
    val guideContentMode: String = GUIDE_MODE_PHOTO,
    val guideImageUri: String = "",
    val guideAudioUri: String = "",
    val guideImageUris: String = "",
    val guidePhotoDisplayMode: String = GUIDE_PHOTO_DISPLAY_CAROUSEL,
    val guidePhotoCarouselIntervalSeconds: Int = 9,
    val autoRecoveryEnabled: Boolean = true,
    val timeoutSeconds: Int = 10,
    val appearanceMode: String = APPEARANCE_LIGHT,
    val fontSizeMode: String = FONT_SIZE_STANDARD,
    val easyModeEnabled: Boolean = false,
    val easyModeSwipeHintEnabled: Boolean = true,
    val preferredSimAccount: String = ""
) {
    companion object {
        const val GUIDE_MODE_PHOTO = "photo"
        const val GUIDE_MODE_VIDEO = "video"
        const val GUIDE_PHOTO_DISPLAY_CAROUSEL = "carousel"
        const val GUIDE_PHOTO_DISPLAY_RANDOM = "random"
        const val APPEARANCE_LIGHT = "light"
        const val APPEARANCE_DARK = "dark"
        const val FONT_SIZE_STANDARD = "standard"
        const val FONT_SIZE_LARGE = "large"
    }
}
