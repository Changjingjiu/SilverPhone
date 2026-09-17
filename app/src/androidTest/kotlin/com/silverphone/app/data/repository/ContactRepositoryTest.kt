package com.silverphone.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.silverphone.app.data.local.AppDatabase
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.ContactWriteResult
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.domain.ImportCommitResult
import com.silverphone.app.domain.ImportPlanner
import com.silverphone.app.domain.ImportedContact
import com.silverphone.app.domain.NormalizedPhoto
import com.silverphone.app.domain.PhotoEdit
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.domain.Sha256
import kotlinx.coroutines.flow.first
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
 * The data-layer guarantees that the rest of the app relies on.
 *
 * These run against a real SQLite database on a device, because the behaviour
 * under test is the database's: transactions, ordering, cascade deletes and the
 * revision counter.
 */
@RunWith(AndroidJUnit4::class)
class ContactRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ContactRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ContactRepository(database, Mutex())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun photo(seed: Int, edge: Int = 8): NormalizedPhoto {
        val bytes = ByteArray(64) { index -> (index + seed).toByte() }
        return NormalizedPhoto(
            jpegBytes = bytes,
            sha256 = Sha256.hex(bytes),
            width = edge,
            height = edge,
        )
    }

    @Test
    fun addingAContactStoresItAndAdvancesTheRevision() = runTest {
        val before = repository.snapshot().revision
        val result = repository.addContact("女儿", "13800138000", PlaceholderColor.DEFAULT, null)
        assertTrue(result is ContactWriteResult.Success)

        val snapshot = repository.snapshot()
        assertEquals(1, snapshot.contacts.size)
        assertEquals("女儿", snapshot.contacts[0].displayName)
        assertEquals("13800138000", snapshot.contacts[0].phoneNumber)
        assertEquals(0, snapshot.contacts[0].sortOrder)
        assertEquals(before + 1, snapshot.revision)
    }

    @Test
    fun aStoredPhotoComesBackByteForByte() = runTest {
        val original = photo(seed = 3)
        repository.addContact("女儿", "13800138000", PlaceholderColor.DEFAULT, original)

        val id = repository.snapshot().contacts.single().id
        val stored = repository.photoBytes(id)
        assertNotNull(stored)
        assertEquals(Sha256.hex(original.jpegBytes), Sha256.hex(stored!!))
        assertEquals(original.sha256, repository.findContact(id)?.photoSha256)
    }

    @Test
    fun newContactsAppendToTheEndInOrder() = runTest {
        repository.addContact("一", "10000000001", PlaceholderColor.DEFAULT, null)
        repository.addContact("二", "10000000002", PlaceholderColor.DEFAULT, null)
        repository.addContact("三", "10000000003", PlaceholderColor.DEFAULT, null)

        val contacts = repository.snapshot().contacts
        assertEquals(listOf("一", "二", "三"), contacts.map { it.displayName })
        assertEquals(listOf(0, 1, 2), contacts.map { it.sortOrder })
    }

    @Test
    fun movingSwapsNeighboursAndDoesNothingAtTheEnds() = runTest {
        repository.addContact("一", "10000000001", PlaceholderColor.DEFAULT, null)
        repository.addContact("二", "10000000002", PlaceholderColor.DEFAULT, null)
        val second = repository.snapshot().contacts[1]

        repository.moveContact(second.id, MoveDirection.UP)
        assertEquals(listOf("二", "一"), repository.snapshot().contacts.map { it.displayName })

        // The first item cannot move up any further.
        val first = repository.snapshot().contacts[0]
        repository.moveContact(first.id, MoveDirection.UP)
        assertEquals(listOf("二", "一"), repository.snapshot().contacts.map { it.displayName })

        val last = repository.snapshot().contacts[1]
        repository.moveContact(last.id, MoveDirection.DOWN)
        assertEquals(listOf("二", "一"), repository.snapshot().contacts.map { it.displayName })
    }

    @Test
    fun deletingAContactAlsoRemovesItsPhotoAndCompactsTheOrder() = runTest {
        repository.addContact("一", "10000000001", PlaceholderColor.DEFAULT, photo(1))
        repository.addContact("二", "10000000002", PlaceholderColor.DEFAULT, photo(2))
        repository.addContact("三", "10000000003", PlaceholderColor.DEFAULT, null)

        val middle = repository.snapshot().contacts[1]
        assertTrue(repository.deleteContact(middle.id) is ContactWriteResult.Success)

        val remaining = repository.snapshot().contacts
        assertEquals(listOf("一", "三"), remaining.map { it.displayName })
        assertEquals(listOf(0, 1), remaining.map { it.sortOrder })
        assertNull(repository.photoBytes(middle.id))
        // The surviving contact's photo must be untouched.
        assertNotNull(repository.photoBytes(remaining[0].id))
    }

    @Test
    fun editingWithoutTouchingThePhotoKeepsTheStoredBytes() = runTest {
        repository.addContact("女儿", "13800138000", PlaceholderColor.DEFAULT, photo(5))
        val id = repository.snapshot().contacts.single().id
        val before = repository.photoBytes(id)

        repository.updateContact(
            id = id,
            rawDisplayName = "闺女",
            rawPhoneNumber = "13800138000",
            placeholderColor = PlaceholderColor.LIGHT_ROSE,
            photoEdit = PhotoEdit.Keep,
        )

        val after = repository.photoBytes(id)
        assertNotNull(after)
        assertEquals(Sha256.hex(before!!), Sha256.hex(after!!))
        assertEquals("闺女", repository.snapshot().contacts.single().displayName)
        assertEquals(PlaceholderColor.LIGHT_ROSE, repository.snapshot().contacts.single().placeholderColor)
    }

    @Test
    fun replacingThePhotoChangesTheDigestAndRemovingItDeletesTheRow() = runTest {
        repository.addContact("女儿", "13800138000", PlaceholderColor.DEFAULT, photo(5))
        val id = repository.snapshot().contacts.single().id

        val replacement = photo(seed = 90)
        repository.updateContact(
            id = id,
            rawDisplayName = "女儿",
            rawPhoneNumber = "13800138000",
            placeholderColor = PlaceholderColor.DEFAULT,
            photoEdit = PhotoEdit.Replace(replacement),
        )
        assertEquals(replacement.sha256, repository.findContact(id)?.photoSha256)

        repository.updateContact(
            id = id,
            rawDisplayName = "女儿",
            rawPhoneNumber = "13800138000",
            placeholderColor = PlaceholderColor.DEFAULT,
            photoEdit = PhotoEdit.Remove,
        )
        assertNull(repository.photoBytes(id))
        // The contact itself survives with its placeholder colour.
        assertNotNull(repository.findContact(id))
    }

    @Test
    fun invalidInputLeavesTheDatabaseUntouched() = runTest {
        val before = repository.snapshot().revision
        val result = repository.addContact("", "13800138000", PlaceholderColor.DEFAULT, null)
        assertTrue(result is ContactWriteResult.InvalidInput)
        assertEquals(0, repository.count())
        assertEquals(before, repository.snapshot().revision)
    }

    @Test
    fun capacityIsEnforcedAtTheLimit() = runTest {
        // Filling 500 rows one transaction at a time is slow but is the only way to
        // prove the limit is checked rather than assumed.
        val entries = (0 until ContactLimits.MAX_CONTACTS).map { index ->
            ImportedContact(
                id = uuidFor(index),
                displayName = "亲人",
                phoneNumber = "1${index.toString().padStart(10, '0')}",
                placeholderColor = PlaceholderColor.DEFAULT,
                photo = null,
            )
        }
        repository.appendImported(entries, skipped = 0, expectedRevision = 0L)
        assertEquals(ContactLimits.MAX_CONTACTS, repository.count())

        val extra = ImportedContact(
            id = uuidFor(9999),
            displayName = "多余",
            phoneNumber = "19999999999",
            placeholderColor = PlaceholderColor.DEFAULT,
            photo = null,
        )
        val result = repository.appendImported(listOf(extra), skipped = 0, expectedRevision = 1L)
        assertTrue(result is ImportCommitResult.CapacityExceeded)
        assertEquals(ContactLimits.MAX_CONTACTS, repository.count())
    }

    @Test
    fun appendingTheSameArchiveTwiceAddsNothingTheSecondTime() = runTest {
        val entries = listOf(
            ImportedContact(
                id = uuidFor(1),
                displayName = "女儿",
                phoneNumber = "13800138000",
                placeholderColor = PlaceholderColor.DEFAULT,
                photo = photo(7),
            ),
            ImportedContact(
                id = uuidFor(2),
                displayName = "儿子",
                phoneNumber = "13900139000",
                placeholderColor = PlaceholderColor.DEFAULT,
                photo = null,
            ),
        )

        // Driven the way the screen drives it: plan against a target snapshot, then
        // commit the plan's additions.
        val firstTarget = repository.snapshot()
        val firstPlan = ImportPlanner
            .planAppend(entries, firstTarget.contacts)
            .copy(expectedRevision = firstTarget.revision)
        val first = repository.appendImported(
            firstPlan.additions,
            firstPlan.skipped,
            firstPlan.expectedRevision,
        )
        assertTrue(first is ImportCommitResult.Appended)
        assertEquals(2, (first as ImportCommitResult.Appended).added)

        val revisionAfterFirst = repository.snapshot().revision

        val secondTarget = repository.snapshot()
        val secondPlan = ImportPlanner
            .planAppend(entries, secondTarget.contacts)
            .copy(expectedRevision = secondTarget.revision)
        assertEquals(0, secondPlan.additions.size)
        assertEquals(2, secondPlan.skipped)

        val second = repository.appendImported(
            secondPlan.additions,
            secondPlan.skipped,
            secondPlan.expectedRevision,
        )
        // Zero additions are written as nothing at all, and the revision must not
        // move for a re-import that changed nothing.
        assertTrue(second is ImportCommitResult.NoChanges)
        assertEquals(2, repository.count())
        assertEquals(revisionAfterFirst, repository.snapshot().revision)
    }

    @Test
    fun aPlanThatReusesAnExistingIdIsRefusedAtomically() = runTest {
        repository.addContact("原有", "10000000001", PlaceholderColor.DEFAULT, null)
        val existingId = repository.snapshot().contacts.single().id
        val revision = repository.snapshot().revision

        // A caller that hands over an id which already exists is a bug, and the
        // database must not end up holding a duplicate or a half-applied batch.
        val result = repository.appendImported(
            additions = listOf(
                ImportedContact(
                    id = uuidFor(40),
                    displayName = "新增",
                    phoneNumber = "10000000009",
                    placeholderColor = PlaceholderColor.DEFAULT,
                    photo = null,
                ),
                ImportedContact(
                    id = existingId,
                    displayName = "重复",
                    phoneNumber = "10000000008",
                    placeholderColor = PlaceholderColor.DEFAULT,
                    photo = null,
                ),
            ),
            skipped = 0,
            expectedRevision = revision,
        )
        assertTrue(result is ImportCommitResult.StorageFailed)
        assertEquals(1, repository.count())
        assertEquals(revision, repository.snapshot().revision)
    }

    @Test
    fun aStalePreviewIsRefused() = runTest {
        repository.addContact("一", "10000000001", PlaceholderColor.DEFAULT, null)
        val entry = ImportedContact(
            id = uuidFor(4),
            displayName = "二",
            phoneNumber = "10000000002",
            placeholderColor = PlaceholderColor.DEFAULT,
            photo = null,
        )

        // Revision 0 is now wrong: an edit already advanced it.
        val result = repository.appendImported(listOf(entry), skipped = 0, expectedRevision = 0L)
        assertTrue(result is ImportCommitResult.StalePreview)
        assertEquals(1, repository.count())
    }

    @Test
    fun replacingSwapsTheWholeSetAndKeepsTheFontPreset() = runTest {
        repository.addContact("旧的", "10000000001", PlaceholderColor.DEFAULT, photo(11))
        repository.setFontPreset(FontPreset.HUGE)

        val replacement = ImportedContact(
            id = uuidFor(20),
            displayName = "新的",
            phoneNumber = "10000000002",
            placeholderColor = PlaceholderColor.LIGHT_TEAL,
            photo = photo(21),
        )
        val revision = repository.snapshot().revision
        val result = repository.replaceImported(listOf(replacement), expectedRevision = revision)

        assertTrue(result is ImportCommitResult.Replaced)
        val contacts = repository.snapshot().contacts
        assertEquals(listOf("新的"), contacts.map { it.displayName })
        assertEquals(0, contacts.single().sortOrder)
        // The local font preference is not part of the contact set.
        assertEquals(FontPreset.HUGE, repository.observeSettings().first().fontPreset)
    }

    @Test
    fun aFailedCommitRollsBackCompletely() = runTest {
        repository.addContact("原有", "10000000001", PlaceholderColor.DEFAULT, photo(3))
        val originalCount = repository.count()
        val originalRevision = repository.snapshot().revision
        val originalId = repository.snapshot().contacts.single().id
        val originalPhoto = repository.photoBytes(originalId)

        // Two additions where the second reuses an existing primary key: the
        // insert fails, so the whole transaction must be undone.
        val additions = listOf(
            ImportedContact(
                id = uuidFor(30),
                displayName = "新增",
                phoneNumber = "10000000002",
                placeholderColor = PlaceholderColor.DEFAULT,
                photo = photo(31),
            ),
            ImportedContact(
                id = originalId,
                displayName = "冲突",
                phoneNumber = "10000000003",
                placeholderColor = PlaceholderColor.DEFAULT,
                photo = null,
            ),
        )
        val result = repository.appendImported(additions, skipped = 0, expectedRevision = originalRevision)
        assertTrue(result is ImportCommitResult.StorageFailed)

        // Not one row and not one revision step survived.
        assertEquals(originalCount, repository.count())
        assertEquals(originalRevision, repository.snapshot().revision)
        assertEquals(
            Sha256.hex(originalPhoto!!),
            Sha256.hex(repository.photoBytes(originalId)!!),
        )
        assertEquals(listOf("原有"), repository.snapshot().contacts.map { it.displayName })
    }

    @Test
    fun theHomeQueryNeverCarriesPhotoBytes() = runTest {
        repository.addContact("女儿", "13800138000", PlaceholderColor.DEFAULT, photo(9))
        val row = database.contactDao().listOnce().single()
        // The projection exposes a digest, not the image itself.
        assertEquals(photo(9).sha256, row.photoSha256)
        assertEquals(1, database.contactPhotoDao().count())
    }

    private fun uuidFor(index: Int): String =
        "00000000-0000-4000-8000-%012d".format(index)
}
