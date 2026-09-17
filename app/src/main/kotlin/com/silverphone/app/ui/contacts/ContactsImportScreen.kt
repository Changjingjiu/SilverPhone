package com.silverphone.app.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.FilledActionButton
import com.silverphone.app.ui.components.MessageState
import com.silverphone.app.ui.components.SectionCard
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * S07: bring relatives in from the phone's own address book.
 *
 * The permission is requested only after the family chooses this entry, and only
 * after the screen explains what it is for. Every person needs exactly one decided
 * number before the import can run, and the elderly user's own wording for each
 * person can be set here rather than after the fact.
 *
 * This is a one-time copy: later changes to the system address book never touch the
 * contacts stored in this app.
 *
 * This file arranges the step; the permission states, the selectable list and the
 * two small dialogs live in `ContactsImportPieces.kt`.
 */
@Composable
fun ContactsImportScreen(
    state: ContactsImportUiState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onManualAdd: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenNumberPicker: (Long) -> Unit,
    onDismissNumberPicker: () -> Unit,
    onChooseNumber: (Long, Int) -> Unit,
    onOpenNameEditor: (Long) -> Unit,
    onDismissNameEditor: () -> Unit,
    onNameChanged: (Long, String) -> Unit,
    onGoToPreview: () -> Unit,
    onBackToPick: () -> Unit,
    onCommit: () -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    FamilyScreen(
        title = stringResource(R.string.contacts_title),
        subtitle = stringResource(R.string.settings_import_contacts_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier.fillMaxSize(),
    ) {

        // The content takes the space that is left AFTER the action bar, rather than
        // claiming everything and collapsing the bar to zero height. Without the
        // weight, the pick list's own weighted LazyColumn consumed the whole
        // remaining height and 下一步：预览 became invisible and unreachable.
        Box(modifier = Modifier.weight(1f)) {
            when (state.permission) {
                ContactsPermission.UNKNOWN -> PermissionRequestBody(onRequestPermission)
                ContactsPermission.DENIED -> PermissionDeniedBody(
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onManualAdd = onManualAdd,
                )

                ContactsPermission.GRANTED -> when {
                    state.loading -> LoadingBody()
                    state.step == ContactsStep.DONE -> DoneBody(
                        state = state,
                        onFinished = onFinished,
                    )

                    state.step == ContactsStep.PREVIEW -> PreviewBody(
                        state = state,
                        onGoBack = onBackToPick,
                    )

                    state.candidates.isEmpty() -> MessageState(
                        icon = Icons.Filled.Person,
                        title = stringResource(
                            if (state.loadFailed) {
                                R.string.contacts_load_failed
                            } else {
                                R.string.contacts_empty
                            },
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> PickBody(
                        state = state,
                        onQueryChange = onQueryChange,
                        onToggleSelected = onToggleSelected,
                        onSelectAllVisible = onSelectAllVisible,
                        onClearSelection = onClearSelection,
                        onOpenNumberPicker = onOpenNumberPicker,
                        onOpenNameEditor = onOpenNameEditor,
                    )
                }
            }
        }

        // Every state except the final result gets an explicit way back. The
        // permission and loading states used to offer only the system back gesture,
        // which is exactly what this app must not rely on.
        if (state.step != ContactsStep.DONE &&
            state.permission == ContactsPermission.GRANTED
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.pagePadding, vertical = dimens.touchGap),
            ) {
                if (state.committing) {
                    Text(
                        text = stringResource(R.string.contacts_importing),
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(bottom = dimens.spaceSnug),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.touchGap),
                ) {
                    if (state.step == ContactsStep.PICK) {
                        FilledActionButton(
                            text = stringResource(R.string.contacts_next),
                            icon = Icons.Filled.Check,
                            enabled = state.selectedCount > 0,
                            onClick = onGoToPreview,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        FilledActionButton(
                            text = stringResource(R.string.contacts_import),
                            icon = Icons.Filled.Check,
                            enabled = state.canSubmit,
                            onClick = onCommit,
                            modifier = Modifier.weight(1f),
                        )
                        FilledActionButton(
                            text = stringResource(R.string.contacts_back_to_pick),
                            emphasis = false,
                            onClick = onBackToPick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    val pickerFor = state.numberPickerFor
    if (pickerFor != null) {
        val candidate = state.candidates.firstOrNull { it.contact.id == pickerFor }
        if (candidate != null) {
            NumberPickerDialog(
                candidate = candidate,
                onChoose = { index -> onChooseNumber(pickerFor, index) },
                onDismiss = onDismissNumberPicker,
            )
        }
    }

    val editorFor = state.nameEditorFor
    if (editorFor != null) {
        val candidate = state.candidates.firstOrNull { it.contact.id == editorFor }
        if (candidate != null) {
            NameEditorDialog(
                candidate = candidate,
                onNameChanged = { value -> onNameChanged(editorFor, value) },
                onDismiss = onDismissNameEditor,
            )
        }
    }
}

/** What the archive is about to change, before anything is written. */
@Composable
private fun PreviewBody(
    state: ContactsImportUiState,
    onGoBack: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val counts = state.counts

    // One list for the summary and the people. A Column with a trailing weighted
    // list gives that list only the leftover height, which is zero once the header
    // fills the screen - and at 超大 the header alone does, hiding the very rows the
    // family is being asked to confirm.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.pagePadding),
        contentPadding = PaddingValues(vertical = dimens.touchGap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSnug)) {
                    Text(
                        text = stringResource(R.string.contacts_preview_title),
                        style = styles.section,
                        color = AppColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.contacts_preview_new, counts.toAdd),
                        style = styles.button,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(
                            R.string.contacts_preview_skipped,
                            counts.skippedExisting,
                        ),
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                    if (counts.missingPhoto > 0) {
                        Text(
                            text = stringResource(
                                R.string.contacts_preview_missing_photo,
                                counts.missingPhoto,
                            ),
                            style = styles.caption,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }
        }
        if (counts.needsFix > 0) {
            item {
                Text(
                    text = stringResource(R.string.contacts_preview_unresolved, counts.needsFix),
                    style = styles.body,
                    color = AppColors.DangerRed,
                )
            }
            item {
                TextButton(
                    onClick = onGoBack,
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                ) {
                    Text(stringResource(R.string.action_back), style = styles.caption)
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.contacts_preview_order),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )
        }
        if (state.existingCount + counts.toAdd > ContactLimits.MAX_CONTACTS) {
            item {
                Text(
                    text = stringResource(R.string.contacts_over_capacity, ContactLimits.MAX_CONTACTS),
                    style = styles.body,
                    color = AppColors.DangerRed,
                )
            }
        }

        val selected = state.candidates.filter { it.selected }
        items(count = selected.size, key = { index -> selected[index].contact.id }) { index ->
            val candidate = selected[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.cardCorner))
                    .background(AppColors.Surface)
                    .border(
                        1.dp,
                        AppColors.Hairline,
                        RoundedCornerShape(dimens.cardCorner),
                    )
                    .padding(dimens.cardInnerPadding),
            ) {
                Column {
                    Text(
                        text = candidate.effectiveName,
                        style = styles.button,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        text = candidate.chosenNumber ?: "",
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** What actually happened, reported at the level the numbers support. */
@Composable
private fun DoneBody(state: ContactsImportUiState, onFinished: () -> Unit) {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    val message = when (val result = state.result) {
        // Entries dropped at commit time are reported, so the totals still add up to
        // what the preview promised.
        is ContactsImportResult.Committed -> if (result.unresolved > 0) {
            stringResource(
                R.string.contacts_result_partial,
                result.added,
                result.skipped,
                result.unresolved,
            )
        } else {
            stringResource(R.string.contacts_result, result.added, result.skipped)
        }

        is ContactsImportResult.Failed -> when (result.reason) {
            ContactsImportResult.Failure.CAPACITY ->
                stringResource(R.string.import_capacity, ContactLimits.MAX_CONTACTS)

            ContactsImportResult.Failure.STALE ->
                stringResource(R.string.import_stale_preview)

            ContactsImportResult.Failure.STORAGE ->
                stringResource(R.string.storage_insufficient)
        }

        null -> stringResource(R.string.contacts_importing)
    }
    val danger = state.result is ContactsImportResult.Failed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        Text(
            text = message,
            style = styles.pageTitle,
            color = if (danger) AppColors.DangerRed else AppColors.TextPrimary,
        )
        if (danger) {
            Text(
                text = stringResource(R.string.import_invalid),
                style = styles.caption,
                color = AppColors.DangerRed,
            )
        }
        FilledActionButton(
            text = stringResource(R.string.action_confirm),
            icon = Icons.Filled.Check,
            onClick = onFinished,
        )
    }
}
