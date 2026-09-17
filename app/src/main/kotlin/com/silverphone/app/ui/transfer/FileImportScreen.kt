package com.silverphone.app.ui.transfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.ImportCommitResult
import com.silverphone.app.domain.ImportedContact
import com.silverphone.app.platform.transfer.BackupReader
import com.silverphone.app.ui.components.BackActionButton
import com.silverphone.app.ui.components.DangerActionButton
import com.silverphone.app.ui.components.PlaceholderAvatar
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

private val THUMB = 64.dp

/**
 * The picker's display name for a chosen document, used only to tell the family
 * which file was read. It never becomes a path.
 */
private fun displayNameOf(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
} catch (failure: Exception) {
    null
}

/**
 * S08: import an archive from another phone.
 *
 * The file is chosen through the system picker, then fully checked, then shown as
 * a preview with exact counts, and only then can be committed. The default is
 * append, which keeps the names and photos this phone already has. Replacing
 * everything is a separate, explicitly confirmed action that states how many
 * contacts would be deleted.
 */
@Composable
fun FileImportScreen(
    state: FileImportUiState,
    onFileChosen: (Uri?) -> Unit,
    onModeChange: (ImportMode) -> Unit,
    onRequestReplace: () -> Unit,
    onDismissReplaceConfirm: () -> Unit,
    onCancelPreview: () -> Unit,
    onCommitAppend: () -> Unit,
    onCommitReplace: () -> Unit,
    onExportFirst: () -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val context = LocalContext.current

    var lastName by remember { mutableStateOf<String?>(null) }

    // Any MIME type is accepted: pickers frequently report the wrong one for a
    // ZIP, and the real decision is made by validating the contents.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // The name is taken from the picker purely so the preview can show which
        // file was read; it is never used to build a path.
        lastName = uri?.let { displayNameOf(context, it) }
        onFileChosen(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimens.pagePadding),
        ) {
            Text(
                text = stringResource(R.string.import_title),
                style = styles.pageTitle,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(top = dimens.pagePadding),
            )

            when (val stage = state.stage) {
                FileImportStage.Idle -> IdleBody(
                    onChoose = {
                        picker.launch(arrayOf("*/*"))
                    },
                )

                FileImportStage.Checking -> CheckingBody()

                is FileImportStage.Rejected -> RejectedBody(
                    reason = stage.reason,
                    onChooseAgain = { picker.launch(arrayOf("*/*")) },
                )

                is FileImportStage.Preview -> PreviewBody(
                    stage = stage,
                    mode = state.mode,
                    lastName = lastName,
                    committing = state.committing,
                    onModeChange = onModeChange,
                    onRequestReplace = onRequestReplace,
                )

                is FileImportStage.Done -> DoneBody(stage.result)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            when (state.stage) {
                is FileImportStage.Preview -> {
                    if (state.committing) {
                        Text(
                            text = stringResource(R.string.import_commit),
                            style = styles.caption,
                            color = AppColors.TextSecondary,
                        )
                    }
                    if (state.mode == ImportMode.APPEND) {
                        PrimaryActionButton(
                            text = stringResource(R.string.import_confirm_append),
                            icon = Icons.Filled.Check,
                            enabled = !state.committing &&
                                state.stage.appendPlan.additions.isNotEmpty(),
                            onClick = onCommitAppend,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        DangerActionButton(
                            text = stringResource(R.string.import_confirm_replace),
                            // A bin, not a refresh arrows glyph: this action deletes
                            // every current contact, and the same arrow glyph is used
                            // elsewhere for "choose a file".
                            icon = Icons.Filled.Delete,
                            enabled = !state.committing,
                            onClick = onRequestReplace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    com.silverphone.app.ui.components.CancelActionButton(
                        text = stringResource(R.string.import_cancel),
                        icon = Icons.Filled.Clear,
                        onClick = onCancelPreview,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is FileImportStage.Done -> PrimaryActionButton(
                    text = stringResource(R.string.action_confirm),
                    icon = Icons.Filled.Check,
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> BackActionButton(
                    text = stringResource(R.string.action_back),
                    icon = Icons.Filled.Info,
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (state.confirmingReplace) {
        val preview = state.stage as? FileImportStage.Preview
        if (preview != null) {
            ReplaceConfirmation(
                existingCount = preview.replacePlan.existingCount,
                incomingCount = preview.replacePlan.incoming.size,
                onExportFirst = {
                    onDismissReplaceConfirm()
                    onExportFirst()
                },
                onCancel = onDismissReplaceConfirm,
                onConfirm = onCommitReplace,
            )
        }
    }
}

@Composable
private fun PreviewBody(
    stage: FileImportStage.Preview,
    mode: ImportMode,
    lastName: String?,
    committing: Boolean,
    onModeChange: (ImportMode) -> Unit,
    onRequestReplace: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val ready = stage.ready
    val plan = stage.appendPlan

    // One list holds the summary and the contacts, so nothing can collapse: a
    // Column with a trailing `fillMaxSize` list gives that list only the leftover
    // height, which is zero once the header fills the screen - and at the larger
    // font presets the header alone does. Everything scrolls together instead.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = dimens.touchGap),
        contentPadding = PaddingValues(bottom = dimens.touchGap),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        if (lastName != null) {
            item {
                Text(
                    text = stringResource(R.string.import_file_summary, lastName),
                    style = styles.caption,
                    color = AppColors.TextSecondary,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.import_exported_at, ready.exportedAt),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )
        }
        item {
            Text(
                text = stringResource(
                    R.string.import_counts,
                    ready.contacts.size,
                    ready.photoCount,
                ),
                style = styles.body,
                color = AppColors.TextPrimary,
            )
        }

        item {
            ModeOption(
                label = stringResource(R.string.import_mode_append),
                selected = mode == ImportMode.APPEND,
                onClick = { onModeChange(ImportMode.APPEND) },
            )
        }
        item {
            ModeOption(
                label = stringResource(R.string.import_mode_replace),
                selected = mode == ImportMode.REPLACE,
                danger = true,
                onClick = { onRequestReplace() },
            )
        }

        if (mode == ImportMode.APPEND) {
            item {
                Text(
                    text = stringResource(
                        R.string.import_append_plan,
                        plan.additions.size,
                        plan.existingCount,
                        plan.skipped,
                    ),
                    style = styles.body,
                    color = AppColors.TextPrimary,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.import_append_help),
                    style = styles.caption,
                    color = AppColors.TextSecondary,
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(
                        R.string.import_replace_warning,
                        stage.replacePlan.existingCount,
                        stage.replacePlan.incoming.size,
                    ),
                    style = styles.body,
                    color = AppColors.DangerRed,
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.import_font_unchanged),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )
        }

        item {
            Text(
                text = stringResource(R.string.import_contacts_heading),
                style = styles.body,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(top = dimens.touchGap),
            )
        }

        items(
            count = ready.contacts.size,
            key = { index -> ready.contacts[index].id },
        ) { index ->
            FileEntryRow(ready.contacts[index])
        }
    }
}

