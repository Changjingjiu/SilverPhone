package com.silverphone.app.platform.transfer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.silverphone.app.data.local.AppDatabase
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.ImportPlanner
import com.silverphone.app.domain.NormalizedPhoto
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.domain.Sha256
import com.silverphone.app.platform.photos.PhotoNormalizer
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exchange format, end to end on a device.
 *
 * This runs the real writer against a real database, then feeds what it produced
 * back into the real reader, and finally drives deliberately broken archives
 * through the same reader. The whole point is that the reader's guarantees are
 * exercised against actual ZIP bytes rather than a description of them.
 */
/** The file name is cosmetic; the archive content is what the tests assert. */
private const val TEST_PREFIX = "test_"

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: ContactRepository
    private lateinit var dirs: TransferDirs
    private lateinit var writer: BackupWriter
    private lateinit var reader: BackupReader
    private lateinit var normalizer: PhotoNormalizer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ContactRepository(database, Mutex())
        // Each operation gets its own UUID-named directory, so tests do not need
        // separate cache roots and cannot collide with each other.
        dirs = TransferDirs(context)
        writer = BackupWriter(repository, dirs, appVersion = "test")
        normalizer = PhotoNormalizer(context)
        reader = BackupReader(context.contentResolver, dirs, normalizer)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------ round trip

    @Test
    fun whatTheWriterProducesTheReaderAcceptsUnchanged() = runTest {
        seed("女儿", "00000000001", PlaceholderColor.LIGHT_BLUE, photoSeed = 1)
        seed("儿子", "00000000002", PlaceholderColor.LIGHT_AMBER, photoSeed = 2)
        seed("老伴", "00000000003", PlaceholderColor.LIGHT_PURPLE, photoSeed = null)

        val written = writer.write(TEST_PREFIX)
        assertTrue(written is BackupWriter.Result.Written)
        val archive = (written as BackupWriter.Result.Written).file
        assertEquals(3, written.contactCount)
        assertEquals(2, written.photoCount)
        assertTrue(archive.name.endsWith(".zip"))

        val read = reader.preflight(Uri.fromFile(archive))
        assertTrue("expected the produced archive to be accepted, was $read", read is BackupReader.Result.Ready)
        val ready = read as BackupReader.Result.Ready

        assertEquals(3, ready.contacts.size)
        assertEquals(2, ready.photoCount)
        // The writer stores the version string verbatim; it is informational only.
        assertEquals("test", ready.appVersion)
        assertEquals(listOf("女儿", "儿子", "老伴"), ready.contacts.map { it.displayName })
        assertEquals(
            listOf("00000000001", "00000000002", "00000000003"),
            ready.contacts.map { it.phoneNumber },
        )
        assertEquals(
            listOf(PlaceholderColor.LIGHT_BLUE, PlaceholderColor.LIGHT_AMBER, PlaceholderColor.LIGHT_PURPLE),
            ready.contacts.map { it.placeholderColor },
        )

        // Photos survive as decodable square JPEGs. Re-encoding means the bytes are
        // not required to be identical, but the picture must still be there.
        val withPhotos = ready.contacts.filter { it.photo != null }
        assertEquals(2, withPhotos.size)
        withPhotos.forEach { contact ->
            val photo = contact.photo!!
            assertEquals(photo.width, photo.height)
            assertTrue(photo.byteCount in 1..com.silverphone.app.domain.ContactLimits.MAX_PHOTO_BYTES)
            assertNotNull(BitmapFactory.decodeByteArray(photo.jpegBytes, 0, photo.byteCount))
        }
        assertNull(ready.contacts.single { it.displayName == "老伴" }.photo)
    }

    @Test
    fun importingWhatWasJustExportedOnTheSamePhoneAddsNothing() = runTest {
        seed("女儿", "00000000001", PlaceholderColor.LIGHT_BLUE, photoSeed = 3)
        seed("儿子", "00000000002", PlaceholderColor.LIGHT_AMBER, photoSeed = null)

        val written = writer.write(TEST_PREFIX) as BackupWriter.Result.Written
        val read = reader.preflight(Uri.fromFile(written.file)) as BackupReader.Result.Ready

        // Exactly the path the screen takes: plan against the current contents.
        val target = repository.snapshot()
        val plan = ImportPlanner.planAppend(read.contacts, target.contacts)
            .copy(expectedRevision = target.revision)

        assertEquals(0, plan.additions.size)
        assertEquals(2, plan.skipped)
    }

    // ------------------------------------------------------ broken archives

    @Test
    fun aPhotoWhoseBytesDoNotMatchItsDigestIsRejected() = runTest {
        seed("女儿", "00000000001", PlaceholderColor.LIGHT_BLUE, photoSeed = 4)
        val archive = (writer.write(TEST_PREFIX) as BackupWriter.Result.Written).file
        val broken = rebuildZip(archive) { name, bytes ->
            if (name.startsWith("photos/")) name to corruptBytes(bytes) else name to bytes
        }

        val result = reader.preflight(Uri.fromFile(broken))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(BackupReader.Reason.PHOTO_MISMATCH, (result as BackupReader.Result.Rejected).reason)
    }

    @Test
    fun aDeclaredPhotoThatIsMissingIsRejected() = runTest {
        seed("女儿", "00000000001", PlaceholderColor.LIGHT_BLUE, photoSeed = 5)
        val archive = (writer.write(TEST_PREFIX) as BackupWriter.Result.Written).file
        // Drop the photo but keep the manifest that declares it.
        val broken = rebuildZip(archive) { name, bytes ->
            if (name.startsWith("photos/")) null else name to bytes
        }

        val result = reader.preflight(Uri.fromFile(broken))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(BackupReader.Reason.PHOTO_MISSING, (result as BackupReader.Result.Rejected).reason)
    }

    @Test
    fun anEntryOutsideTheWhitelistIsRejected() = runTest {
        seed("女儿", "00000000001", PlaceholderColor.LIGHT_BLUE, photoSeed = 6)
        val archive = (writer.write(TEST_PREFIX) as BackupWriter.Result.Written).file
        val broken = rebuildZip(archive) { name, bytes -> name to bytes }
            .let { plain -> addEntry(plain, "notes.txt", "hello".toByteArray()) }

        val result = reader.preflight(Uri.fromFile(broken))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(BackupReader.Reason.UNEXPECTED_ENTRY, (result as BackupReader.Result.Rejected).reason)
    }

    @Test
    fun aParentTraversalEntryNameIsRejected() = runTest {
        seed("女儿", "00000000001", PlaceholderColor.LIGHT_BLUE, photoSeed = 7)
        val archive = (writer.write(TEST_PREFIX) as BackupWriter.Result.Written).file
        val broken = rebuildZip(archive) { name, bytes -> name to bytes }
            .let { plain -> addEntry(plain, "../escaped.jpg", "x".toByteArray()) }

        val result = reader.preflight(Uri.fromFile(broken))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(
            BackupReader.Reason.UNSAFE_ENTRY_NAME,
            (result as BackupReader.Result.Rejected).reason,
        )
    }

    @Test
    fun anUnknownSchemaVersionIsRejectedRatherThanGuessedAt() = runTest {
        val plain = writeZip(
            mapOf(
                "manifest.json" to manifestWith(schemaVersion = 2),
            ),
        )
        val result = reader.preflight(Uri.fromFile(plain))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(
            BackupReader.Reason.UNSUPPORTED_VERSION,
            (result as BackupReader.Result.Rejected).reason,
        )
    }

    @Test
    fun aManifestWithAnUnknownFieldIsRejected() = runTest {
        val manifest = manifestWith(schemaVersion = 1, extraField = "\"surprise\":true")
        val plain = writeZip(mapOf("manifest.json" to manifest))
        val result = reader.preflight(Uri.fromFile(plain))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(
            BackupReader.Reason.MANIFEST_INVALID,
            (result as BackupReader.Result.Rejected).reason,
        )
    }

    @Test
    fun aSparseSortOrderIsRejected() = runTest {
        val manifest = """
            {
              "format": "silverphone-backup",
              "schemaVersion": 1,
              "exportedAt": "2026-09-17T08:00:00Z",
              "appVersion": "1.0.0",
              "contacts": [
                {
                  "id": "11111111-1111-4111-8111-111111111111",
                  "displayName": "女儿",
                  "phoneNumber": "00000000001",
                  "sortOrder": 5,
                  "placeholderColor": "LIGHT_BLUE",
                  "photo": null
                }
              ]
            }
        """.trimIndent()
        val plain = writeZip(mapOf("manifest.json" to manifest.toByteArray(StandardCharsets.UTF_8)))
        val result = reader.preflight(Uri.fromFile(plain))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(
            BackupReader.Reason.INVALID_CONTACT,
            (result as BackupReader.Result.Rejected).reason,
        )
    }

    @Test
    fun aFileThatIsNotAZipIsRejected() = runTest {
        val plain = File(dirs.newStagingDirectory(), "not-a-zip.bin")
        plain.writeBytes("this is plainly not an archive".toByteArray())

        val result = reader.preflight(Uri.fromFile(plain))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(BackupReader.Reason.NOT_A_ZIP, (result as BackupReader.Result.Rejected).reason)
    }

    @Test
    fun aNonSquareArchivedPhotoIsRejectedRatherThanCropped() = runTest {
        // The normaliser centre-crops, so checking the *output* dimensions would
        // accept any shape and quietly change what the other phone had stored.
        val jpeg = jpegOf(width = 600, height = 400)
        val archive = writeZip(
            mapOf(
                "manifest.json" to manifestWithPhoto(jpeg),
                "photos/$PHOTO_ID.jpg" to jpeg,
            ),
        )

        val result = reader.preflight(Uri.fromFile(archive))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(
            BackupReader.Reason.PHOTO_INVALID,
            (result as BackupReader.Result.Rejected).reason,
        )
    }

    @Test
    fun anArchivedPhotoLargerThanTheStoredEdgeIsRejected() = runTest {
        val jpeg = jpegOf(width = 800, height = 800)
        val archive = writeZip(
            mapOf(
                "manifest.json" to manifestWithPhoto(jpeg),
                "photos/$PHOTO_ID.jpg" to jpeg,
            ),
        )

        val result = reader.preflight(Uri.fromFile(archive))
        assertTrue(result is BackupReader.Result.Rejected)
        assertEquals(
            BackupReader.Reason.PHOTO_INVALID,
            (result as BackupReader.Result.Rejected).reason,
        )
    }

    @Test
    fun aSquarePhotoWithinTheEdgeIsStillAccepted() = runTest {
        // The counterpart to the two rejections above: the same path must accept a
        // conforming photo, so the check is not simply rejecting everything.
        val jpeg = jpegOf(width = 512, height = 512)
        val archive = writeZip(
            mapOf(
                "manifest.json" to manifestWithPhoto(jpeg),
                "photos/$PHOTO_ID.jpg" to jpeg,
            ),
        )

        val result = reader.preflight(Uri.fromFile(archive))
        assertTrue("expected acceptance, was $result", result is BackupReader.Result.Ready)
        assertEquals(1, (result as BackupReader.Result.Ready).photoCount)
    }

    @Test
    fun aRejectedArchiveLeavesNoStagedBytesBehind() = runTest {
        val plain = File(dirs.newStagingDirectory(), "broken.bin")
        plain.writeBytes("not an archive".toByteArray())

        val before = dirs.stagingRoot.listFiles().orEmpty().map { it.name }.toSet()
        val result = reader.preflight(Uri.fromFile(plain))
        assertTrue(result is BackupReader.Result.Rejected)

        // Nothing will consume the staged bytes, so the directory this call created
        // must be gone: a rejected archive can be up to 70 MiB, and a few of them
        // would fill the cache before the next app start cleans it.
        val after = dirs.stagingRoot.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(before, after)
    }

    // ------------------------------------------------------------- helpers

    /** Builds a square or non-square JPEG of the given size, for shape testing. */
    private fun jpegOf(width: Int, height: Int): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(android.graphics.Color.rgb(90, 120, 150))
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun manifestWithPhoto(jpeg: ByteArray): ByteArray = """
        {
          "format": "silverphone-backup",
          "schemaVersion": 1,
          "exportedAt": "2026-09-17T08:00:00Z",
          "appVersion": "1.0.0",
          "contacts": [
            {
              "id": "$PHOTO_ID",
              "displayName": "女儿",
              "phoneNumber": "00000000001",
              "sortOrder": 0,
              "placeholderColor": "LIGHT_BLUE",
              "photo": {
                "path": "photos/$PHOTO_ID.jpg",
                "mimeType": "image/jpeg",
                "byteCount": ${jpeg.size},
                "sha256": "${Sha256.hex(jpeg)}"
              }
            }
          ]
        }
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private suspend fun seed(
        name: String,
        number: String,
        color: PlaceholderColor,
        photoSeed: Int?,
    ) {
        repository.addContact(name, number, color, photoSeed?.let { syntheticPhoto(it) })
    }

    /** A small but genuinely decodable square JPEG, generated rather than shipped. */
    private fun syntheticPhoto(seed: Int): NormalizedPhoto {
        val edge = 64
        val pixels = IntArray(edge * edge) { index ->
            val x = index % edge
            val y = index / edge
            val r = (x * 3 + seed * 20) % 256
            val g = (y * 3 + seed * 10) % 256
            val b = ((x + y) * 2 + seed * 30) % 256
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            pixels, edge, edge, android.graphics.Bitmap.Config.ARGB_8888,
        )
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        val bytes = out.toByteArray()
        return NormalizedPhoto(bytes, Sha256.hex(bytes), edge, edge)
    }

    private fun manifestWith(schemaVersion: Int, extraField: String = ""): ByteArray {
        val extra = if (extraField.isBlank()) "" else "$extraField,"
        return """
            {
              "format": "silverphone-backup",
              $extra
              "schemaVersion": $schemaVersion,
              "exportedAt": "2026-09-17T08:00:00Z",
              "appVersion": "1.0.0",
              "contacts": []
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Rewrites an archive entry by entry. Returning null for an entry drops it,
     * which is how the missing-photo case is built.
     */
    private fun rebuildZip(
        source: File,
        transform: (String, ByteArray) -> Pair<String, ByteArray>?,
    ): File {
        val entries = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(source.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val transformed = LinkedHashMap<String, ByteArray>()
        for ((name, bytes) in entries) {
            val result = transform(name, bytes) ?: continue
            transformed[result.first] = result.second
        }
        return writeZip(transformed)
    }

    private fun writeZip(entries: Map<String, ByteArray>): File {
        val target = File(dirs.newStagingDirectory(), "test-archive.zip")
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return target
    }

    private fun addEntry(archive: File, name: String, bytes: ByteArray): File {
        val entries = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        entries[name] = bytes
        return writeZip(entries)
    }

    /** Flips bytes in the middle of the image so the digest no longer matches. */
    private fun corruptBytes(bytes: ByteArray): ByteArray {
        val copy = bytes.copyOf()
        for (index in 20 until minOf(copy.size, 40)) {
            copy[index] = (copy[index].toInt() xor 0xFF).toByte()
        }
        return copy
    }

    private companion object {
        /** Canonical v4 UUID, so it passes the identity format check. */
        const val PHOTO_ID = "b1111111-1111-4111-8111-111111111111"
    }
}
