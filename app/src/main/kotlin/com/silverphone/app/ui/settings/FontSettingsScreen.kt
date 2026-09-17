package com.silverphone.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.FontPreset
import com.silverphone.app.ui.components.CancelActionButton
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/** Size of the sample face in the preview card. */
private val PREVIEW_FACE = 44.dp

/**
 * S10: the four text-size presets.
 *
 * The whole page is rendered at the preset being previewed, and only the option list
 * scrolls. The preview itself is pinned above the buttons, because it is the thing
 * being chosen *for*: when it was part of the scrolling block, the fixed button row
 * sliced it in half at the standard preset, which reads as a broken layout rather
 * than as a page that scrolls. A half-visible option row is a familiar "scroll for
 * more" affordance; a half-visible card is not.
 *
 * The preview uses local shapes, never a real dial action.
 */
@Composable
fun FontSettingsScreen(
    state: FontUiState,
    onSelect: (FontPreset) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.padding(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.pagePadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            Text(
                text = stringResource(R.string.font_title),
                style = styles.pageTitle,
                color = AppColors.TextPrimary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimens.pagePadding,
                    end = dimens.pagePadding,
                    top = dimens.touchGap,
                    bottom = dimens.touchGap,
                ),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            Text(
                text = stringResource(R.string.font_hint),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )
            FontPreset.entries.forEach { preset ->
                PresetOption(
                    preset = preset,
                    selected = preset == state.selected,
                    onClick = { onSelect(preset) },
                )
            }
            Text(
                text = stringResource(R.string.font_scope_note),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )
        }

        Column(
            modifier = Modifier.padding(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            PreviewCard()
        }

        // Stacked, like every other screen. Side by side, each half-width button had
        // 59 dp for its label after the icon and padding, so at 超大 both wrapped to
        // one character per line.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            if (state.saveFailed) {
                Text(
                    text = stringResource(R.string.preferences_save_failed),
                    style = styles.body,
                    color = AppColors.DangerRed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryActionButton(
                text = stringResource(R.string.font_save),
                icon = Icons.Filled.Check,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
            CancelActionButton(
                text = stringResource(R.string.font_cancel),
                icon = Icons.Filled.Clear,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PresetOption(
    preset: FontPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val label = when (preset) {
        FontPreset.STANDARD -> stringResource(R.string.font_standard)
        FontPreset.LARGE -> stringResource(R.string.font_large)
        FontPreset.EXTRA_LARGE -> stringResource(R.string.font_extra_large)
        FontPreset.HUGE -> stringResource(R.string.font_huge)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) AppColors.Focus else AppColors.Outline,
                shape = RoundedCornerShape(dimens.cardCorner),
            )
            .clickable(onClick = onClick)
            // A border thickness and a tick are invisible to a screen reader, so the
            // choice is exposed as a selected radio option.
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
            .padding(horizontal = dimens.cardInnerPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Person,
            contentDescription = null,
            tint = if (selected) AppColors.Focus else AppColors.Outline,
            modifier = Modifier
                .size(dimens.secondaryGlyph)
                .clearAndSetSemantics { },
        )
        Text(
            text = label,
            style = styles.button,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(start = dimens.cardInnerPadding),
        )
    }
}

/**
 * A real card-shaped preview, sized by whatever preset is being previewed.
 *
 * Deliberately compact: the four options above it and the two buttons below it are
 * fixed, so a tall preview was sliced off by the button row at the standard preset -
 * which reads as a broken layout rather than as a page that scrolls. This version
 * fits whole at the default size; at the larger presets the page scrolls, which is
 * what the header explains.
 */
@Composable
private fun PreviewCard() {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The face and the two text roles side by side rather than stacked. Stacked,
        // the card grew tall enough that the option list above it showed two of its
        // four presets: the sample was crowding out the controls it exists to explain.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(PREVIEW_FACE),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = AppColors.Ink,
                    modifier = Modifier
                        .size(dimens.secondaryGlyph)
                        .clearAndSetSemantics { },
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = stringResource(R.string.font_preview_name),
                    style = styles.contactName,
                    color = AppColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.font_preview_body),
                    style = styles.body,
                    color = AppColors.TextSecondary,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.cardCorner))
                .background(AppColors.CallGreen)
                .heightIn(min = dimens.primaryButtonHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = null,
                tint = AppColors.OnCallGreen,
                modifier = Modifier.size(dimens.primaryGlyph),
            )
            Text(
                text = stringResource(R.string.font_preview_button),
                style = styles.button,
                color = AppColors.OnCallGreen,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
