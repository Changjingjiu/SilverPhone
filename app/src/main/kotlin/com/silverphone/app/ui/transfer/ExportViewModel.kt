package com.silverphone.app.ui.transfer

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.platform.transfer.BackupWriter
import com.silverphone.app.platform.transfer.ExportGateway
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A generated archive that is ready to be saved or shared. */
class GeneratedExport(
    val file: File,
    val contactCount: Int,
    val photoCount: Int,
)

/** Results the screen has to report precisely, without overstating them. */
enum class ExportStatus {
    SAVED,
    SAVE_FAILED,
    SAVE_LEFT_OVER_PARTIAL_FILE,
    SHARE_OPENED,
    SAVE_CANCELLED,
    GENERATE_FAILED,
    NO_CONTACTS,
}

data class ExportUiState(
    val contactCount: Int = 0,
    val photoCount: Int = 0,
    val generating: Boolean = false,
    val generated: GeneratedExport? = null,
    val saving: Boolean = false,
    val exportStatus: ExportStatus? = null,
) {
    val canGenerate: Boolean get() = contactCount > 0 && !generating
}

class ExportViewModel(
    repository: ContactRepository,
    private val writer: BackupWriter,
    private val gateway: ExportGateway,
) : ViewModel() {

    private class Counts(val contacts: Int, val photos: Int)

    private val local = MutableStateFlow(ExportUiState())

    private val counts = combine(
        repository.observeContactCount(),
        repository.observePhotoCount(),
    ) { contacts, photos -> Counts(contacts, photos) }

    val uiState: StateFlow<ExportUiState> = combine(counts, local) { current, state ->
        state.copy(contactCount = current.contacts, photoCount = current.photos)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExportUiState(),
    )

    fun generate(filePrefix: String) {
        if (local.value.generating) return
        // A previously generated archive is dead the moment a new one is asked for;
        // leaving it would keep a second copy in the cache until its TTL expires.
        local.value.generated?.file?.parentFile?.deleteRecursively()
        local.value = local.value.copy(generating = true, generated = null, exportStatus = null)
        viewModelScope.launch {
            when (val result = writer.write(filePrefix)) {
                is BackupWriter.Result.Written -> local.value = local.value.copy(
                    generating = false,
                    generated = GeneratedExport(
                        file = result.file,
                        contactCount = result.contactCount,
                        photoCount = result.photoCount,
                    ),
                )

                BackupWriter.Result.NoContacts -> local.value = local.value.copy(
                    generating = false,
                    exportStatus = ExportStatus.NO_CONTACTS,
                )

                is BackupWriter.Result.Failed -> local.value = local.value.copy(
                    generating = false,
                    exportStatus = ExportStatus.GENERATE_FAILED,
                )
            }
        }
    }

    /** Called with the URI the family picked, or with null if they cancelled. */
    fun onSaveTargetChosen(target: Uri?) {
        val generated = local.value.generated ?: return
        if (target == null) {
            // Cancelling the system picker changes nothing and is not a failure.
            local.value = local.value.copy(exportStatus = ExportStatus.SAVE_CANCELLED)
            return
        }
        if (local.value.saving) return
        local.value = local.value.copy(saving = true)
        viewModelScope.launch {
            val outcome = gateway.copyTo(generated.file, target)
            local.value = local.value.copy(
                saving = false,
                exportStatus = when (outcome) {
                    is ExportGateway.CopyOutcome.Saved -> ExportStatus.SAVED
                    is ExportGateway.CopyOutcome.Failed -> ExportStatus.SAVE_FAILED
                    is ExportGateway.CopyOutcome.FailedWithLeftover ->
                        ExportStatus.SAVE_LEFT_OVER_PARTIAL_FILE
                },
            )
        }
    }

    /**
     * The share intent, or null when there is nothing generated yet.
     *
     * Opening the share sheet is reported as exactly that: the app cannot know
     * whether the receiving app was used or whether anything arrived.
     */
    fun shareIntent(): Intent? {
        val generated = local.value.generated ?: return null
        local.value = local.value.copy(exportStatus = ExportStatus.SHARE_OPENED)
        return gateway.buildShareIntent(generated.file)
    }

    fun chooserFor(intent: Intent): Intent = gateway.chooserFor(intent)
}
