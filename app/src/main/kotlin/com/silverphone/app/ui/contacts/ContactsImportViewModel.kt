package com.silverphone.app.ui.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.ContactsImportPlanner
import com.silverphone.app.domain.DisplayNameRules
import com.silverphone.app.domain.ImportCommitResult
import com.silverphone.app.domain.ImportedContact
import com.silverphone.app.domain.PhoneNumberRules
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.platform.contacts.SystemContact
import com.silverphone.app.platform.contacts.SystemContactsRead
import com.silverphone.app.platform.contacts.SystemContactsSource
import com.silverphone.app.platform.photos.PhotoNormalizer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContactsPermission { UNKNOWN, GRANTED, DENIED }

enum class ContactsStep { PICK, PREVIEW, DONE }

/** One selectable system contact plus the family's decisions about it. */
data class ImportCandidate(
    val contact: SystemContact,
    val selected: Boolean = false,
    val numberIndex: Int? = null,
    /** Blank means "use the system name". */
    val customName: String = "",
) {
    val effectiveName: String get() = customName.trim().ifBlank { contact.displayName.trim() }

    val chosenNumber: String? get() = numberIndex?.let { contact.numbers.getOrNull(it)?.number }

    /** The number is missing or unusable; the name cannot be blamed for this. */
    val numberUnusable: Boolean
        get() = chosenNumber == null ||
            PhoneNumberRules.normalize(chosenNumber.orEmpty()) !is PhoneNumberRules.Result.Valid

    /** The wording is not acceptable yet - in practice, too long. */
    val nameUnusable: Boolean
        get() = DisplayNameRules.validate(effectiveName) !is DisplayNameRules.Result.Valid

    /** True when this candidate cannot be imported until the family fixes it. */
    val needsFix: Boolean get() = selected && (numberUnusable || nameUnusable)
}

data class PlanCounts(
    val toAdd: Int = 0,
    val skippedExisting: Int = 0,
    val needsFix: Int = 0,
    val missingPhoto: Int = 0,
)

sealed interface ContactsImportResult {
    data class Committed(val added: Int, val skipped: Int, val unresolved: Int) :
        ContactsImportResult

    data class Failed(val reason: Failure) : ContactsImportResult

    enum class Failure { CAPACITY, STALE, STORAGE }
}

data class ContactsImportUiState(
    val permission: ContactsPermission = ContactsPermission.UNKNOWN,
    val loading: Boolean = false,
    /** The address book could not be read at all, which is not the same as empty. */
    val loadFailed: Boolean = false,
    val candidates: List<ImportCandidate> = emptyList(),
    val query: String = "",
    val step: ContactsStep = ContactsStep.PICK,
    val numberPickerFor: Long? = null,
    val nameEditorFor: Long? = null,
    val committing: Boolean = false,
    val counts: PlanCounts = PlanCounts(),
    val result: ContactsImportResult? = null,
    val existingCount: Int = 0,
) {
    val visible: List<ImportCandidate>
        get() = if (query.isBlank()) {
            candidates
        } else {
            candidates.filter { it.contact.displayName.contains(query.trim(), ignoreCase = true) }
        }

    val selectedCount: Int get() = candidates.count { it.selected }

    val canSubmit: Boolean
        get() = counts.toAdd > 0 && counts.needsFix == 0 && !committing
}

/**
 * S07 state.
 *
 * Source names are used as the starting suggestion only: the family can change
 * how this phone's elderly user addresses each person before importing. The
 * duplicate test is made against the phone's numbers as they were when the
 * preview was built, so a number that appears twice in the source is still
 * handled per person.
 */
