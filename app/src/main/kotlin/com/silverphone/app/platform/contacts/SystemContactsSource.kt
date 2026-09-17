package com.silverphone.app.platform.contacts

import android.content.ContentResolver
import kotlin.coroutines.cancellation.CancellationException
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The kind of number, as the address book records it. */
enum class SystemPhoneType {
    MOBILE,
    HOME,
    WORK,
    FAX,
    /** A type the user named themselves; [SystemPhoneNumber.customLabel] holds it. */
    CUSTOM,
    OTHER,
}

/** One phone number belonging to a system contact. */
data class SystemPhoneNumber(
    val number: String,
    val type: SystemPhoneType,
    /** Only set for [SystemPhoneType.CUSTOM] - text the user typed in their phone. */
    val customLabel: String? = null,
    val isPrimary: Boolean,
)

/**
 * One system contact, grouped from its phone rows.
 *
 * [id] and [lookupKey] are only meaningful for this one selection session; they
 * are never written into a contact or an export file, because they are not
 * portable between phones.
 */
data class SystemContact(
    val id: Long,
    val lookupKey: String?,
    val displayName: String,
    val numbers: List<SystemPhoneNumber>,
    /** Transient, read-only view of the system avatar; never stored as a dependency. */
    val photoUri: Uri?,
) {
    /**
     * The number to preselect, or null when the family must choose.
     *
     * A single number is unambiguous. With several, only a system-marked primary
     * is trusted as a default; otherwise guessing could dial the wrong line.
     */
    val defaultNumberIndex: Int?
        get() = when {
            numbers.size == 1 -> 0
            numbers.count { it.isPrimary } == 1 -> numbers.indexOfFirst { it.isPrimary }
            else -> null
        }
}

/** What a read of the system address book produced. */
sealed interface SystemContactsRead {
    data class Loaded(val contacts: List<SystemContact>) : SystemContactsRead

    /** The permission was lost between the check and the query. */
    data object NotPermitted : SystemContactsRead

    /** The provider refused or failed. Distinct from an empty address book. */
    data object Failed : SystemContactsRead
}

/**
 * Reads the phone's own contacts, read-only.
 *
 * Only a small projection is requested, the cursor is always closed, and nothing
 * is written back to the system address book. This runs only when a family member
 * asks for it.
 */
class SystemContactsSource(private val contentResolver: ContentResolver) {

    suspend fun read(): SystemContactsRead = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )

        val grouped = LinkedHashMap<Long, MutableEntry>()

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                )
                val lookupIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                )
                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                )
                val numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )
                val typeIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                )
                val labelIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                )
                val primaryIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                )
                val photoIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    // A row whose number is blank tells us nothing useful.
                    val rawNumber = numberIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }
                    if (rawNumber.isNullOrBlank()) continue

                    val entry = grouped.getOrPut(id) {
                        MutableEntry(
                            id = id,
                            lookupKey = lookupIndex.takeIf { it >= 0 }
                                ?.let { cursor.getString(it) },
                            displayName = nameIndex.takeIf { it >= 0 }
                                ?.let { cursor.getString(it) }
                                .orEmpty()
                                .ifBlank { rawNumber },
                            photoUri = photoIndex.takeIf { it >= 0 }
                                ?.let { cursor.getString(it) }
                                ?.let { runCatching { Uri.parse(it) }.getOrNull() },
                        )
                    }
                    entry.numbers.add(
                        SystemPhoneNumber(
                            number = rawNumber,
                            type = phoneTypeOf(
                                type = typeIndex.takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0,
                            ),
                            customLabel = labelIndex.takeIf { it >= 0 }
                                ?.let { cursor.getString(it) }
                                ?.takeIf { it.isNotBlank() },
                            isPrimary = primaryIndex.takeIf { it >= 0 }?.let {
                                cursor.getInt(it) == 1
                            } ?: false,
                        ),
                    )
                }
            }
        } catch (security: SecurityException) {
            // The permission was revoked mid-read. Reported as such rather than as an
            // empty address book: "no contacts found" is a screen the family cannot act
            // on, and it is also untrue.
            return@withContext SystemContactsRead.NotPermitted
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // A dead or refusing provider must not take the app down with it.
            return@withContext SystemContactsRead.Failed
        }

        SystemContactsRead.Loaded(
            grouped.values.map { entry ->
                SystemContact(
                    id = entry.id,
                    lookupKey = entry.lookupKey,
                    displayName = entry.displayName,
                    numbers = entry.numbers.toList(),
                    photoUri = entry.photoUri,
                )
            },
        )
    }

    /**
     * Maps the address book's type code onto our own enum.
     *
     * Deliberately returns no text: this layer must not decide how a type is worded,
     * because that wording has to come from a string resource.
     */
    private fun phoneTypeOf(type: Int): SystemPhoneType = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> SystemPhoneType.MOBILE
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> SystemPhoneType.HOME
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> SystemPhoneType.WORK
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
        -> SystemPhoneType.FAX

        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> SystemPhoneType.CUSTOM
        else -> SystemPhoneType.OTHER
    }

    private class MutableEntry(
        val id: Long,
        val lookupKey: String?,
        val displayName: String,
        val photoUri: Uri?,
        val numbers: MutableList<SystemPhoneNumber> = mutableListOf(),
    )
}
