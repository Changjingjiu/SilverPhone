package com.silverphone.app.ui.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.ContactLimits
import com.silverphone.app.domain.CountryCode
import com.silverphone.app.domain.DisplayNameRules
import com.silverphone.app.domain.PhoneNumberRules
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.ui.components.AppTextField
import com.silverphone.app.ui.components.ContactPhoto
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.FilledActionButton
import com.silverphone.app.ui.components.PlaceholderAvatar
import com.silverphone.app.ui.components.QuietActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

private val PHOTO_PREVIEW_SIZE = 112.dp
private const val SWATCHES_PER_ROW = 3

/**
 * S05: add or edit one contact.
 *
 * Field order follows the spec: photo, name, number, placeholder colour, save.
 * Nothing is written until save succeeds, so backing out leaves the stored data
 * untouched. Delete sits at the very bottom, visually separated from save, and
 * always asks first.
 */
@Composable
fun ContactEditorScreen(
    state: EditorUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPlaceholderColorChange: (PlaceholderColor) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onCropped: (android.graphics.Bitmap) -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    pendingCropUri: Uri? = null,
    onCropCancelled: () -> Unit = {},
) {
    if (pendingCropUri != null) {
        PhotoCropScreen(
            sourceUri = pendingCropUri,
            onUsePhoto = onCropped,
            onCancel = onCropCancelled,
            modifier = modifier,
        )
        return
    }

    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // The stored contact is read asynchronously. Rendering the form before it
    // arrives would show empty fields and then overwrite anything typed into them.
    if (!state.loaded) {
        LoadingEditor(modifier = modifier)
        return
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // A cancelled picker returns null and changes nothing.
        if (uri != null) onPhotoPicked(uri)
    }

    val requestBack = {
        if (state.hasUnsavedChanges) confirmDiscard = true else onFinished()
    }

    BackHandler(enabled = state.hasUnsavedChanges) { confirmDiscard = true }

    FamilyScreen(
        title = stringResource(
            if (state.isNew) R.string.editor_add_title else R.string.editor_edit_title,
        ),
        onBack = requestBack,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier.fillMaxSize(),
        // Save belongs in the bar, where every other Android form keeps it. A footer
        // that holds three 64 dp buttons costs a third of the screen and hides the
        // content it is meant to act on.
        actions = {
            QuietActionButton(
                text = stringResource(R.string.editor_save),
                enabled = state.canSave,
                onClick = onSave,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            PhotoSection(
                state = state,
                enabled = !state.saving,
                onPickPhoto = { photoPicker.launch(arrayOf("image/*")) },
                onRemovePhoto = onRemovePhoto,
            )

            AppTextField(
                value = state.name,
                onValueChange = onNameChange,
                // Locked during a save: the write captured the values at tap time, so
                // an edit made while it is in flight would be silently dropped when
                // the screen closes on success.
                enabled = !state.saving,
                isError = state.nameError != null,
                label = stringResource(R.string.editor_name_label),
                placeholder = stringResource(R.string.editor_name_hint),
                supportingText = {
                    Text(
                        text = nameSupport(state.nameError),
                        style = styles.caption,
                        color = if (state.nameError != null) AppColors.DangerRed else AppColors.TextSecondary,
                    )
                },
                minHeight = dimens.inputHeight,
            )

            AppTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                enabled = !state.saving,
                isError = state.phoneError != null,
                label = stringResource(R.string.editor_phone_label),
                placeholder = stringResource(R.string.editor_phone_hint),
                supportingText = {
                    Text(
                        text = phoneSupport(state.phoneError, state.phone, state.countryCode),
                        style = styles.caption,
                        color = if (state.phoneError != null) AppColors.DangerRed else AppColors.TextSecondary,
                    )
                },
                minHeight = dimens.inputHeight,
            )

            if (state.sameNumberNotice) {
                Notice(text = stringResource(R.string.editor_same_number))
            }
            if (state.sameNameNotice) {
                Notice(text = stringResource(R.string.editor_same_name))
            }

            ColorSection(
                selectedColor = state.placeholderColor,
                onSelect = onPlaceholderColorChange,
            )

            when (state.failure) {
                SaveFailure.CAPACITY -> Notice(
                    text = stringResource(R.string.editor_capacity_reached, ContactLimits.MAX_CONTACTS),
                    danger = true,
                )

                SaveFailure.VALIDATION -> Notice(
                    text = stringResource(R.string.editor_fix_fields),
                    danger = true,
                )

                SaveFailure.PHOTO_UNSUPPORTED -> Notice(
                    text = stringResource(R.string.photo_unsupported),
                    danger = true,
                )

                SaveFailure.PHOTO_TOO_LARGE -> Notice(
                    text = stringResource(R.string.photo_too_large),
                    danger = true,
                )

                SaveFailure.PHOTO_ENCODE_FAILED -> Notice(
                    text = stringResource(R.string.photo_encode_failed),
                    danger = true,
                )

                SaveFailure.STORAGE -> Notice(
                    text = stringResource(R.string.storage_insufficient),
                    danger = true,
                )

                null -> Unit
            }

            if (state.saving) {
                Text(
                    text = stringResource(R.string.editor_saving),
                    style = styles.caption,
                    color = AppColors.TextSecondary,
                )
            }

            // Deleting sits at the end of the form, under a line, as a text action: it
            // is the one thing on this screen nobody should press by reflex.
            if (!state.isNew) {
                HorizontalDivider(
                    color = AppColors.Hairline,
                    modifier = Modifier.padding(top = dimens.spaceRoomy, bottom = dimens.spaceSnug),
                )
                QuietActionButton(
                    text = stringResource(R.string.editor_delete),
                    enabled = !state.saving,
                    danger = true,
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title), style = styles.body) },
            text = { Text(stringResource(R.string.editor_discard_message), style = styles.caption) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 56.dp),
                    onClick = {
                        confirmDiscard = false
                        onFinished()
                    },
                ) {
                    Text(stringResource(R.string.editor_discard_confirm), style = styles.body)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.editor_keep_editing), style = styles.body)
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_confirm_title), style = styles.body) },
            text = { Text(stringResource(R.string.editor_delete_confirm_message), style = styles.caption) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 56.dp),
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.editor_delete_confirm_action),
                        style = styles.body,
                        color = AppColors.DangerRed,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.editor_cancel), style = styles.body)
                }
            },
        )
    }
}

