package com.silverphone.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["phoneNumber"]),
        Index(value = ["sortOrder"]),
    ],
)
data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val phoneNumber: String,
    val sortOrder: Int,
    val placeholderColor: String,
)

/**
 * One stored photo per contact, as a JPEG blob.
 *
 * Photos live in their own table so the home query can read contact metadata
 * without pulling any image bytes, and so a contact and its photo can still be
 * committed inside a single transaction.
 *
 * A regular class, not a data class: ByteArray compares by identity, so the
 * generated equals()/hashCode() would be misleading. Identity is the primary key.
 */
@Entity(
    tableName = "contact_photos",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class ContactPhotoEntity(
    @PrimaryKey val contactId: String,
    val jpegBytes: ByteArray,
    val sha256: String,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactPhotoEntity) return false
        return contactId == other.contactId
    }

    override fun hashCode(): Int = contactId.hashCode()
}

/** Exactly one row (id = 1) holds the app-wide preferences. */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val fontPreset: String = com.silverphone.app.domain.FontPreset.DEFAULT.name,
    val contactsRevision: Long = 0L,
    /** Empty means "follow the system language"; otherwise a language tag. */
    val languageTag: String = "",
    /** Prefix added to numbers stored without one. Empty means "add nothing". */
    val countryCode: String = com.silverphone.app.domain.CountryCode.DEFAULT,
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}

/**
 * Home-screen projection: contact metadata plus the photo digest, never the
 * image bytes.
 */
data class ContactListRow(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val sortOrder: Int,
    @ColumnInfo(name = "placeholderColor") val placeholderColor: String,
    val photoSha256: String?,
)
