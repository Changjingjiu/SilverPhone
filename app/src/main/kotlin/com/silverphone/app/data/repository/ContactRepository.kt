package com.silverphone.app.data.repository

import androidx.room.withTransaction
import com.silverphone.app.app.AppLanguage
import com.silverphone.app.data.local.AppDatabase
import com.silverphone.app.data.local.AppSettingsEntity
import com.silverphone.app.data.local.ContactEntity
import com.silverphone.app.domain.AppSettings
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.ContactLookup
import com.silverphone.app.domain.CountryCode
import com.silverphone.app.domain.ContactWriteResult
import com.silverphone.app.domain.DisplayNameRules
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.domain.ImportCommitResult
import com.silverphone.app.domain.ImportedContact
import com.silverphone.app.domain.NormalizedPhoto
import com.silverphone.app.domain.PhoneNumberRules
import com.silverphone.app.domain.PhotoEdit
import com.silverphone.app.domain.PlaceholderColor
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Contacts plus the revision they were read at. */
class ContactsSnapshot(
    val contacts: List<Contact>,
    val revision: Long,
)

/** Which neighbour a contact swaps places with. */
enum class MoveDirection { UP, DOWN }

/**
 * The single write path for contacts, photos, ordering and the revision counter.
 *
 * Two invariants matter more than anything else here:
 *
 *  1. A contact change, its photo and the revision bump are committed in one
 *     transaction, so a failure can never leave half an import behind or advance
 *     the revision for a write that rolled back.
 *  2. The revision is re-checked inside the committing transaction, so a preview
 *     built against older data is refused rather than applied to data it never
 *     described.
 */
