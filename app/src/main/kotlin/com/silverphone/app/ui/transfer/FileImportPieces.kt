package com.silverphone.app.ui.transfer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.ImportCommitResult
import com.silverphone.app.domain.ImportedContact
import com.silverphone.app.platform.transfer.BackupReader
import com.silverphone.app.ui.components.PRESSED_SCALE_GENTLE
import com.silverphone.app.ui.components.PlaceholderAvatar
import com.silverphone.app.ui.components.PressHaptics
import com.silverphone.app.ui.components.FilledActionButton
import com.silverphone.app.ui.components.StatusCard
import com.silverphone.app.ui.components.StatusTone
import com.silverphone.app.ui.components.pressScale
import com.silverphone.app.ui.components.rememberPressFeedback
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * The states S08 can be in, kept apart from the screen that arranges them: nothing
 * chosen, checking, refused, one file entry, one mode choice, the result, and the
 * explicit replace confirmation.
 */

private val THUMB = 64.dp

@Composable
internal fun IdleBody(onChoose: () -> Unit) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Scrollable, so the action stays reachable when the text is large.
            .verticalScroll(rememberScrollState())
            .padding(top = dimens.touchGap),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        Text(
            text = stringResource(R.string.import_choose_hint),
            style = styles.body,
            color = AppColors.TextSecondary,
        )
        FilledActionButton(
            text = stringResource(R.string.import_choose),
            icon = Icons.Filled.Refresh,
            onClick = onChoose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun CheckingBody() {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = AppColors.Ink,
            modifier = Modifier.size(dimens.primaryGlyph),
        )
        Text(
            text = stringResource(R.string.import_checking),
            style = styles.body,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(top = dimens.touchGap),
        )
    }
}

@Composable
internal fun RejectedBody(reason: BackupReader.Reason, onChooseAgain: () -> Unit) {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    val message = when (reason) {
        BackupReader.Reason.UNSUPPORTED_VERSION -> stringResource(R.string.import_invalid_version)
        BackupReader.Reason.PHOTO_MISSING,
        BackupReader.Reason.PHOTO_MISMATCH,
        BackupReader.Reason.PHOTO_INVALID,
        -> stringResource(R.string.import_invalid_photo)

        BackupReader.Reason.EMPTY_CONTACTS -> stringResource(R.string.import_preview_empty)
        else -> stringResource(R.string.import_invalid)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = dimens.touchGap),
        verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
    ) {
        StatusCard(text = message, tone = StatusTone.DANGER, icon = Icons.Filled.Warning)
        Text(
            text = stringResource(R.string.import_rejected_hint),
            style = styles.body,
            color = AppColors.TextSecondary,
        )
        FilledActionButton(
            text = stringResource(R.string.import_choose),
            icon = Icons.Filled.Refresh,
            onClick = onChooseAgain,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun FileEntryRow(contact: ImportedContact) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Hairline, RoundedCornerShape(dimens.cardCorner))
            .padding(dimens.cardInnerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = remember(contact.id) {
            contact.photo?.jpegBytes?.let { bytes ->
                android.graphics.BitmapFactory
                    .decodeByteArray(bytes, 0, bytes.size)
                    ?.asImageBitmap()
            }
        }
        Box(
            modifier = Modifier
                .size(THUMB)
                .clip(RoundedCornerShape(dimens.photoCorner)),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlaceholderAvatar(
                    color = contact.placeholderColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(dimens.cardInnerPadding))
        Column {
            Text(text = contact.displayName, style = styles.button, color = AppColors.TextPrimary)
            Text(text = contact.phoneNumber, style = styles.caption, color = AppColors.TextSecondary)
        }
    }
}

@Composable
internal fun ModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val accent = if (danger) AppColors.DangerRed else AppColors.Ink
    val shape = RoundedCornerShape(dimens.cardCorner)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE_GENTLE)
    PressHaptics(interactionSource)
    val fill by animateColorAsState(
        targetValue = when {
            press.pressed -> AppColors.SurfacePressed
            selected && danger -> AppColors.DangerSoft
            selected -> AppColors.InkSoft
            else -> AppColors.Surface
        },
        animationSpec = tween(90),
        label = "modeFill",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(press.scale)
            .clip(shape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else AppColors.Hairline,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accent),
                role = Role.RadioButton,
                onClick = onClick,
            )
            // Selection was signalled only by a border width, which a screen reader
            // cannot convey and a low-vision user can easily miss.
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.RadioButton
                this.selected = selected
                onClick(label = label) {
                    onClick()
                    true
                }
            }
            .heightIn(min = dimens.minTouchTarget)
            .padding(dimens.cardInnerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimens.secondaryGlyph)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(accent)
                    } else {
                        Modifier.border(2.dp, AppColors.Outline, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AppColors.Surface,
                    modifier = Modifier
                        .size(20.dp)
                        .clearAndSetSemantics { },
                )
            }
        }
        Text(
            text = label,
            style = styles.body,
            color = if (danger) AppColors.DangerRed else AppColors.TextPrimary,
            modifier = Modifier.padding(start = dimens.cardInnerPadding),
        )
    }
}

@Composable
internal fun DoneBody(result: ImportCommitResult) {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    val message = when (result) {
        is ImportCommitResult.Appended -> stringResource(
            R.string.import_result_append,
            result.added,
            result.skipped,
        )

        is ImportCommitResult.Replaced -> stringResource(
            R.string.import_result_replace,
            result.total,
        )

        ImportCommitResult.NoChanges -> stringResource(R.string.import_result_none)
        ImportCommitResult.StalePreview -> stringResource(R.string.import_stale_preview)
        is ImportCommitResult.CapacityExceeded -> stringResource(
            R.string.import_capacity,
            result.limit,
        )

        is ImportCommitResult.StorageFailed -> stringResource(R.string.storage_insufficient)
    }
    val danger = result is ImportCommitResult.StalePreview ||
        result is ImportCommitResult.CapacityExceeded ||
        result is ImportCommitResult.StorageFailed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = dimens.touchGap),
    ) {
        StatusCard(
            text = message,
            tone = if (danger) StatusTone.DANGER else StatusTone.GOOD,
            icon = if (danger) Icons.Filled.Warning else Icons.Filled.Check,
        )
    }
}

/**
 * The dangerous confirmation. It states both counts and offers "export first", so
 * replacing is never something a family member can do by accident.
 */
@Composable
internal fun ReplaceConfirmation(
    existingCount: Int,
    incomingCount: Int,
    onExportFirst: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val styles = LocalAppTextStyles.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.import_mode_replace), style = styles.body) },
        text = {
            Text(
                text = stringResource(
                    R.string.import_replace_warning,
                    existingCount,
                    incomingCount,
                ),
                style = styles.caption,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(
                    text = stringResource(R.string.import_confirm_replace),
                    style = styles.body,
                    color = AppColors.DangerRed,
                )
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onExportFirst, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(
                        text = stringResource(R.string.import_replace_export_first),
                        style = styles.caption,
                    )
                }
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.import_cancel), style = styles.caption)
                }
            }
        },
    )
}
