package com.silverphone.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.ContactWriteResult
import com.silverphone.app.domain.DisplayNameRules
import com.silverphone.app.domain.NormalizedPhoto
import com.silverphone.app.domain.PhoneNumberRules
import com.silverphone.app.domain.PhotoEdit
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.platform.photos.PhotoNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a save did not happen. Each maps to a specific, readable message. */
enum class SaveFailure {
    /** A field is missing or malformed; the offending fields are also flagged. */
    VALIDATION,
    CAPACITY,

    /** The chosen image could not be used, for the reason shown. */
    PHOTO_UNSUPPORTED,
    PHOTO_TOO_LARGE,
    PHOTO_ENCODE_FAILED,

    STORAGE,
}

data class EditorUiState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val placeholderColor: PlaceholderColor = PlaceholderColor.DEFAULT,
    /** The stored contact, used to preview an already saved photo. */
    val storedContact: Contact? = null,
    /** A newly picked and normalised photo, not yet saved. */
    val draftPhoto: NormalizedPhoto? = null,
    val photoRemoved: Boolean = false,
    val nameError: DisplayNameRules.Reason? = null,
    val phoneError: PhoneNumberRules.Reason? = null,
    val sameNumberNotice: Boolean = false,
    val sameNameNotice: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    /** True while a picked image is being decoded and re-encoded. */
    val photoProcessing: Boolean = false,
    /** Read once so the editor can show what will actually be dialled. */
    val countryCode: String = "",
    val failure: SaveFailure? = null,
) {
    /**
     * True only when something would actually be lost by leaving.
     *
     * Comparing against the stored values matters: opening an existing contact and
     * closing it again must not ask "discard your changes?" when there are none.
     */
    val hasUnsavedChanges: Boolean
        get() {
            if (saved || deleted) return false
            if (draftPhoto != null || photoRemoved) return true
            val stored = storedContact
                ?: return name.isNotBlank() || phone.isNotBlank()
            return name.trim() != stored.displayName ||
                phone.trim() != stored.phoneNumber ||
                placeholderColor != stored.placeholderColor
        }

    /**
     * Only blocked while a save is in flight. Keeping the button enabled with empty
     * fields is deliberate: tapping it is how a family member learns which field is
     * missing, whereas a permanently greyed button explains nothing.
     */
    val canSave: Boolean
        get() = !saving && !photoProcessing
}

/**
 * S05 state.
 *
 * Editing keeps the contact id and its position; the draft is only written on a
 * successful save, so cancelling always leaves the database exactly as it was.
 * Validation runs on every keystroke for feedback, but is re-run by the
 * repository at save time, which is what actually protects the data.
 */
