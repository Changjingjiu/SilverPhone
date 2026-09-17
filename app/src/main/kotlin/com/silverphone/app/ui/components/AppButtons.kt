package com.silverphone.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * The action buttons.
 *
 * Every action carries a glyph as well as a colour and a word, so the meanings
 * stay distinguishable in grayscale and without reading: a red outlined cross
 * cancels, a red solid bin deletes, and a dark-blue solid tick saves or enters.
 * The green call area inside a contact card is drawn by the card itself.
 *
 * They are built on one pressable surface rather than on the Material button, because
 * a button has to answer the finger: it darkens, it sinks, it gives a short tick, and
 * it springs back when the finger leaves. Material's button only tints the fill by a
 * few percent, which is invisible on the dark fills this app uses.
 */

/** Long enough to be seen while the finger is down, short enough to feel immediate. */
private const val PRESS_FILL_MILLIS = 90

/** Solid dark blue: save, import, enter settings. */
@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = AppColors.Ink,
        containerPressed = AppColors.InkPressed,
        content = Color.White,
    )
}

/**
 * Dark blue, quietly filled: go back up one level. Never destructive.
 *
 * Softer than the solid buttons on purpose. A back action is on almost every screen,
 * and drawing it as a full-strength outlined block made the way out the loudest thing
 * on the page. The arrow and the word still carry the meaning in greyscale.
 */
@Composable
fun BackActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        enabled = true,
        container = AppColors.SurfaceSunken,
        containerPressed = AppColors.InkSoft,
        content = AppColors.Ink,
        height = LocalAppDimens.current.secondaryButtonHeight,
    )
}

/**
 * A labelled, glyph-carrying button that fits beside its siblings.
 *
 * The label is kept even where an icon alone would be understood by a designer:
 * family members are not necessarily confident with icons, and the spec asks for
 * the reorder actions to say 上移 / 下移 rather than show only arrows.
 */
@Composable
fun CompactActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val contentColor = when {
        !enabled -> AppColors.DisabledOnSurface
        danger -> AppColors.DangerRed
        else -> AppColors.Ink
    }
    val pressedFill = if (danger) AppColors.DangerSoft else AppColors.InkSoft
    // A chip, not a stadium: three of these share one row, and three full-round
    // outlines read as decoration rather than as three quiet controls.
    val shape = RoundedCornerShape(dimens.chipCorner)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource)
    PressHaptics(interactionSource, enabled = enabled)
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.DisabledSurface
            press.pressed -> pressedFill
            else -> AppColors.Surface
        },
        animationSpec = tween(PRESS_FILL_MILLIS),
        label = "compactFill",
    )

    Row(
        modifier = modifier
            .pressScale(press.scale)
            // The compact controls only appear on the family's screens, which are laid
            // out like any other app rather than around a large touch target.
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, contentColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = 8.dp, vertical = 4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(dimens.secondaryGlyph)
                .clearAndSetSemantics { },
        )
        Text(
            text = text,
            style = styles.caption,
            color = contentColor,
            // Wraps rather than being cut mid-glyph: three of these share a row and
            // at 特大 the label no longer fits on one line.
            maxLines = 2,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** The one implementation behind every full-width button. */
@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    containerPressed: Color,
    content: Color,
    border: Color? = null,
    height: androidx.compose.ui.unit.Dp? = null,
) {
    val dimens = LocalAppDimens.current
    val minHeight = height ?: dimens.primaryButtonHeight
    val shape = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource)
    PressHaptics(interactionSource, enabled = enabled)

    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.DisabledSurface
            press.pressed -> containerPressed
            else -> container
        },
        animationSpec = tween(PRESS_FILL_MILLIS),
        label = "actionFill",
    )
    val labelColor = if (enabled) content else AppColors.DisabledOnSurface
    // A button sits a little above the page and flattens onto it while it is held,
    // which is the half of the feedback that the colour alone cannot carry.
    val elevation by animateDpAsState(
        targetValue = if (enabled && !press.pressed) 2.dp else 0.dp,
        animationSpec = tween(PRESS_FILL_MILLIS),
        label = "actionElevation",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(press.scale)
            .shadow(elevation, shape)
            .clip(shape)
            .background(fill)
            .then(
                if (border == null) {
                    Modifier
                } else {
                    Modifier.border(
                        2.dp,
                        if (enabled) border else AppColors.DisabledOnSurface,
                        shape,
                    )
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = labelColor),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = minHeight)
            .padding(horizontal = dimens.cardInnerPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the button's own label already carries the meaning.
            contentDescription = null,
            modifier = Modifier
                .size(dimens.buttonGlyph)
                .clearAndSetSemantics { },
            tint = labelColor,
        )
        Text(
            text = text,
            style = LocalAppTextStyles.current.button,
            color = labelColor,
            modifier = Modifier.padding(start = dimens.touchGap),
        )
    }
}
