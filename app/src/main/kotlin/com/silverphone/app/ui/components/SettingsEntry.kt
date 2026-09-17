package com.silverphone.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * One row of the family menu: a large glyph in its own square, a title, one line of
 * explanation, and a chevron that says "this opens something".
 *
 * Shared by the family menu, the import/export screen and the About screen so a
 * family member meets exactly the same shape everywhere, and so a change to the row
 * only has to be made once.
 */
@Composable
fun SettingsEntry(
    icon: Painter,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Highlights the row whose screen is open beside the menu. */
    selected: Boolean = false,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val shape = RoundedCornerShape(dimens.cardCorner)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE_GENTLE)
    PressHaptics(interactionSource)
    val fill by animateColorAsState(
        targetValue = when {
            press.pressed -> AppColors.SurfacePressed
            selected -> AppColors.InkSoft
            else -> AppColors.Surface
        },
        animationSpec = tween(90),
        label = "entryFill",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(press.scale)
            .clip(shape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.Ink else AppColors.Hairline,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = AppColors.Ink),
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = dimens.minTouchTarget)
            .padding(horizontal = dimens.cardInnerPadding, vertical = dimens.cardInnerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimens.chipSize)
                .clip(RoundedCornerShape(dimens.chipCorner))
                .background(AppColors.SurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                // Decorative: the title next to it already says what this does.
                contentDescription = null,
                tint = AppColors.Ink,
                modifier = Modifier
                    .size(dimens.secondaryGlyph)
                    .clearAndSetSemantics { },
            )
        }
        Spacer(Modifier.width(dimens.cardInnerPadding))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = styles.button, color = AppColors.TextPrimary)
            Text(
                text = description,
                style = styles.caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(top = dimens.spaceTight),
            )
        }
        Spacer(Modifier.width(dimens.spaceSnug))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier
                .size(dimens.secondaryGlyph)
                .clearAndSetSemantics { },
        )
    }
}

/**
 * A group of rows or paragraphs under one small heading.
 *
 * Used where a screen would otherwise be a wall of text: the heading names the
 * question, the card holds the answer, and the page reads as sections instead of
 * one long column.
 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = styles.section,
                color = AppColors.TextSecondary,
                // Flush with the card's own left edge: a section label that is inset by
                // a few dp reads as a mistake rather than as a hierarchy.
                modifier = Modifier.padding(bottom = dimens.spaceSnug),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.cardCorner))
                .background(AppColors.Surface)
                .border(1.dp, AppColors.Hairline, RoundedCornerShape(dimens.cardCorner))
                .padding(dimens.spaceRoomy),
        ) {
            content()
        }
    }
}

/**
 * A short piece of news inside a tinted card, so a status is a thing on the page
 * rather than a coloured line of text lost between two paragraphs.
 */
@Composable
fun StatusCard(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val (fill, content) = when (tone) {
        StatusTone.NEUTRAL -> AppColors.SurfaceSunken to AppColors.TextSecondary
        StatusTone.GOOD -> AppColors.CallGreenSoft to AppColors.CallGreen
        StatusTone.DANGER -> AppColors.DangerSoft to AppColors.DangerRed
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(fill)
            .padding(dimens.spaceRoomy),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(dimens.secondaryGlyph)
                    .clearAndSetSemantics { },
            )
            Spacer(Modifier.width(dimens.spaceSnug))
        }
        Text(text = text, style = styles.body, color = content)
    }
}

/** The three weights a status line can carry. */
enum class StatusTone {
    NEUTRAL,
    GOOD,
    DANGER,
}
