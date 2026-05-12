package com.phonebook.senior.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.phonebook.senior.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsOnce(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettings)

    @Query("UPDATE app_settings SET guideVideoUri = :uri WHERE id = 1")
    suspend fun updateGuideVideo(uri: String)

    @Query("UPDATE app_settings SET guideContentEnabled = :enabled WHERE id = 1")
    suspend fun updateGuideContentEnabled(enabled: Boolean)

    @Query("UPDATE app_settings SET guideContentMode = :mode WHERE id = 1")
    suspend fun updateGuideContentMode(mode: String)

    @Query("UPDATE app_settings SET guideImageUri = :uri WHERE id = 1")
    suspend fun updateGuideImage(uri: String)

    @Query("UPDATE app_settings SET guideAudioUri = :uri WHERE id = 1")
    suspend fun updateGuideAudio(uri: String)

    @Query("UPDATE app_settings SET autoRecoveryEnabled = :enabled WHERE id = 1")
    suspend fun updateAutoRecovery(enabled: Boolean)

    @Query("UPDATE app_settings SET timeoutSeconds = :seconds WHERE id = 1")
    suspend fun updateTimeout(seconds: Int)

    @Query("UPDATE app_settings SET appearanceMode = :mode WHERE id = 1")
    suspend fun updateAppearanceMode(mode: String)

    @Query("UPDATE app_settings SET fontSizeMode = :mode WHERE id = 1")
    suspend fun updateFontSizeMode(mode: String)

    @Query("UPDATE app_settings SET easyModeEnabled = :enabled WHERE id = 1")
    suspend fun updateEasyMode(enabled: Boolean)

    @Query("UPDATE app_settings SET easyModeSwipeHintEnabled = :enabled WHERE id = 1")
    suspend fun updateEasyModeSwipeHint(enabled: Boolean)

    @Query("UPDATE app_settings SET preferredSimAccount = :account WHERE id = 1")
    suspend fun updatePreferredSimAccount(account: String)
}
