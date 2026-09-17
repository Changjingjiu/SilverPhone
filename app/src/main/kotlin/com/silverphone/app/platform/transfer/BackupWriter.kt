package com.silverphone.app.platform.transfer

import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.Sha256
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes one contact exchange archive.
 *
 * The archive is built in the private cache, one photo at a time, so the whole
 * set of blobs and the whole ZIP are never held in memory together. A failure
 * deletes the partial file and its directory, so a half-written archive can never
 * be offered for sharing.
 */
class BackupWriter(
    private val repository: ContactRepository,
    private val dirs: TransferDirs,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface Result {
        class Written(
            val file: File,
            val contactCount: Int,
            val photoCount: Int,
        ) : Result

        /** Nothing to export: an empty archive must not be produced. */
        data object NoContacts : Result

        data class Failed(val cause: Throwable) : Result
    }

    /**
     * [filePrefix] becomes the start of the file name. It is passed in rather than
     * fixed here because it is the one part of the archive a person reads, and the
     * person reading it reads the interface language.
     */
    suspend fun write(filePrefix: String): Result = withContext(Dispatchers.IO) {
        val snapshot = repository.snapshot()
        if (snapshot.contacts.isEmpty()) {
            return@withContext Result.NoContacts
        }

        val directory = dirs.newExportDirectory()
        val file = File(directory, fileName(filePrefix, clock()))

        try {
            var photoCount = 0
            val entries = ArrayList<BackupContact>(snapshot.contacts.size)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
                for ((index, contact) in snapshot.contacts.withIndex()) {
                    val photo = if (contact.hasPhoto) {
                        writePhoto(zip, contact.id, repository.photoBytes(contact.id), contact.photoSha256)
                    } else {
                        null
                    }
                    if (photo != null) photoCount++
                    entries.add(
                        BackupContact(
                            id = contact.id,
                            displayName = contact.displayName,
                            phoneNumber = contact.phoneNumber,
                            // The array is already ordered; writing the index keeps
                            // "sortOrder equals position" true by construction.
                            sortOrder = index,
                            placeholderColor = contact.placeholderColor.name,
                            photo = photo,
                        ),
                    )
                }

                val manifest = BackupManifest(
                    format = BackupProtocol.FORMAT_MARKER,
                    schemaVersion = BackupProtocol.SCHEMA_VERSION,
                    exportedAt = isoUtc(clock()),
                    appVersion = appVersion,
                    contacts = entries,
                )
                zip.putNextEntry(ZipEntry(BackupProtocol.MANIFEST_ENTRY))
                zip.write(BackupJson.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            Result.Written(
                file = file,
                contactCount = snapshot.contacts.size,
                photoCount = photoCount,
            )
        } catch (failure: Throwable) {
            file.delete()
            directory.deleteRecursively()
            Result.Failed(failure)
        }
    }

    private fun writePhoto(
        zip: ZipOutputStream,
        contactId: String,
        bytes: ByteArray?,
        expectedDigest: String?,
    ): BackupPhoto? {
        if (bytes == null) return null
        val digest = Sha256.hex(bytes)
        // A stored digest that disagrees with the stored bytes means the row is
        // inconsistent; exporting it would produce an archive that fails its own
        // integrity check on the other phone.
        if (expectedDigest != null && digest != expectedDigest) {
            throw IOException("stored photo digest does not match its bytes for $contactId")
        }
        val path = BackupProtocol.photoPath(contactId)
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
        return BackupPhoto(
            path = path,
            mimeType = BackupProtocol.PHOTO_MIME_TYPE,
            byteCount = bytes.size,
            sha256 = digest,
        )
    }

    companion object {
        /** The extension is part of the contract; the rest of the name is only for people. */
        const val FILE_EXTENSION: String = ".zip"

        fun fileName(filePrefix: String, now: Long): String =
            filePrefix + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now)) + FILE_EXTENSION

        fun isoUtc(now: Long): String {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(Date(now))
        }
    }
}