class EditorViewModel(
    private val repository: ContactRepository,
    private val photoNormalizer: PhotoNormalizer,
    private val contactId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditorUiState(isNew = contactId == null),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (contactId == null) {
            _uiState.update { it.copy(loaded = true, countryCode = repository.countryCode()) }
            return
        }
        val contact = repository.findContact(contactId)
        _uiState.update {
            it.copy(
                loaded = true,
                countryCode = repository.countryCode(),
                storedContact = contact,
                name = contact?.displayName ?: "",
                phone = contact?.phoneNumber ?: "",
                placeholderColor = contact?.placeholderColor ?: PlaceholderColor.DEFAULT,
            )
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.updateName(value) }
        refreshNotices()
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.updatePhone(value) }
        refreshNotices()
    }

    fun onPlaceholderColorChange(color: PlaceholderColor) {
        _uiState.update { it.copy(placeholderColor = color) }
    }

    /** Accepts the bitmap produced by the crop step. */
    fun onCroppedBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(photoProcessing = true, failure = null) }
            val result = photoNormalizer.normalizeCroppedBitmap(bitmap)
            _uiState.update { current ->
                when (result) {
                    is PhotoNormalizer.Result.Normalized -> current.copy(
                        photoProcessing = false,
                        draftPhoto = result.photo,
                        photoRemoved = false,
                    )

                    is PhotoNormalizer.Result.Failed -> current.copy(
                        photoProcessing = false,
                        failure = result.reason.toSaveFailure(),
                    )
                }
            }
        }
    }

    private fun PhotoNormalizer.Reason.toSaveFailure(): SaveFailure = when (this) {
        PhotoNormalizer.Reason.UNSUPPORTED_FORMAT -> SaveFailure.PHOTO_UNSUPPORTED
        PhotoNormalizer.Reason.TOO_LARGE -> SaveFailure.PHOTO_TOO_LARGE
        PhotoNormalizer.Reason.UNREADABLE -> SaveFailure.PHOTO_UNSUPPORTED
        PhotoNormalizer.Reason.ENCODE_FAILED -> SaveFailure.PHOTO_ENCODE_FAILED
    }

    fun onRemovePhoto() {
        _uiState.update { it.copy(draftPhoto = null, photoRemoved = true) }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) return // a second tap while saving is ignored

        // Both fields are validated for this attempt, including blanks, so the
        // family is told about every missing field at once instead of discovering
        // them one tap at a time.
        val validated = state.updateName(state.name).validatePhoneForSave(state.phone)
        if (validated.nameError != null || validated.phoneError != null) {
            _uiState.value = validated.copy(failure = SaveFailure.VALIDATION)
            return
        }

        _uiState.update { it.copy(saving = true, failure = null) }
        viewModelScope.launch {
            val photoEdit: PhotoEdit = when {
                state.draftPhoto != null -> PhotoEdit.Replace(state.draftPhoto)
                state.photoRemoved -> PhotoEdit.Remove
                else -> PhotoEdit.Keep
            }

            val result = if (state.isNew) {
                repository.addContact(
                    rawDisplayName = validated.name,
                    rawPhoneNumber = validated.phone,
                    placeholderColor = state.placeholderColor,
                    photo = state.draftPhoto,
                )
            } else {
                repository.updateContact(
                    id = contactId ?: return@launch,
                    rawDisplayName = validated.name,
                    rawPhoneNumber = validated.phone,
                    placeholderColor = state.placeholderColor,
                    photoEdit = photoEdit,
                )
            }

            _uiState.update { current ->
                when (result) {
                    is ContactWriteResult.Success -> current.copy(saving = false, saved = true)
                    is ContactWriteResult.InvalidInput -> current.copy(
                        saving = false,
                        failure = SaveFailure.VALIDATION,
                        nameError = result.nameReason,
                        phoneError = result.phoneReason,
                    )

                    is ContactWriteResult.CapacityExceeded -> current.copy(
                        saving = false,
                        failure = SaveFailure.CAPACITY,
                    )

                    is ContactWriteResult.NotFound -> current.copy(
                        saving = false,
                        failure = SaveFailure.STORAGE,
                    )

                    is ContactWriteResult.StorageFailed -> current.copy(
                        saving = false,
                        failure = SaveFailure.STORAGE,
                    )
                }
            }
        }
    }

    fun delete() {
        val id = contactId ?: return
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = repository.deleteContact(id)
            _uiState.update { current ->
                when (result) {
                    is ContactWriteResult.Success -> current.copy(saving = false, deleted = true)
                    else -> current.copy(saving = false, failure = SaveFailure.STORAGE)
                }
            }
        }
    }

    private fun refreshNotices() {
        viewModelScope.launch {
            val state = _uiState.value
            val normalized = (PhoneNumberRules.normalize(state.phone) as? PhoneNumberRules.Result.Valid)
                ?.normalized
            val trimmedName = state.name.trim()

            val others = repository.snapshot().contacts.filter { it.id != contactId }
            _uiState.update {
                it.copy(
                    sameNumberNotice = normalized != null &&
                        others.any { other -> other.phoneNumber == normalized },
                    sameNameNotice = trimmedName.isNotEmpty() &&
                        others.any { other -> other.displayName == trimmedName },
                )
            }
        }
    }

    private fun EditorUiState.updateName(value: String): EditorUiState {
        val result = DisplayNameRules.validate(value)
        return copy(
            name = value,
            nameError = (result as? DisplayNameRules.Result.Invalid)?.reason,
        )
    }

    private fun EditorUiState.updatePhone(value: String): EditorUiState {
        // While typing, a blank field shows the hint rather than an error, so a
        // form that has only just been opened is not covered in red.
        if (value.isBlank()) {
            return copy(phone = value, phoneError = null)
        }
        val result = PhoneNumberRules.normalize(value)
        return copy(
            phone = value,
            phoneError = (result as? PhoneNumberRules.Result.Invalid)?.reason,
        )
    }

    /** A save attempt treats a blank number as the error it is. */
    private fun EditorUiState.validatePhoneForSave(value: String): EditorUiState {
        val result = PhoneNumberRules.normalize(value)
        return copy(
            phone = value,
            phoneError = (result as? PhoneNumberRules.Result.Invalid)?.reason,
        )
    }
}
