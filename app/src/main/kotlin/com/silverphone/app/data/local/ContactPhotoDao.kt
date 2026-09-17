package com.silverphone.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactPhotoDao {

    @Query("SELECT * FROM contact_photos WHERE contactId = :contactId")
    suspend fun find(contactId: String): ContactPhotoEntity?

    @Query("SELECT jpegBytes FROM contact_photos WHERE contactId = :contactId")
    suspend fun bytes(contactId: String): ByteArray?

    /** How many contacts currently have a stored photo. */
    @Query("SELECT COUNT(*) FROM contact_photos")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM contact_photos")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(photo: ContactPhotoEntity)

    @Query("DELETE FROM contact_photos WHERE contactId = :contactId")
    suspend fun delete(contactId: String)

    @Query("DELETE FROM contact_photos")
    suspend fun deleteAll()

    /** Emits when the set of photos or their digests changes, for cache invalidation. */
    @Query("SELECT contactId || ':' || sha256 FROM contact_photos ORDER BY contactId")
    fun observePhotoKeys(): Flow<List<String>>
}
