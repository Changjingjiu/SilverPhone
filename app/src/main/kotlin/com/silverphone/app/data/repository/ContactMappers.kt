package com.silverphone.app.data.repository

import com.silverphone.app.data.local.AppSettingsEntity
import com.silverphone.app.data.local.ContactEntity
import com.silverphone.app.data.local.ContactListRow
import com.silverphone.app.data.local.ContactPhotoEntity
import com.silverphone.app.domain.AppSettings
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.domain.NormalizedPhoto
import com.silverphone.app.domain.PlaceholderColor

/**
 * Stored values are mapped back into domain values here.
 *
 * An unrecognised placeholder colour falls back to the default instead of
 * failing: the home screen has to draw something, and a card that suddenly
 * disappears would be worse for the user than one drawn in the default colour.
 * The database cannot normally contain such a value, because every write path
 * validates first.
 */
internal fun ContactListRow.toContact(): Contact = Contact(
    id = id,
    displayName = displayName,
    phoneNumber = phoneNumber,
    sortOrder = sortOrder,
    placeholderColor = PlaceholderColor.fromStorage(placeholderColor)
        ?: PlaceholderColor.DEFAULT,
    photoSha256 = photoSha256,
)

internal fun ContactEntity.toContact(photoSha256: String? = null): Contact = Contact(
    id = id,
    displayName = displayName,
    phoneNumber = phoneNumber,
    sortOrder = sortOrder,
    placeholderColor = PlaceholderColor.fromStorage(placeholderColor)
        ?: PlaceholderColor.DEFAULT,
    photoSha256 = photoSha256,
)

internal fun AppSettingsEntity.toAppSettings(): AppSettings = AppSettings(
    fontPreset = FontPreset.fromStorage(fontPreset) ?: FontPreset.DEFAULT,
    contactsRevision = contactsRevision,
    languageTag = languageTag,
    countryCode = countryCode,
)

internal fun NormalizedPhoto.toEntity(contactId: String): ContactPhotoEntity =
    ContactPhotoEntity(
        contactId = contactId,
        jpegBytes = jpegBytes,
        sha256 = sha256,
        width = width,
        height = height,
    )
