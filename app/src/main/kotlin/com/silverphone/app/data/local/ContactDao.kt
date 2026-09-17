package com.silverphone.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    /**
     * Home list. Joins the photo table only for the digest, so no image bytes are
     * ever read to draw the list.
     */
    @Query(
        """
        SELECT c.id AS id,
               c.displayName AS displayName,
               c.phoneNumber AS phoneNumber,
               c.sortOrder AS sortOrder,
               c.placeholderColor AS placeholderColor,
               p.sha256 AS photoSha256
        FROM contacts AS c
        LEFT JOIN contact_photos AS p ON p.contactId = c.id
        ORDER BY c.sortOrder ASC
        """,
    )
    fun observeList(): Flow<List<ContactListRow>>

    @Query(
        """
        SELECT c.id AS id,
               c.displayName AS displayName,
               c.phoneNumber AS phoneNumber,
               c.sortOrder AS sortOrder,
               c.placeholderColor AS placeholderColor,
               p.sha256 AS photoSha256
        FROM contacts AS c
        LEFT JOIN contact_photos AS p ON p.contactId = c.id
        ORDER BY c.sortOrder ASC
        """,
    )
    suspend fun listOnce(): List<ContactListRow>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun findById(id: String): ContactEntity?

    /** Single contact with its photo digest, for the dial path's re-read. */
    @Query(
        """
        SELECT c.id AS id,
               c.displayName AS displayName,
               c.phoneNumber AS phoneNumber,
               c.sortOrder AS sortOrder,
               c.placeholderColor AS placeholderColor,
               p.sha256 AS photoSha256
        FROM contacts AS c
        LEFT JOIN contact_photos AS p ON p.contactId = c.id
        WHERE c.id = :id
        """,
    )
    suspend fun findListRow(id: String): ContactListRow?

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM contacts")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(sortOrder) FROM contacts")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT phoneNumber FROM contacts")
    suspend fun allPhoneNumbers(): List<String>

    @Query("SELECT * FROM contacts ORDER BY sortOrder ASC")
    suspend fun all(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE sortOrder = :sortOrder LIMIT 1")
    suspend fun findBySortOrder(sortOrder: Int): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}