@Composable
private fun PhotoSection(
    state: EditorUiState,
    enabled: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val dimens = LocalAppDimens.current

    val draftBitmap = remember(state.draftPhoto) {
        state.draftPhoto?.jpegBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    val hasPhoto = draftBitmap != null ||
        (!state.photoRemoved && state.storedContact?.hasPhoto == true)

    // Photo above, actions under it. Side by side looked wrong at every size: a tall
    // preview next to a short button leaves a band of dead space, and the eye reads the
    // two as unrelated. Stacked, the block reads as one thing - this is the face, these
    // are the things you can do to it.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSnug),
    ) {
        Box(
            modifier = Modifier
                .size(PHOTO_PREVIEW_SIZE)
                .clip(RoundedCornerShape(dimens.photoCorner)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                draftBitmap != null -> androidx.compose.foundation.Image(
                    bitmap = draftBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clearAndSetSemantics { },
                )

                !state.photoRemoved && state.storedContact?.hasPhoto == true ->
                    ContactPhoto(contact = state.storedContact, modifier = Modifier.fillMaxSize())

                else -> PlaceholderAvatar(
                    color = state.placeholderColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        FilledActionButton(
            text = stringResource(
                if (hasPhoto) R.string.editor_change_photo else R.string.editor_choose_photo,
            ),
            icon = Icons.Filled.Person,
            enabled = enabled,
            onClick = onPickPhoto,
        )

        if (hasPhoto) {
            QuietActionButton(
                text = stringResource(R.string.editor_remove_photo),
                enabled = enabled,
                danger = true,
                onClick = onRemovePhoto,
            )
        } else {
            Text(
                text = stringResource(R.string.editor_photo_missing),
                style = LocalAppTextStyles.current.caption,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColorSection(
    selectedColor: PlaceholderColor,
    onSelect: (PlaceholderColor) -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.touchGap)) {
        Text(
            text = stringResource(R.string.editor_placeholder_label),
            style = styles.section,
            color = AppColors.TextSecondary,
        )
        // Three per row. Six 56 dp swatches with 12 dp gaps need 396 dp, which does
        // not fit the 355 dp of content a 411 dp phone offers - the last swatch was
        // being squeezed into a deformed 39 dp ellipse below the minimum touch size.
        // Three per row, and each one takes a third of the width. Fixed 56 dp dots left
        // the right-hand two thirds of the row empty, which read as a layout that had
        // given up rather than as six choices.
        PlaceholderColor.entries.chunked(SWATCHES_PER_ROW).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.touchGap),
            ) {
                rowColors.forEach { color ->
                    val isSelected = color == selectedColor
                    val label = placeholderColorLabel(color)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                    Box(
                        modifier = Modifier
                            .size(dimens.minTouchTarget)
                            .clip(CircleShape)
                            .background(AppColors.placeholderBackground(color))
                            .border(
                                width = if (isSelected) 4.dp else 1.dp,
                                color = if (isSelected) AppColors.Focus else AppColors.Outline,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(color) }
                            // The visual selection cue is a border width, which a
                            // screen reader cannot convey, so the colour is named and
                            // the selected state is exposed explicitly.
                            .clearAndSetSemantics {
                                contentDescription = label
                                role = Role.RadioButton
                                selected = isSelected
                                onClick(label = label) {
                                    onSelect(color)
                                    true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // A tick inside the chosen swatch. A ring alone is easy to miss
                        // at arm's length, and the person choosing the colour is usually
                        // doing it for someone else's eyes rather than their own.
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = AppColors.Focus,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics { },
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun placeholderColorLabel(color: PlaceholderColor): String = stringResource(
    when (color) {
        PlaceholderColor.LIGHT_BLUE -> R.string.placeholder_light_blue
        PlaceholderColor.LIGHT_AMBER -> R.string.placeholder_light_amber
        PlaceholderColor.LIGHT_PURPLE -> R.string.placeholder_light_purple
        PlaceholderColor.LIGHT_TEAL -> R.string.placeholder_light_teal
        PlaceholderColor.LIGHT_ROSE -> R.string.placeholder_light_rose
        PlaceholderColor.LIGHT_GRAY -> R.string.placeholder_light_gray
    },
)

@Composable
private fun LoadingEditor(modifier: Modifier = Modifier) {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(dimens.pagePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = AppColors.Ink,
            modifier = Modifier.size(dimens.primaryGlyph),
        )
        Text(
            text = stringResource(R.string.editor_loading),
            style = styles.body,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = dimens.touchGap),
        )
    }
}

@Composable
private fun Notice(text: String, danger: Boolean = false) {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    val content = if (danger) AppColors.DangerRed else AppColors.TextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(if (danger) AppColors.DangerSoft else AppColors.SurfaceSunken)
            .padding(dimens.spaceRoomy),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (danger) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(dimens.secondaryGlyph)
                .clearAndSetSemantics { },
        )
        Text(
            text = text,
            style = styles.caption,
            color = content,
            modifier = Modifier.padding(start = dimens.spaceSnug),
        )
    }
}

@Composable
private fun nameSupport(reason: DisplayNameRules.Reason?): String = when (reason) {
    null -> stringResource(R.string.editor_name_example)
    DisplayNameRules.Reason.EMPTY -> stringResource(R.string.editor_name_required)
    DisplayNameRules.Reason.TOO_LONG -> stringResource(R.string.editor_name_too_long)
    DisplayNameRules.Reason.CONTROL_CHARACTER,
    DisplayNameRules.Reason.INVALID_UNICODE,
    -> stringResource(R.string.editor_name_invalid)
}

/**
 * Help for the number field.
 *
 * Once a valid number has been typed it says what will actually be dialled, which is
 * the only way to see the effect of the dialling-code setting from here. Before that
 * it says what the field accepts.
 */
@Composable
private fun phoneSupport(
    reason: PhoneNumberRules.Reason?,
    phone: String,
    countryCode: String,
): String = when {
    reason == PhoneNumberRules.Reason.EMPTY -> stringResource(R.string.editor_phone_required)
    reason != null -> stringResource(R.string.editor_phone_invalid)
    phone.isBlank() -> stringResource(R.string.editor_phone_format_hint)
    else -> stringResource(
        R.string.editor_dial_preview,
        CountryCode.apply(phone.trim(), countryCode),
    )
}
