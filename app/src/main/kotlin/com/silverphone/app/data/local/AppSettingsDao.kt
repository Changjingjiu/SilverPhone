package com.silverphone.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = ${AppSettingsEntity.SINGLETON_ID}")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = ${AppSettingsEntity.SINGLETON_ID}")
    suspend fun get(): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)

    @Query("UPDATE app_settings SET fontPreset = :preset WHERE id = ${AppSettingsEntity.SINGLETON_ID}")
    suspend fun setFontPreset(preset: String)

    @Query("UPDATE app_settings SET languageTag = :tag WHERE id = ${AppSettingsEntity.SINGLETON_ID}")
    suspend fun setLanguageTag(tag: String)

    @Query("UPDATE app_settings SET countryCode = :code WHERE id = ${AppSettingsEntity.SINGLETON_ID}")
    suspend fun setCountryCode(code: String)

    /**
     * Bumps the revision by one. Callers must run this inside the same transaction
     * as the contact change it describes, so the revision can never advance for a
     * write that rolled back.
     */
    @Query(
        """
        UPDATE app_settings
        SET contactsRevision = contactsRevision + 1
        WHERE id = ${AppSettingsEntity.SINGLETON_ID}
        """,
    )
    suspend fun incrementRevision()
}
