package com.silverphone.app.ui.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.domain.AppendPlan
import com.silverphone.app.domain.ImportCommitResult
import com.silverphone.app.domain.ImportPlanner
import com.silverphone.app.domain.ReplacePlan
import com.silverphone.app.platform.transfer.BackupReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the two documented import behaviours the family chose. */
enum class ImportMode { APPEND, REPLACE }

/** Where the file import is. Only [Preview] and [Done] allow any writing. */
sealed interface FileImportStage {
    data object Idle : FileImportStage

    /** Reading and validating. No import action is offered while this runs. */
    data object Checking : FileImportStage

    class Preview(
        val ready: BackupReader.Result.Ready,
        val appendPlan: AppendPlan,
        val replacePlan: ReplacePlan,
    ) : FileImportStage

    /** The archive was refused. Nothing on this phone changed. */
    data class Rejected(val reason: BackupReader.Reason) : FileImportStage

    data class Done(val result: ImportCommitResult) : FileImportStage
}

data class FileImportUiState(
    val stage: FileImportStage = FileImportStage.Idle,
    val mode: ImportMode = ImportMode.APPEND,
    val committing: Boolean = false,
    val confirmingReplace: Boolean = false,
)

/**
 * S08 state.
 *
 * Validation completes before any writing is possible, the confirmed counts come
 * from the plan rather than from the file summary, and the commit re-checks the
 * contacts revision so a preview that went stale cannot be applied.
 */
class FileImportViewModel(
    private val repository: ContactRepository,
    private val reader: BackupReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileImportUiState())
    val uiState: StateFlow<FileImportUiState> = _uiState.asStateFlow()

    fun onFileChosen(uri: Uri?) {
        if (uri == null) {
            // Picking nothing is a normal outcome, not an error.
            _uiState.update { it.copy(stage = FileImportStage.Idle) }
            return
        }
        _uiState.update { it.copy(stage = FileImportStage.Checking, confirmingReplace = false) }
        viewModelScope.launch {
            when (val result = reader.preflight(uri)) {
                is BackupReader.Result.Rejected -> _uiState.update {
                    it.copy(stage = FileImportStage.Rejected(result.reason))
                }

                is BackupReader.Result.Ready -> {
                    // The target snapshot is read once; the plan is always measured
                    // against it, never against a set that grows as entries match.
                    val target = repository.snapshot()
                    val append = ImportPlanner
                        .planAppend(result.contacts, target.contacts)
                        .copy(expectedRevision = target.revision)
                    val replace = ImportPlanner
                        .planReplace(result.contacts, target.contacts)
                        .copy(expectedRevision = target.revision)
                    _uiState.update {
                        it.copy(
                            stage = FileImportStage.Preview(result, append, replace),
                            mode = ImportMode.APPEND,
                        )
                    }
                }
            }
        }
    }

    fun onModeChange(mode: ImportMode) {
        _uiState.update { it.copy(mode = mode, confirmingReplace = false) }
    }

    fun onRequestReplace() {
        _uiState.update { it.copy(mode = ImportMode.REPLACE, confirmingReplace = true) }
    }

    fun onDismissReplaceConfirm() {
        _uiState.update { it.copy(confirmingReplace = false, mode = ImportMode.APPEND) }
    }

    fun onCancelPreview() {
        _uiState.update { it.copy(stage = FileImportStage.Idle, confirmingReplace = false) }
    }

    /** Commits the append plan. Writes nothing when there is nothing to add. */
    fun commitAppend() {
        val preview = _uiState.value.stage as? FileImportStage.Preview ?: return
        if (_uiState.value.committing) return
        val plan = preview.appendPlan
        _uiState.update { it.copy(committing = true) }
        viewModelScope.launch {
            val result = repository.appendImported(
                additions = plan.additions,
                skipped = plan.skipped,
                expectedRevision = plan.expectedRevision,
            )
            _uiState.update {
                it.copy(committing = false, stage = FileImportStage.Done(result))
            }
        }
    }

    /** Commits the replace plan, which deletes every current contact first. */
    fun commitReplace() {
        val preview = _uiState.value.stage as? FileImportStage.Preview ?: return
        if (_uiState.value.committing) return
        val plan = preview.replacePlan
        _uiState.update { it.copy(committing = true, confirmingReplace = false) }
        viewModelScope.launch {
            val result = repository.replaceImported(
                incoming = plan.incoming,
                expectedRevision = plan.expectedRevision,
            )
            _uiState.update {
                it.copy(committing = false, stage = FileImportStage.Done(result))
            }
        }
    }

    fun onFinished() {
        _uiState.update { FileImportUiState() }
    }
}
