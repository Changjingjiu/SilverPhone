package com.silverphone.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * The frame every family screen sits in.
 *
 * These screens belong to the person who set the phone up, not to the person who
 * makes calls, so they use the canonical Android scaffolding: a Material 3 [Scaffold]
 * with a [TopAppBar], and the [PaddingValues] it produces are handed to the content
 * instead of being dropped. The home screen is the only place built around the elderly
 * user, and the only place that keeps the oversized targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    title: String,
    /**
     * One muted line under the title, on every family screen.
     *
     * The two panes of the settings scaffold are read side by side, so their content
     * has to start on the same line: a pane with a subtitle and a pane without one
     * pushed their first rows a whole line apart, which is what makes a two-pane
     * layout look broken rather than dense.
     */
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = LocalAppTextStyles.current.pageTitle,
                        color = AppColors.TextPrimary,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backLabel,
                                tint = AppColors.Ink,
                            )
                        }
                    }
                },
                actions = actions ?: {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextPrimary,
                    navigationIconContentColor = AppColors.Ink,
                    actionIconContentColor = AppColors.Ink,
                ),
            )
        },
        content = { inner ->
            // The scaffold's insets are applied exactly once, to the column that holds
            // the screen. The 600 dp measure keeps a row of text from stretching the
            // width of a tablet; on a phone it has no effect at all.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(inner),
                ) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = LocalAppTextStyles.current.caption,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(
                                start = LocalAppDimens.current.pagePadding,
                                end = LocalAppDimens.current.pagePadding,
                                top = LocalAppDimens.current.spaceSnug,
                            ),
                        )
                    }
                    content()
                }
            }
        },
    )
}

/** A filled button at the size an ordinary app uses: 44 dp, and only as wide as it needs. */
@Composable
fun FilledActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    /** False paints the quiet tonal fill instead of the full-strength one. */
    emphasis: Boolean = true,
) {
    val styles = LocalAppTextStyles.current
    val container = when {
        danger -> AppColors.DangerRed
        emphasis -> AppColors.Ink
        else -> AppColors.SurfaceSunken
    }
    val containerPressed = when {
        danger -> AppColors.DangerRedPressed
        emphasis -> AppColors.InkPressed
        else -> AppColors.InkSoft
    }
    val labelColor = when {
        !enabled -> AppColors.DisabledOnSurface
        danger || emphasis -> Color.White
        else -> AppColors.Ink
    }

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource)
    PressHaptics(interactionSource, enabled = enabled)
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.DisabledSurface
            press.pressed -> containerPressed
            else -> container
        },
        animationSpec = tween(90),
        label = "filledFill",
    )

    Row(
        modifier = modifier
            .sizeIn(maxWidth = 360.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = labelColor),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier
                    .size(20.dp)
                    .clearAndSetSemantics { },
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = styles.button, color = labelColor)
    }
}

/**
 * A text action: the shape an app bar uses for Save, and the shape a form uses for the
 * destructive action it does not want pressed by accident.
 */
@Composable
fun QuietActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val styles = LocalAppTextStyles.current
    val content = when {
        !enabled -> AppColors.DisabledOnSurface
        danger -> AppColors.DangerRed
        else -> AppColors.Ink
    }
    val pressedFill = if (danger) AppColors.DangerSoft else AppColors.InkSoft

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE_GENTLE)
    PressHaptics(interactionSource, enabled = enabled)
    val fill by animateColorAsState(
        targetValue = if (enabled && press.pressed) pressedFill else Color.Transparent,
        animationSpec = tween(90),
        label = "quietFill",
    )

    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = styles.button, color = content)
    }
}
