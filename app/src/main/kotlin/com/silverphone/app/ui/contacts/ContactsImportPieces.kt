package com.silverphone.app.ui.contacts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import com.silverphone.app.ui.components.AppTextField
import com.silverphone.app.ui.components.CompactActionButton
import com.silverphone.app.ui.components.MessageState
import com.silverphone.app.ui.components.PRESSED_SCALE_GENTLE
import com.silverphone.app.ui.components.FilledActionButton
import com.silverphone.app.ui.components.QuietActionButton
import com.silverphone.app.ui.components.SectionCard
import com.silverphone.app.ui.components.pressScale
import com.silverphone.app.ui.components.rememberPressFeedback
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * The building blocks of S07, kept apart from the screen that arranges them so each
 * piece can be read and changed on its own: the permission states, the selectable
 * list, and the two small dialogs.
 */

private val THUMB = 48.dp

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
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSnug)) {
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
            }
        }
        FilledActionButton(
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
            FilledActionButton(
                text = stringResource(R.string.contacts_retry_permission),
                icon = Icons.Filled.Refresh,
                onClick = onRequestPermission,
            )
            QuietActionButton(
                text = stringResource(R.string.contacts_open_settings),
                onClick = onOpenAppSettings,
            )
            QuietActionButton(
                text = stringResource(R.string.contacts_manual_add),
                onClick = onManualAdd,
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
        AppTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.contacts_search_hint),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.Ink) },
            minHeight = dimens.inputHeight,
            modifier = Modifier.padding(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.touchGap,
                bottom = dimens.touchGap,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceRoomy),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.contacts_selected_count, state.selectedCount),
                style = styles.caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            // Two text actions, not two outlined chips. Side by side, their borders sat
            // a hair apart and read as one control with a line through it.
            QuietActionButton(
                text = stringResource(R.string.contacts_select_all),
                onClick = onSelectAllVisible,
            )
            QuietActionButton(
                text = stringResource(R.string.contacts_clear_selection),
                onClick = onClearSelection,
            )
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
    val shape = RoundedCornerShape(dimens.cardCorner)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE_GENTLE)
    val fill by animateColorAsState(
        targetValue = when {
            candidate.selected -> AppColors.InkSoft
            press.pressed -> AppColors.SurfacePressed
            else -> AppColors.Surface
        },
        animationSpec = tween(90),
        label = "candidateFill",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(press.scale)
            .clip(shape)
            .background(fill)
            .border(
                width = if (candidate.selected) 2.dp else 1.dp,
                color = if (candidate.selected) AppColors.Ink else AppColors.Hairline,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = AppColors.Ink),
                onClick = onToggle,
            )
            .padding(dimens.cardInnerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One line per person: the tick, the face, the name and number, and the one
        // action this row has. The first version put a second column of buttons inside
        // the text column, which pushed the rows to 140 dp each and left the name and
        // number fighting with a button for the same width.
        Checkbox(
            checked = candidate.selected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AppColors.Ink,
                uncheckedColor = AppColors.Outline,
                checkmarkColor = AppColors.Surface,
            ),
        )

        Box(
            modifier = Modifier
                .size(THUMB)
                .clip(RoundedCornerShape(dimens.photoCorner))
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
                        .padding(8.dp)
                        .clearAndSetSemantics { },
                )
            }
        }

        Spacer(Modifier.width(dimens.cardInnerPadding))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = candidate.effectiveName, style = styles.button, color = AppColors.TextPrimary)

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

            // Only when the contact really has several numbers, and only until one is
            // chosen: the picker then sits with the problem it solves instead of
            // occupying every row.
            if (candidate.contact.numbers.size > 1) {
                QuietActionButton(
                    text = stringResource(R.string.contacts_number_picker_title),
                    onClick = onPickNumber,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.width(dimens.spaceSnug))

        QuietActionButton(
            text = stringResource(R.string.manage_edit),
            onClick = onEditName,
        )
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
                AppTextField(
                    value = draft,
                    onValueChange = { draft = it; onNameChanged(it) },
                    isError = !valid,
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
