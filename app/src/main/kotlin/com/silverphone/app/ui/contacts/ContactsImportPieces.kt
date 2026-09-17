package com.silverphone.app.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.silverphone.app.R
import com.silverphone.app.domain.DisplayNameRules
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.platform.contacts.SystemPhoneNumber
import com.silverphone.app.platform.contacts.SystemPhoneType
import com.silverphone.app.ui.components.MessageState
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * The building blocks of S07, kept apart from the screen that arranges them so each
 * piece can be read and changed on its own: the permission states, the selectable
 * list, and the two small dialogs.
 */

private val THUMB = 64.dp

@Composable
internal fun PermissionRequestBody(onRequestPermission: () -> Unit) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        Text(
            text = stringResource(R.string.contacts_intro),
            style = styles.body,
            color = AppColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.contacts_intro_hint),
            style = styles.caption,
            color = AppColors.TextSecondary,
        )
        PrimaryActionButton(
            text = stringResource(R.string.contacts_allow),
            icon = Icons.Filled.Person,
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun PermissionDeniedBody(
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onManualAdd: () -> Unit,
) {
    MessageState(
        icon = Icons.Filled.Person,
        title = stringResource(R.string.contacts_denied),
        hint = stringResource(R.string.contacts_denied_hint),
        actions = {
            PrimaryActionButton(
                text = stringResource(R.string.contacts_retry_permission),
                icon = Icons.Filled.Refresh,
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryActionButton(
                text = stringResource(R.string.contacts_open_settings),
                icon = Icons.Filled.Settings,
                onClick = onOpenAppSettings,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryActionButton(
                text = stringResource(R.string.contacts_manual_add),
                icon = Icons.Filled.Edit,
                onClick = onManualAdd,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
internal fun LoadingBody() {
    val dimens = LocalAppDimens.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = AppColors.Ink,
            modifier = Modifier.size(dimens.primaryGlyph),
        )
    }
}

@Composable
internal fun PickBody(
    state: ContactsImportUiState,
    onQueryChange: (String) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenNumberPicker: (Long) -> Unit,
    onOpenNameEditor: (Long) -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = styles.body,
            label = { Text(stringResource(R.string.contacts_search_hint), style = styles.caption) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.Ink) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.inputHeight)
                .padding(horizontal = dimens.pagePadding, vertical = dimens.touchGap),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(dimens.touchGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.contacts_selected_count, state.selectedCount),
                style = styles.caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onSelectAllVisible,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.contacts_select_all), style = styles.caption)
            }
            TextButton(
                onClick = onClearSelection,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.contacts_clear_selection), style = styles.caption)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                bottom = dimens.touchGap,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val visible = state.visible
            items(count = visible.size, key = { index -> visible[index].contact.id }) { index ->
                val candidate = visible[index]
                CandidateRow(
                    candidate = candidate,
                    onToggle = { onToggleSelected(candidate.contact.id) },
                    onPickNumber = { onOpenNumberPicker(candidate.contact.id) },
                    onEditName = { onOpenNameEditor(candidate.contact.id) },
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ImportCandidate,
    onToggle: () -> Unit,
    onPickNumber: () -> Unit,
    onEditName: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .clickable(onClick = onToggle)
            .padding(dimens.cardInnerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = candidate.selected, onCheckedChange = { onToggle() })

        Box(
            modifier = Modifier
                .size(THUMB)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.placeholderBackground(PlaceholderColor.LIGHT_BLUE)),
        ) {
            val photoUri = candidate.contact.photoUri
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = AppColors.Ink,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clearAndSetSemantics { },
                )
            }
        }

        Spacer(Modifier.width(dimens.cardInnerPadding))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = candidate.effectiveName, style = styles.body, color = AppColors.TextPrimary)

            val chosen = candidate.chosenNumber
            Text(
                text = chosen ?: stringResource(R.string.contacts_number_needed),
                style = styles.caption,
                color = if (chosen == null) AppColors.DangerRed else AppColors.TextSecondary,
            )

            if (candidate.numberUnusable) {
                // Naming the real problem matters: telling someone to shorten a name
                // that is already fine, while the number is what cannot be used, is a
                // dead end - they would edit the wrong field forever.
                Text(
                    text = stringResource(R.string.contacts_number_missing),
                    style = styles.caption,
                    color = AppColors.DangerRed,
                )
            } else if (candidate.nameUnusable) {
                Text(
                    text = stringResource(R.string.contacts_name_too_long),
                    style = styles.caption,
                    color = AppColors.DangerRed,
                )
            }

            // Stacked, not side by side. Two labelled buttons need about 240 dp and
            // the text column beside the checkbox and thumbnail only has 231 dp on a
            // 411 dp phone, so 修改 was squeezed to nothing at the larger presets.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (candidate.contact.numbers.size > 1) {
                    TextButton(
                        onClick = onPickNumber,
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                    ) {
                        Text(
                            text = stringResource(R.string.contacts_number_picker_title),
                            style = styles.caption,
                        )
                    }
                }
                TextButton(
                    onClick = onEditName,
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                ) {
                    Text(stringResource(R.string.manage_edit), style = styles.caption)
                }
            }
        }
    }
}

@Composable
internal fun NumberPickerDialog(
    candidate: ImportCandidate,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_number_picker_title), style = styles.body) },
        text = {
            // Scrollable: a contact with many numbers at the largest preset would
            // otherwise push the later numbers and the 取消 button off the dialog.
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = candidate.contact.displayName, style = styles.caption)
                candidate.contact.numbers.forEachIndexed { index, number ->
                    val typeLabel = phoneTypeLabel(number)
                    TextButton(
                        modifier = Modifier.heightIn(min = 56.dp),
                        onClick = { onChoose(index) },
                    ) {
                        Text(
                            text = if (typeLabel.isBlank()) {
                                number.number
                            } else {
                                "$typeLabel ${number.number}"
                            },
                            style = styles.body,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel), style = styles.body)
            }
        },
    )
}

@Composable
private fun phoneTypeLabel(number: SystemPhoneNumber): String = when (number.type) {
    SystemPhoneType.MOBILE -> stringResource(R.string.phone_type_mobile)
    SystemPhoneType.HOME -> stringResource(R.string.phone_type_home)
    SystemPhoneType.WORK -> stringResource(R.string.phone_type_work)
    SystemPhoneType.FAX -> stringResource(R.string.phone_type_fax)
    // A type the family named in their own phone, so their own wording is the useful one.
    SystemPhoneType.CUSTOM -> number.customLabel.orEmpty()
    SystemPhoneType.OTHER -> ""
}

@Composable
internal fun NameEditorDialog(
    candidate: ImportCandidate,
    onNameChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val styles = LocalAppTextStyles.current
    var draft by remember(candidate.contact.id) { mutableStateOf(candidate.effectiveName) }
    val valid = DisplayNameRules.validate(draft) is DisplayNameRules.Result.Valid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_name_editor_label), style = styles.body) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; onNameChanged(it) },
                    singleLine = true,
                    isError = !valid,
                    textStyle = styles.body,
                    supportingText = {
                        Text(
                            // While the field is in error the hint must explain the
                            // error, not sit there saying "最多 12 个字".
                            text = if (valid) {
                                stringResource(R.string.editor_name_example)
                            } else {
                                stringResource(R.string.contacts_name_too_long)
                            },
                            style = styles.caption,
                            color = if (valid) {
                                AppColors.TextSecondary
                            } else {
                                AppColors.DangerRed
                            },
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_confirm), style = styles.body)
            }
        },
    )
}