class ContactRepository(
    private val database: AppDatabase,
    private val writeMutex: Mutex,
) : ContactLookup {

    private val contacts = database.contactDao()
    private val photos = database.contactPhotoDao()
    private val settings = database.appSettingsDao()

    // ---------------------------------------------------------------- reads

    fun observeContacts(): Flow<List<Contact>> =
        contacts.observeList().map { rows -> rows.map { it.toContact() } }

    fun observeSettings(): Flow<AppSettings> =
        settings.observe().map { entity -> entity?.toAppSettings() ?: AppSettings.INITIAL }

    fun observeContactCount(): Flow<Int> = contacts.observeCount()

    /** Emits whenever the stored photo set or a photo digest changes. */
    fun observePhotoKeys(): Flow<List<String>> = photos.observePhotoKeys()

    suspend fun photoBytes(contactId: String): ByteArray? = photos.bytes(contactId)

    /**
     * Re-reads one contact for the dial path. Implements [ContactLookup] so the
     * dial coordinator never reads a stale copy from the card that was tapped.
     */
    override suspend fun findContact(id: String): Contact? =
        contacts.findListRow(id)?.toContact()

    suspend fun count(): Int = contacts.count()

    /** How many contacts currently have a stored photo, for the export summary. */
    suspend fun photoCount(): Int = photos.count()

    fun observePhotoCount(): Flow<Int> = photos.observeCount()

    /** Contact metadata and the matching revision, read in one transaction. */
    suspend fun snapshot(): ContactsSnapshot = database.withTransaction {
        val rows = contacts.listOnce()
        val revision = settings.get()?.contactsRevision ?: 0L
        ContactsSnapshot(contacts = rows.map { it.toContact() }, revision = revision)
    }

    suspend fun allPhoneNumbers(): Set<String> = contacts.allPhoneNumbers().toHashSet()

    // --------------------------------------------------------------- writes

    suspend fun addContact(
        rawDisplayName: String,
        rawPhoneNumber: String,
        placeholderColor: PlaceholderColor,
        photo: NormalizedPhoto?,
    ): ContactWriteResult {
        val valid = validate(rawDisplayName, rawPhoneNumber)
            ?: return invalidResult(rawDisplayName, rawPhoneNumber)

        return writeTransaction(
            onFailure = { failure -> ContactWriteResult.StorageFailed(failure) },
        ) {
            if (contacts.count() >= ContactLimits.MAX_CONTACTS) {
                return@writeTransaction ContactWriteResult.CapacityExceeded(
                    ContactLimits.MAX_CONTACTS,
                )
            }
            val nextOrder = (contacts.maxSortOrder() ?: -1) + 1
            val id = newContactId()
            contacts.insert(
                ContactEntity(
                    id = id,
                    displayName = valid.displayName,
                    phoneNumber = valid.phoneNumber,
                    sortOrder = nextOrder,
                    placeholderColor = placeholderColor.name,
                ),
            )
            photo?.let { photos.upsert(it.toEntity(id)) }
            bumpRevision()
            ContactWriteResult.Success
        }
    }

    suspend fun updateContact(
        id: String,
        rawDisplayName: String,
        rawPhoneNumber: String,
        placeholderColor: PlaceholderColor,
        photoEdit: PhotoEdit,
    ): ContactWriteResult {
        val valid = validate(rawDisplayName, rawPhoneNumber)
            ?: return invalidResult(rawDisplayName, rawPhoneNumber)

        return writeTransaction(
            onFailure = { failure -> ContactWriteResult.StorageFailed(failure) },
        ) {
            val existing = contacts.findById(id)
                ?: return@writeTransaction ContactWriteResult.NotFound
            contacts.update(
                existing.copy(
                    displayName = valid.displayName,
                    phoneNumber = valid.phoneNumber,
                    placeholderColor = placeholderColor.name,
                ),
            )
            when (photoEdit) {
                is PhotoEdit.Keep -> Unit
                is PhotoEdit.Replace -> photos.upsert(photoEdit.photo.toEntity(id))
                is PhotoEdit.Remove -> photos.delete(id)
            }
            bumpRevision()
            ContactWriteResult.Success
        }
    }

    suspend fun deleteContact(id: String): ContactWriteResult = writeTransaction(
        onFailure = { failure -> ContactWriteResult.StorageFailed(failure) },
    ) {
        if (contacts.findById(id) == null) {
            return@writeTransaction ContactWriteResult.NotFound
        }
        // Delete the photo explicitly rather than relying on ON DELETE CASCADE, so
        // the outcome does not depend on the foreign-key pragma being on.
        photos.delete(id)
        contacts.deleteById(id)
        compactSortOrder()
        bumpRevision()
        ContactWriteResult.Success
    }

    /**
     * Deletes several contacts in one transaction.
     *
     * The management screen's multi-select delete has to be all-or-nothing: a family
     * member who selected four people and confirmed once must not end up with two of
     * them gone because the fourth write failed. Photos are deleted explicitly for the
     * same reason the single delete does it - it must not depend on the foreign-key
     * pragma being on - and the stored order is compacted once at the end.
     */
    suspend fun deleteContacts(ids: List<String>): ContactWriteResult = writeTransaction(
        onFailure = { failure -> ContactWriteResult.StorageFailed(failure) },
    ) {
        if (ids.isEmpty()) return@writeTransaction ContactWriteResult.NotFound
        val existing = ids.filter { id -> contacts.findById(id) != null }
        if (existing.isEmpty()) return@writeTransaction ContactWriteResult.NotFound
        existing.forEach { id -> photos.delete(id) }
        contacts.deleteByIds(existing)
        compactSortOrder()
        bumpRevision()
        ContactWriteResult.Success
    }

    suspend fun moveContact(id: String, direction: MoveDirection): ContactWriteResult =
        writeTransaction(
            onFailure = { failure -> ContactWriteResult.StorageFailed(failure) },
        ) {
            val current = contacts.findById(id)
                ?: return@writeTransaction ContactWriteResult.NotFound
            val neighbourOrder = when (direction) {
                MoveDirection.UP -> current.sortOrder - 1
                MoveDirection.DOWN -> current.sortOrder + 1
            }
            val neighbour = contacts.findBySortOrder(neighbourOrder)
                ?: return@writeTransaction ContactWriteResult.Success // already at the edge

            contacts.updateSortOrder(current.id, neighbour.sortOrder)
            contacts.updateSortOrder(neighbour.id, current.sortOrder)
            bumpRevision()
            ContactWriteResult.Success
        }

    /**
     * The three preference writers. Each returns true when the value was written.
     *
     * They catch their own failures because a preference is not worth the process: an
     * exception here used to escape an unguarded coroutine on a settings screen and
     * take the app down with it. The screens that call them keep the user on the page
     * when this returns false, so a failed save is visible instead of silent.
     */
    suspend fun setFontPreset(preset: FontPreset): Boolean = writePreference {
        settings.setFontPreset(preset.name)
    }

    /** The chosen interface language; empty means "follow the system". */
    suspend fun setLanguageTag(tag: String): Boolean = writePreference {
        settings.setLanguageTag(tag)
    }

    /** The dialling prefix for numbers stored without one. */
    suspend fun setCountryCode(code: String): Boolean = writePreference {
        settings.setCountryCode(code.trim())
    }

    private suspend fun writePreference(write: suspend () -> Unit): Boolean = try {
        writeMutex.withLock { write() }
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        false
    }

    /** Read by the dial path just before a call is placed. */
    suspend fun countryCode(): String =
        settings.get()?.countryCode ?: CountryCode.DEFAULT

    /** Read once at startup, to put the family's language choice in force. */
    suspend fun languageTag(): String =
        settings.get()?.languageTag ?: AppLanguage.SYSTEM

    // ------------------------------------------------------- import commits

    /**
     * Appends the planned additions. The revision is re-checked inside the
     * transaction, so a preview that went stale is refused instead of applied.
     */
    suspend fun appendImported(
        additions: List<ImportedContact>,
        skipped: Int,
        expectedRevision: Long,
    ): ImportCommitResult = writeTransaction(
        onFailure = { failure -> ImportCommitResult.StorageFailed(failure) },
    ) {
        if (readRevision() != expectedRevision) {
            return@writeTransaction ImportCommitResult.StalePreview
        }
        val existing = contacts.count()
        if (existing + additions.size > ContactLimits.MAX_CONTACTS) {
            return@writeTransaction ImportCommitResult.CapacityExceeded(
                limit = ContactLimits.MAX_CONTACTS,
                current = existing,
                incoming = additions.size,
            )
        }
        if (additions.isEmpty()) {
            // Nothing to write: no rows touched, and the revision must not move.
            return@writeTransaction ImportCommitResult.NoChanges
        }

        var nextOrder = (contacts.maxSortOrder() ?: -1) + 1
        val rows = ArrayList<ContactEntity>(additions.size)
        for (incoming in additions) {
            rows.add(
                ContactEntity(
                    id = incoming.id,
                    displayName = incoming.displayName,
                    phoneNumber = incoming.phoneNumber,
                    sortOrder = nextOrder++,
                    placeholderColor = incoming.placeholderColor.name,
                ),
            )
        }
        contacts.insertAll(rows)
        for (incoming in additions) {
            incoming.photo?.let { photos.upsert(it.toEntity(incoming.id)) }
        }
        bumpRevision()
        ImportCommitResult.Appended(added = additions.size, skipped = skipped)
    }

    /**
     * Replaces every contact with the archive contents in one transaction. The
     * font preset is a local preference and is deliberately left alone.
     */
    suspend fun replaceImported(
        incoming: List<ImportedContact>,
        expectedRevision: Long,
    ): ImportCommitResult = writeTransaction(
        onFailure = { failure -> ImportCommitResult.StorageFailed(failure) },
    ) {
        if (readRevision() != expectedRevision) {
            return@writeTransaction ImportCommitResult.StalePreview
        }

        // Photos first, then contacts: safe whether or not foreign key
        // enforcement happens to be on.
        photos.deleteAll()
        contacts.deleteAll()

        val rows = incoming.mapIndexed { index, contact ->
            ContactEntity(
                id = contact.id,
                displayName = contact.displayName,
                phoneNumber = contact.phoneNumber,
                sortOrder = index,
                placeholderColor = contact.placeholderColor.name,
            )
        }
        contacts.insertAll(rows)
        for (contact in incoming) {
            contact.photo?.let { photos.upsert(it.toEntity(contact.id)) }
        }
        bumpRevision()
        ImportCommitResult.Replaced(total = incoming.size)
    }

    // -------------------------------------------------------------- helpers

    private suspend fun readRevision(): Long = settings.get()?.contactsRevision ?: 0L

    /**
     * Advances the contacts revision.
     *
     * The settings row is created first if it is missing, so the counter cannot
     * silently stop advancing and leave every later preview looking stale.
     */
    private suspend fun bumpRevision() {
        if (settings.get() == null) {
            settings.upsert(AppSettingsEntity())
        }
        settings.incrementRevision()
    }

    /** Renumbers sortOrder to a contiguous 0..n-1 run, preserving relative order. */
    private suspend fun compactSortOrder() {
        val remaining = contacts.all() // ordered by sortOrder
        remaining.forEachIndexed { index, entity ->
            if (entity.sortOrder != index) {
                contacts.updateSortOrder(entity.id, index)
            }
        }
    }

    private class ValidatedInput(val displayName: String, val phoneNumber: String)

    private fun validate(rawDisplayName: String, rawPhoneNumber: String): ValidatedInput? {
        val name = DisplayNameRules.validate(rawDisplayName)
        val phone = PhoneNumberRules.normalize(rawPhoneNumber)
        if (name !is DisplayNameRules.Result.Valid || phone !is PhoneNumberRules.Result.Valid) {
            return null
        }
        return ValidatedInput(name.value, phone.normalized)
    }

    private fun invalidResult(
        rawDisplayName: String,
        rawPhoneNumber: String,
    ): ContactWriteResult.InvalidInput {
        val name = DisplayNameRules.validate(rawDisplayName)
        val phone = PhoneNumberRules.normalize(rawPhoneNumber)
        return ContactWriteResult.InvalidInput(
            nameReason = (name as? DisplayNameRules.Result.Invalid)?.reason,
            phoneReason = (phone as? PhoneNumberRules.Result.Invalid)?.reason,
        )
    }

    /**
     * Runs [block] under the shared write lock and inside one database
     * transaction, mapping thrown failures through [onFailure] without ever
     * swallowing coroutine cancellation.
     */
    private suspend fun <T> writeTransaction(
        onFailure: (Throwable) -> T,
        block: suspend () -> T,
    ): T = writeMutex.withLock {
        try {
            database.withTransaction { block() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            onFailure(failure)
        }
    }

    private fun newContactId(): String = UUID.randomUUID().toString().lowercase()
}
