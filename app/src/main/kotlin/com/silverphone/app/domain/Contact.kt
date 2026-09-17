package com.silverphone.app.domain

/**
 * A contact as the home screen sees it.
 *
 * Photo bytes are deliberately absent: the list must never load every stored
 * image just to show cards. [photoSha256] doubles as the "has a photo" flag and
 * as the image cache key, so no separate flag can drift out of sync.
 */
data class Contact(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val sortOrder: Int,
    val placeholderColor: PlaceholderColor,
    val photoSha256: String?,
) {
    val hasPhoto: Boolean get() = photoSha256 != null
}

/** Persisted application settings. There is exactly one row of these. */
data class AppSettings(
    val fontPreset: FontPreset,
    val contactsRevision: Long,
    /** Empty means "follow the system language". */
    val languageTag: String,
    /** Prefix for numbers stored without one. Empty means "add nothing". */
    val countryCode: String,
) {
    companion object {
        val INITIAL = AppSettings(
            fontPreset = FontPreset.DEFAULT,
            contactsRevision = 0L,
            languageTag = "",
            countryCode = CountryCode.DEFAULT,
        )
    }
}

/** Validated user input for creating or updating a contact. */
data class ContactDraft(
    val displayName: String,
    val phoneNumber: String,
    val placeholderColor: PlaceholderColor,
)
