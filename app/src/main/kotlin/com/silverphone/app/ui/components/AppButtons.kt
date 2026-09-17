package com.silverphone.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 */

/** Solid dark blue: save, import, enter settings. */
@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = LocalAppDimens.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.primaryButtonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Ink,
            contentColor = Color.White,
            disabledContainerColor = AppColors.DisabledSurface,
            disabledContentColor = AppColors.DisabledOnSurface,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(dimens.cardCorner),
    ) {
        ActionContent(text = text, icon = icon)
    }
}

/** Solid red, destructive: delete, replace everything. Always confirms first. */
@Composable
fun DangerActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = LocalAppDimens.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.primaryButtonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.DangerRed,
            contentColor = Color.White,
            disabledContainerColor = AppColors.DisabledSurface,
            disabledContentColor = AppColors.DisabledOnSurface,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(dimens.cardCorner),
    ) {
        ActionContent(text = text, icon = icon)
    }
}

/** Red outline: cancel, which keeps the data as it was. */
@Composable
fun CancelActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = LocalAppDimens.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.primaryButtonHeight),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(dimens.cardCorner),
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = if (enabled) AppColors.CancelRed else AppColors.DisabledOnSurface,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AppColors.CancelRed,
            disabledContentColor = AppColors.TextSecondary,
        ),
    ) {
        ActionContent(text = text, icon = icon)
    }
}

/** Dark blue outline: go back up one level. Never destructive. */
@Composable
fun BackActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.primaryButtonHeight),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(dimens.cardCorner),
        border = androidx.compose.foundation.BorderStroke(width = 2.dp, color = AppColors.Ink),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
    ) {
        ActionContent(text = text, icon = icon)
    }
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

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimens.minTouchTarget),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (enabled) contentColor else AppColors.DisabledOnSurface,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = AppColors.DisabledOnSurface,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
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

@Composable
private fun ActionContent(text: String, icon: ImageVector) {
    val dimens = LocalAppDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.minTouchTarget)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the button's own label already carries the meaning.
            contentDescription = null,
            modifier = Modifier
                .size(dimens.primaryGlyph)
                .clearAndSetSemantics { },
            tint = androidx.compose.material3.LocalContentColor.current,
        )
        Text(
            text = text,
            style = LocalAppTextStyles.current.button,
            modifier = Modifier
                .padding(start = dimens.touchGap)
                .weight(1f, fill = false),
        )
    }
}
