package com.silverphone.app.domain

/**
 * Every quantitative limit in one place, so the rules can be tested directly and
 * so no screen can invent its own slightly different number.
 *
 * These are product-level engineering limits, not Android platform limits. Sizes
 * are in bytes; MiB is 1024 * 1024 and KiB is 1024.
 */
object ContactLimits {

    /** Maximum contacts stored on this device, and in one imported archive. */
    const val MAX_CONTACTS: Int = 500

    /** Maximum size of one stored (or archived) photo. */
    const val MAX_PHOTO_BYTES: Int = 128 * 1024

    /** Maximum side length of a stored square photo, in pixels. */
    const val MAX_PHOTO_EDGE_PX: Int = 512

    /** Largest source image file we will even try to decode. */
    const val MAX_SOURCE_PHOTO_BYTES: Long = 20L * 1024 * 1024

    /** Largest source image, in pixels, before sampling. */
    const val MAX_SOURCE_PHOTO_PIXELS: Long = 24_000_000L

    /** manifest.json size limit, UTF-8. */
    const val MAX_MANIFEST_BYTES: Long = 1024L * 1024

    /** Largest archive we will read, by actual bytes read. */
    const val MAX_ARCHIVE_BYTES: Long = 80L * 1024 * 1024

    /** Largest total uncompressed content, counted as it is read. */
    const val MAX_ARCHIVE_UNCOMPRESSED_BYTES: Long = 70L * 1024 * 1024

    /** One manifest plus at most MAX_CONTACTS photos. */
    const val MAX_ARCHIVE_ENTRIES: Int = MAX_CONTACTS + 1

    /** How long a generated export stays in the share cache before cleanup. */
    const val EXPORT_CACHE_TTL_MILLIS: Long = 24L * 60 * 60 * 1000
}
