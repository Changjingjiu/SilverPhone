package com.silverphone.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.silverphone.app.domain.FontPreset

val LocalAppDimens = staticCompositionLocalOf<AppDimens> {
    error("AppDimens was read outside SilverPhoneTheme")
}

/**
 * The one theme for the whole app.
 *
 * The colour scheme is a fixed light scheme: no dynamic colour, no dark variant,
 * no theme switcher. Changing [fontPreset] recomposes every screen with new text
 * sizes, which is why switching the preset never needs an Activity restart.
 */
@Composable
fun SilverPhoneTheme(
    fontPreset: FontPreset,
    content: @Composable () -> Unit,
) {
    val textStyles = remember(fontPreset) { appTextStyles(fontPreset) }
    val dimens = remember(fontPreset) { appDimens(fontPreset) }
    val typography = remember(fontPreset) { materialTypography(fontPreset) }

    val colorScheme = remember {
        lightColorScheme(
            primary = AppColors.Ink,
            onPrimary = Color.White,
            // Not BrandLime: the fluorescent yellow-green is the launcher icon's
            // background and nothing else. Wiring it into a container role meant any
            // Material component that reaches for primaryContainer would paint itself
            // in it.
            primaryContainer = AppColors.InkSoft,
            onPrimaryContainer = AppColors.Ink,
            secondary = AppColors.CallGreen,
            onSecondary = AppColors.OnCallGreen,
            secondaryContainer = AppColors.CallGreen,
            onSecondaryContainer = AppColors.OnCallGreen,
            tertiary = AppColors.Ink,
            onTertiary = Color.White,
            background = AppColors.Background,
            onBackground = AppColors.TextPrimary,
            surface = AppColors.Surface,
            onSurface = AppColors.TextPrimary,
            surfaceVariant = AppColors.Background,
            onSurfaceVariant = AppColors.TextSecondary,
            outline = AppColors.Outline,
            outlineVariant = AppColors.Outline,
            error = AppColors.DangerRed,
            onError = Color.White,
            errorContainer = Color(0xFFFEE4E2),
            onErrorContainer = AppColors.DangerRed,
            scrim = Color(0x99000000),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalAppTextStyles provides textStyles,
            LocalAppDimens provides dimens,
            content = content,
        )
    }
}
