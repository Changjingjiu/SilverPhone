package com.silverphone.app.platform.photos

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.IOException
import okio.Buffer

/**
 * Identity of the image to load: which contact, and which revision of their photo.
 *
 * Only the key material is carried, never the bytes: the model stays small and
 * comparable, and the bytes are read at fetch time by our own fetcher.
 */
data class ContactPhotoModel(
    val contactId: String,
    val sha256: String,
)

/** Reads stored photo bytes for one contact. */
fun interface PhotoBytesSource {
    suspend fun photoBytes(contactId: String): ByteArray?
}

/**
 * Cache key for a stored photo.
 *
 * Keying on contact id plus the digest of the stored bytes is what makes a
 * replaced photo show up immediately: the digest changes, so the old entry can
 * never be served for the new image.
 */
class ContactPhotoKeyer : Keyer<ContactPhotoModel> {
    override fun key(data: ContactPhotoModel, options: Options): String =
        "contact-photo:${data.contactId}:${data.sha256}"
}

/**
 * Reads the JPEG blob for [ContactPhotoModel] straight out of the database.
 *
 * This wires an existing data source into Coil; decoding, scaling and caching
 * stay Coil's job.
 */
class ContactPhotoFetcher(
    private val data: ContactPhotoModel,
    private val source: PhotoBytesSource,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = source.photoBytes(data.contactId)
            ?: throw IOException("no stored photo for contact ${data.contactId}")
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = options.fileSystem,
            ),
            mimeType = JPEG_MIME_TYPE,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val source: PhotoBytesSource) : Fetcher.Factory<ContactPhotoModel> {
        override fun create(
            data: ContactPhotoModel,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ContactPhotoFetcher(data, source, options)
    }

    companion object {
        const val JPEG_MIME_TYPE: String = "image/jpeg"
    }
}