class ContactsImportViewModel(
    private val repository: ContactRepository,
    private val source: SystemContactsSource,
    private val photoNormalizer: PhotoNormalizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsImportUiState())
    val uiState: StateFlow<ContactsImportUiState> = _uiState.asStateFlow()

    private var targetNumbers: Set<String> = emptySet()
    private var expectedRevision: Long = 0L

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                permission = if (granted) {
                    ContactsPermission.GRANTED
                } else {
                    ContactsPermission.DENIED
                },
            )
        }
        if (granted) load()
    }

    /**
     * Applies the platform's current permission state.
     *
     * Called whenever the screen returns to the foreground, so a permission
     * granted or revoked in system settings is reflected. A repeat visit with the
     * list already loaded does not re-read the address book.
     */
    fun onObservedPermission(granted: Boolean) {
        val current = _uiState.value
        if (!granted) {
            if (current.permission != ContactsPermission.DENIED) {
                _uiState.value = current.copy(permission = ContactsPermission.DENIED)
            }
            return
        }
        if (current.permission == ContactsPermission.GRANTED && current.candidates.isNotEmpty()) return
        onPermissionResult(true)
    }

    fun load() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val read = try {
                source.read()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                SystemContactsRead.Failed
            }
            if (read !is SystemContactsRead.Loaded) {
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        // Losing the permission mid-read goes back to the state that
                        // offers to ask for it again.
                        permission = if (read is SystemContactsRead.NotPermitted) {
                            ContactsPermission.DENIED
                        } else {
                            current.permission
                        },
                        loadFailed = read is SystemContactsRead.Failed,
                        candidates = emptyList(),
                    )
                }
                return@launch
            }
            val contacts = read.contacts
            val snapshot = repository.snapshot()
            targetNumbers = snapshot.contacts.map { it.phoneNumber }.toSet()
            expectedRevision = snapshot.revision
            _uiState.update { current ->
                current.copy(
                    loading = false,
                    permission = ContactsPermission.GRANTED,
                    loadFailed = false,
                    candidates = contacts.map { contact ->
                        ImportCandidate(
                            contact = contact,
                            selected = false,
                            numberIndex = contact.defaultNumberIndex,
                        )
                    },
                    existingCount = snapshot.contacts.size,
                )
            }
            recompute()
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onToggleSelected(contactId: Long) {
        _uiState.update { current ->
            current.copy(
                candidates = current.candidates.map { candidate ->
                    if (candidate.contact.id == contactId) {
                        candidate.copy(selected = !candidate.selected)
                    } else {
                        candidate
                    }
                },
            )
        }
        recompute()
    }

    fun onSelectAllVisible() {
        val visibleIds = _uiState.value.visible.map { it.contact.id }.toSet()
        _uiState.update { current ->
            current.copy(
                candidates = current.candidates.map { candidate ->
                    if (candidate.contact.id in visibleIds) {
                        candidate.copy(selected = true)
                    } else {
                        candidate
                    }
                },
            )
        }
        recompute()
    }

    fun onClearSelection() {
        _uiState.update { current ->
            current.copy(candidates = current.candidates.map { it.copy(selected = false) })
        }
        recompute()
    }

    fun onOpenNumberPicker(contactId: Long) {
        _uiState.update { it.copy(numberPickerFor = contactId) }
    }

    fun onDismissNumberPicker() {
        _uiState.update { it.copy(numberPickerFor = null) }
    }

    /** A contact with several numbers cannot be imported until one is picked. */
    fun onChooseNumber(contactId: Long, index: Int) {
        _uiState.update { current ->
            current.copy(
                numberPickerFor = null,
                candidates = current.candidates.map { candidate ->
                    if (candidate.contact.id == contactId) {
                        candidate.copy(numberIndex = index, selected = true)
                    } else {
                        candidate
                    }
                },
            )
        }
        recompute()
    }

    fun onOpenNameEditor(contactId: Long) {
        _uiState.update { it.copy(nameEditorFor = contactId) }
    }

    fun onDismissNameEditor() {
        _uiState.update { it.copy(nameEditorFor = null) }
    }

    fun onNameChanged(contactId: Long, name: String) {
        _uiState.update { current ->
            current.copy(
                candidates = current.candidates.map { candidate ->
                    if (candidate.contact.id == contactId) {
                        candidate.copy(customName = name)
                    } else {
                        candidate
                    }
                },
            )
        }
        recompute()
    }

    fun onGoToPreview() {
        _uiState.update { it.copy(step = ContactsStep.PREVIEW) }
    }

    fun onBackToPick() {
        _uiState.update { it.copy(step = ContactsStep.PICK) }
    }

    fun onCancel() {
        _uiState.value = ContactsImportUiState(permission = ContactsPermission.GRANTED)
        targetNumbers = emptySet()
    }

    /** Normalises the chosen photos, then appends everything in one transaction. */
    fun onCommit() {
        val state = _uiState.value
        if (state.committing) return

        val plannerCandidates = plannerCandidatesFor(state)
        val counts = ContactsImportPlanner.count(plannerCandidates, targetNumbers)
        val toAdd = state.candidates.filterIndexed { index, _ ->
            plannerCandidates[index].isAddition(targetNumbers)
        }
        if (toAdd.isEmpty()) return

        _uiState.update { it.copy(committing = true) }
        viewModelScope.launch {
            val imported = ArrayList<ImportedContact>(toAdd.size)
            for (candidate in toAdd) {
                val normalized = PhoneNumberRules.normalize(candidate.chosenNumber.orEmpty())
                if (normalized !is PhoneNumberRules.Result.Valid) continue
                val name = DisplayNameRules.validate(candidate.effectiveName)
                if (name !is DisplayNameRules.Result.Valid) continue

                imported.add(
                    ImportedContact(
                        id = UUID.randomUUID().toString().lowercase(),
                        displayName = name.value,
                        phoneNumber = normalized.normalized,
                        placeholderColor = PlaceholderColor.DEFAULT,
                        photo = readPhoto(candidate.contact.photoUri),
                    ),
                )
            }

            // "Skipped" means one specific thing: the number is already on this
            // phone. Deriving it from `selected - imported` would fold in anything
            // that failed validation here and report it as a duplicate.
            val skippedExisting = counts.skippedExisting
            val droppedAtCommit = toAdd.size - imported.size

            val result = repository.appendImported(
                additions = imported,
                skipped = skippedExisting,
                expectedRevision = expectedRevision,
            )
            _uiState.update { current ->
                current.copy(
                    committing = false,
                    step = ContactsStep.DONE,
                    result = result.toUiResult(
                        unresolvedCount = counts.needsFix + droppedAtCommit,
                    ),
                )
            }
        }
    }

    private fun plannerCandidatesFor(
        state: ContactsImportUiState,
    ): List<ContactsImportPlanner.Candidate> = state.candidates.map { candidate ->
        ContactsImportPlanner.Candidate(
            selected = candidate.selected,
            name = candidate.effectiveName,
            number = candidate.chosenNumber,
            hasPhoto = candidate.contact.photoUri != null,
        )
    }

    private suspend fun readPhoto(photoUri: Uri?): com.silverphone.app.domain.NormalizedPhoto? {
        if (photoUri == null) return null
        // A system photo that cannot be read is recorded as "no photo" instead of
        // failing the whole import, which is what the spec asks for.
        return when (val result = photoNormalizer.normalizeFromUri(photoUri)) {
            is PhotoNormalizer.Result.Normalized -> result.photo
            is PhotoNormalizer.Result.Failed -> null
        }
    }

    private fun ImportCommitResult.toUiResult(unresolvedCount: Int): ContactsImportResult =
        when (this) {
            is ImportCommitResult.Appended -> ContactsImportResult.Committed(
                added = added,
                skipped = skipped,
                unresolved = unresolvedCount,
            )

            ImportCommitResult.NoChanges -> ContactsImportResult.Committed(
                added = 0,
                skipped = 0,
                unresolved = unresolvedCount,
            )

            is ImportCommitResult.CapacityExceeded ->
                ContactsImportResult.Failed(ContactsImportResult.Failure.CAPACITY)

            ImportCommitResult.StalePreview ->
                ContactsImportResult.Failed(ContactsImportResult.Failure.STALE)

            is ImportCommitResult.StorageFailed ->
                ContactsImportResult.Failed(ContactsImportResult.Failure.STORAGE)

            is ImportCommitResult.Replaced -> ContactsImportResult.Committed(
                added = total,
                skipped = 0,
                unresolved = unresolvedCount,
            )
        }

    private fun recompute() {
        val state = _uiState.value
        val counts = ContactsImportPlanner.count(
            plannerCandidatesFor(state),
            targetNumbers,
        )
        _uiState.update {
            it.copy(
                counts = PlanCounts(
                    toAdd = counts.toAdd,
                    skippedExisting = counts.skippedExisting,
                    needsFix = counts.needsFix,
                    missingPhoto = counts.missingPhoto,
                ),
            )
        }
    }

    companion object {
        val CAPACITY: Int get() = ContactLimits.MAX_CONTACTS
    }
}
