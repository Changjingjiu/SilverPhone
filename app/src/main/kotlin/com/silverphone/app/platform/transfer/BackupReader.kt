package com.silverphone.app.platform.transfer

import android.content.ContentResolver
import android.net.Uri
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.DisplayNameRules
import com.silverphone.app.domain.ImportedContact
import com.silverphone.app.domain.NormalizedPhoto
import com.silverphone.app.domain.PhoneNumberRules
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.domain.Sha256
import com.silverphone.app.platform.photos.PhotoNormalizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and fully validates one exchange archive before anything is written.
 *
 * Every limit is counted from the bytes actually read, never from the size the
 * provider claims or the size the ZIP header declares. Entry names are only ever
 * used to match entries against the manifest; files on disk are named by this
 * class, so the archive can never steer a write to a path of its choosing.
 *
 * Any single problem rejects the whole archive. A partially usable archive is not
 * a thing: the family would have no way to tell which relatives arrived.
 */
class BackupReader(
    private val contentResolver: ContentResolver,
    private val dirs: TransferDirs,
    private val photoNormalizer: PhotoNormalizer,
) {

    sealed interface Result {
        class Ready(
            val contacts: List<ImportedContact>,
            val exportedAt: String,
            val appVersion: String,
            val photoCount: Int,
        ) : Result

        data class Rejected(val reason: Reason) : Result
    }

    enum class Reason {
        UNREADABLE,
        TOO_LARGE,
        NOT_A_ZIP,
        TOO_MANY_ENTRIES,
        UNSAFE_ENTRY_NAME,
        DUPLICATE_ENTRY,
        ENTRY_TOO_LARGE,
        UNEXPECTED_ENTRY,
        MANIFEST_MISSING,

        /** The manifest did not parse as the exact protocol shape. */
        MANIFEST_INVALID,

        /** A schema version this build does not implement. */
        UNSUPPORTED_VERSION,
        EMPTY_CONTACTS,
        TOO_MANY_CONTACTS,
        INVALID_CONTACT,

        /** Declared in the manifest but absent from the archive. */
        PHOTO_MISSING,

        /** Present but not matching its declared size or digest. */
        PHOTO_MISMATCH,

        /** Present and matching, but not a usable square JPEG. */
        PHOTO_INVALID,
        IO_FAILURE,
    }

    suspend fun preflight(source: Uri): Result = withContext(Dispatchers.IO) {
        val staging = dirs.newStagingDirectory()
        try {
            runPreflight(source, staging)
        } finally {
            // In a finally, not after the call: leaving the screen mid-check cancels
            // this coroutine at a suspension point inside runPreflight, and the staged
            // bytes - up to the whole expanded archive - would otherwise sit in the
            // cache until the next launch.
            //
            // Nothing consumes them anyway: the photos have already been decoded and
            // re-encoded into memory, and `archive.bin` was only ever a working copy.
            staging.deleteRecursively()
        }
    }

    private suspend fun runPreflight(source: Uri, staging: File): Result {
        return try {
            val archive = File(staging, ARCHIVE_COPY_NAME)
            when (val copy = copyBounded(source, archive)) {
                CopyResult.TooLarge -> return Result.Rejected(Reason.TOO_LARGE)
                CopyResult.Failed -> return Result.Rejected(Reason.UNREADABLE)
                CopyResult.Ok -> Unit
            }
            when (val staged = extractAndValidate(archive, staging)) {
                is ExtractResult.Failed -> Result.Rejected(staged.reason)
                is ExtractResult.Ok -> buildContacts(staged, staging)
            }
        } catch (failure: IOException) {
            Result.Rejected(Reason.IO_FAILURE)
        } catch (failure: SecurityException) {
            Result.Rejected(Reason.UNREADABLE)
        }
    }

    // ------------------------------------------------------------- extraction

    private class StagedEntries(
        val files: Map<String, File>,
    )

    private sealed interface CopyResult {
        data object Ok : CopyResult
        data object TooLarge : CopyResult
        data object Failed : CopyResult
    }

    private fun copyBounded(source: Uri, target: File): CopyResult {
        val input: InputStream = try {
            contentResolver.openInputStream(source) ?: return CopyResult.Failed
        } catch (failure: IOException) {
            return CopyResult.Failed
        }
        return input.use { stream ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    // Counted as it is read, so a provider that under-reports its
                    // size cannot get past this.
                    if (total > ContactLimits.MAX_ARCHIVE_BYTES) return CopyResult.TooLarge
                    output.write(buffer, 0, read)
                }
            }
            CopyResult.Ok
        }
    }

    private sealed interface ExtractResult {
        class Ok(val entries: StagedEntries) : ExtractResult
        data class Failed(val reason: Reason) : ExtractResult
    }

    private fun extractAndValidate(archive: File, staging: File): ExtractResult {
        if (!looksLikeZip(archive)) return ExtractResult.Failed(Reason.NOT_A_ZIP)

        val staged = LinkedHashMap<String, File>()
        var totalUncompressed = 0L
        var entryCount = 0
        var stagedIndex = 0

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > ContactLimits.MAX_ARCHIVE_ENTRIES) {
                        return ExtractResult.Failed(Reason.TOO_MANY_ENTRIES)
                    }
                    if (entry.isDirectory) {
                        // The protocol defines no directory entries; a writer that
                        // emits them is simply a duplicate/extra entry.
                        return ExtractResult.Failed(Reason.UNEXPECTED_ENTRY)
                    }

                    val name = entry.name
                    if (!isSafeEntryName(name)) {
                        return ExtractResult.Failed(Reason.UNSAFE_ENTRY_NAME)
                    }
                    if (!BackupProtocol.isAllowedEntry(name)) {
                        return ExtractResult.Failed(Reason.UNEXPECTED_ENTRY)
                    }
                    if (staged.containsKey(name)) {
                        return ExtractResult.Failed(Reason.DUPLICATE_ENTRY)
                    }

                    val perEntryCap = if (name == BackupProtocol.MANIFEST_ENTRY) {
                        ContactLimits.MAX_MANIFEST_BYTES
                    } else {
                        ContactLimits.MAX_PHOTO_BYTES.toLong()
                    }

                    // Program-generated name: the archive's name never becomes a path.
                    val target = File(staging, "entry-${stagedIndex++}.bin")
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var entryBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalUncompressed += read
                            if (entryBytes > perEntryCap) {
                                return ExtractResult.Failed(Reason.ENTRY_TOO_LARGE)
                            }
                            if (totalUncompressed > ContactLimits.MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                                return ExtractResult.Failed(Reason.TOO_LARGE)
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                    staged[name] = target
                }
            }
        } catch (failure: ZipException) {
            // Covers corrupt CRCs and unsupported compression methods.
            return ExtractResult.Failed(Reason.NOT_A_ZIP)
        } catch (failure: IOException) {
            return ExtractResult.Failed(Reason.IO_FAILURE)
        }

        if (!staged.containsKey(BackupProtocol.MANIFEST_ENTRY)) {
            return ExtractResult.Failed(Reason.MANIFEST_MISSING)
        }
        return ExtractResult.Ok(StagedEntries(staged))
    }

    private fun looksLikeZip(file: File): Boolean {
        if (file.length() < 4) return false
        return FileInputStream(file).use { input ->
            val signature = ByteArray(4)
            input.read(signature) == 4 &&
                signature[0] == 0x50.toByte() &&
                signature[1] == 0x4B.toByte()
        }
    }

    /**
     * Rejects absolute paths, parent traversal, backslashes and drive letters.
     * Nothing here is used to build a path, but such an archive is malformed or
     * hostile and is refused outright.
     */
    private fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.length > 256) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.contains("..")) return false
        if (name.contains('\\')) return false
        if (name.length >= 2 && name[1] == ':') return false
        return true
    }

    // ------------------------------------------------------- manifest checking

    private suspend fun buildContacts(
        staged: ExtractResult.Ok,
        staging: File,
    ): Result {
        val entries = staged.entries
        val manifestFile = entries.files.getValue(BackupProtocol.MANIFEST_ENTRY)
        val manifestText = try {
            manifestFile.readText(Charsets.UTF_8)
        } catch (failure: IOException) {
            return Result.Rejected(Reason.MANIFEST_INVALID)
        }

        val manifest = try {
            BackupJson.decodeManifest(manifestText)
        } catch (failure: Exception) {
            return Result.Rejected(Reason.MANIFEST_INVALID)
        }

        if (manifest.format != BackupProtocol.FORMAT_MARKER) {
            return Result.Rejected(Reason.MANIFEST_INVALID)
        }
        if (manifest.schemaVersion != BackupProtocol.SCHEMA_VERSION) {
            // An unknown version is refused, never guessed at.
            return Result.Rejected(Reason.UNSUPPORTED_VERSION)
        }
        if (manifest.contacts.isEmpty()) {
            return Result.Rejected(Reason.EMPTY_CONTACTS)
        }
        if (manifest.contacts.size > ContactLimits.MAX_CONTACTS) {
            return Result.Rejected(Reason.TOO_MANY_CONTACTS)
        }

        val seenIds = HashSet<String>(manifest.contacts.size)
        val seenPaths = HashSet<String>(manifest.contacts.size)
        val imported = ArrayList<ImportedContact>(manifest.contacts.size)
        var photoCount = 0

        for ((index, entry) in manifest.contacts.withIndex()) {
            val id = entry.id
            if (!isCanonicalUuidV4(id)) return Result.Rejected(Reason.INVALID_CONTACT)
            if (!seenIds.add(id)) return Result.Rejected(Reason.INVALID_CONTACT)

            // The array order is the stored order and must be dense and exact.
            if (entry.sortOrder != index) return Result.Rejected(Reason.INVALID_CONTACT)

            val name = DisplayNameRules.validate(entry.displayName)
            if (name !is DisplayNameRules.Result.Valid) {
                return Result.Rejected(Reason.INVALID_CONTACT)
            }
            val phone = PhoneNumberRules.normalize(entry.phoneNumber)
            if (phone !is PhoneNumberRules.Result.Valid) {
                return Result.Rejected(Reason.INVALID_CONTACT)
            }
            // An archived number must already be normalised; if re-normalising
            // changed it, the archive is not the shape the protocol describes.
            if (phone.normalized != entry.phoneNumber) {
                return Result.Rejected(Reason.INVALID_CONTACT)
            }
            val color = PlaceholderColor.fromStorage(entry.placeholderColor)
                ?: return Result.Rejected(Reason.INVALID_CONTACT)

            val photo = entry.photo
            val normalizedPhoto: NormalizedPhoto?
            if (photo == null) {
                normalizedPhoto = null
            } else {
                val expectedPath = BackupProtocol.photoPath(id)
                if (photo.path != expectedPath) return Result.Rejected(Reason.INVALID_CONTACT)
                if (photo.mimeType != BackupProtocol.PHOTO_MIME_TYPE) {
                    return Result.Rejected(Reason.INVALID_CONTACT)
                }
                if (photo.byteCount < 1 || photo.byteCount > ContactLimits.MAX_PHOTO_BYTES) {
                    return Result.Rejected(Reason.INVALID_CONTACT)
                }
                if (photo.sha256.length != 64 || !photo.sha256.all { it in "0123456789abcdef" }) {
                    return Result.Rejected(Reason.INVALID_CONTACT)
                }
                if (!seenPaths.add(expectedPath)) return Result.Rejected(Reason.INVALID_CONTACT)

                val stagedFile = entries.files[expectedPath]
                    ?: return Result.Rejected(Reason.PHOTO_MISSING)

                when (val verified = verifyPhoto(stagedFile, photo)) {
                    is PhotoCheck.Failed -> return Result.Rejected(verified.reason)
                    is PhotoCheck.Ok -> {
                        normalizedPhoto = verified.photo
                        photoCount++
                    }
                }
            }

            imported.add(
                ImportedContact(
                    id = id,
                    displayName = name.value,
                    phoneNumber = phone.normalized,
                    placeholderColor = color,
                    photo = normalizedPhoto,
                ),
            )
        }

        // Every staged entry must be accounted for: an undeclared photo is an
        // archive this build does not understand.
        if (entries.files.size != seenPaths.size + 1) {
            return Result.Rejected(Reason.UNEXPECTED_ENTRY)
        }

        return Result.Ready(
            contacts = imported,
            exportedAt = manifest.exportedAt,
            appVersion = manifest.appVersion,
            photoCount = photoCount,
        )
    }

    private sealed interface PhotoCheck {
        class Ok(val photo: NormalizedPhoto) : PhotoCheck
        data class Failed(val reason: Reason) : PhotoCheck
    }

    private suspend fun verifyPhoto(file: File, declared: BackupPhoto): PhotoCheck {
        val bytes = try {
            file.readBytes()
        } catch (failure: IOException) {
            return PhotoCheck.Failed(Reason.PHOTO_MISMATCH)
        }
        if (bytes.size != declared.byteCount) {
            return PhotoCheck.Failed(Reason.PHOTO_MISMATCH)
        }
        if (Sha256.hex(bytes) != declared.sha256) {
            return PhotoCheck.Failed(Reason.PHOTO_MISMATCH)
        }
        if (!isJpeg(bytes)) {
            return PhotoCheck.Failed(Reason.PHOTO_INVALID)
        }

        // The protocol says an archived photo is already a square JPEG of at most
        // 512 px per side. This has to be checked on the *encoded* image, because
        // the normaliser centre-crops: checking afterwards would silently accept
        // any shape and quietly change what the other phone stored.
        val source = photoNormalizer.sourceDimensions(bytes)
            ?: return PhotoCheck.Failed(Reason.PHOTO_INVALID)
        if (!source.isSquare || source.width > ContactLimits.MAX_PHOTO_EDGE_PX) {
            return PhotoCheck.Failed(Reason.PHOTO_INVALID)
        }

        // Decode and re-encode to the stored form. A file that claims to be a
        // square JPEG but cannot be decoded is corrupt, not merely lossy.
        return when (val normalized = photoNormalizer.normalizeBytes(bytes)) {
            is PhotoNormalizer.Result.Normalized -> PhotoCheck.Ok(normalized.photo)
            is PhotoNormalizer.Result.Failed -> PhotoCheck.Failed(Reason.PHOTO_INVALID)
        }
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    /** Canonical lowercase UUID with version 4 and RFC 4122 variant bits. */
    private fun isCanonicalUuidV4(value: String): Boolean {
        if (value.length != 36) return false
        for (index in 0 until 36) {
            when (index) {
                8, 13, 18, 23 -> if (value[index] != '-') return false
                else -> if (value[index] !in "0123456789abcdef") return false
            }
        }
        if (value[14] != '4') return false
        return value[19] in "89ab"
    }

    private companion object {
        const val ARCHIVE_COPY_NAME = "archive.bin"
    }
}
