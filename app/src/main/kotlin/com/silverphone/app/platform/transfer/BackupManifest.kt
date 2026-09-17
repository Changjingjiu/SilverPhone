package com.silverphone.app.platform.transfer

import kotlinx.serialization.Serializable

/**
 * The v1 contact exchange format.
 *
 * These are the exact wire names from the protocol document; they are not renamed
 * to match UI wording. Decoding is strict (see [BackupJson]): a missing field, an
 * unknown field, a wrong type or an unsupported schema version is a rejection,
 * never a guess.
 */
@Serializable
data class BackupManifest(
    /** Fixed marker so a random JSON file cannot be mistaken for a backup. */
    val format: String,
    /** Exactly 1. No other value is accepted, and none is "guessed at". */
    val schemaVersion: Int,
    /** ISO 8601 UTC, e.g. 2026-09-17T08:00:00Z. */
    val exportedAt: String,
    /** Informational only; never used in place of the schema version. */
    val appVersion: String,
    val contacts: List<BackupContact>,
)

@Serializable
data class BackupContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val sortOrder: Int,
    val placeholderColor: String,
    /**
     * Must be present and must be null when there is no photo. The absence of the
     * field is a protocol error rather than an implicit "no photo".
     */
    val photo: BackupPhoto?,
)

@Serializable
data class BackupPhoto(
    val path: String,
    val mimeType: String,
    val byteCount: Int,
    /** Digest of the archived bytes; detects corruption, is not a signature. */
    val sha256: String,
)

object BackupProtocol {
    const val FORMAT_MARKER: String = "silverphone-backup"
    const val SCHEMA_VERSION: Int = 1
    const val MANIFEST_ENTRY: String = "manifest.json"
    const val PHOTO_DIRECTORY: String = "photos"
    const val PHOTO_MIME_TYPE: String = "image/jpeg"
    const val PHOTO_EXTENSION: String = ".jpg"

    /** Archive path for one contact's photo. Derived from the id, never from user input. */
    fun photoPath(contactId: String): String = "$PHOTO_DIRECTORY/$contactId$PHOTO_EXTENSION"

    /** True when [entryName] is one of the two entry shapes the protocol allows. */
    fun isAllowedEntry(entryName: String): Boolean {
        if (entryName == MANIFEST_ENTRY) return true
        val prefix = "$PHOTO_DIRECTORY/"
        if (!entryName.startsWith(prefix)) return false
        val remainder = entryName.substring(prefix.length)
        // Exactly one path segment: a deeper path is not part of the protocol.
        return remainder.isNotEmpty() && !remainder.contains('/')
    }
}
