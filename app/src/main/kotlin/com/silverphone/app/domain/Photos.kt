package com.silverphone.app.domain

/**
 * A photo that has already been decoded, cropped, scaled and re-encoded to the
 * stored form: a square JPEG no larger than 512 px per side and 128 KiB.
 *
 * [sha256] is the digest of [jpegBytes] exactly as stored, so it can be used both
 * as the image cache key and as the integrity check in an exported archive.
 *
 * Declared as a regular class rather than a data class because ByteArray uses
 * identity equality, which would make a generated equals() quietly wrong.
 */
class NormalizedPhoto(
    val jpegBytes: ByteArray,
    val sha256: String,
    val width: Int,
    val height: Int,
) {
    val byteCount: Int get() = jpegBytes.size
}

/** How an edit changes the stored photo of an existing contact. */
sealed interface PhotoEdit {
    /** Leave the stored photo untouched. */
    data object Keep : PhotoEdit

    /** Replace it with an already normalised image. */
    data class Replace(val photo: NormalizedPhoto) : PhotoEdit

    /** Drop the photo, keeping the contact and its placeholder colour. */
    data object Remove : PhotoEdit
}
