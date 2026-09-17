package com.silverphone.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.silverphone.app.domain.FontPreset

/**
 * The role-based text styles, at the sizes the 1.0 preset uses.
 *
 * The 1.0 preset is deliberately the ordinary Android scale - 28 sp for a screen
 * title, 16 sp for body text, 14 sp for secondary text - so that at the default
 * setting the app reads like any other app a younger person uses. The presets above
 * it are what the family reaches for when the elderly user needs more.
 *
 * Scaling works by multiplying the base sp value by the preset ratio and then
 * handing the result to Compose, which resolves sp through the system font scale.
 * The system scale is never overwritten, and it is never applied twice.
 */
@Immutable
data class AppTextStyles(
    val pageTitle: TextStyle,
    val contactName: TextStyle,
    val button: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
)

private const val PAGE_TITLE_BASE_SP = 28f
private const val CONTACT_NAME_BASE_SP = 22f
private const val BUTTON_BASE_SP = 16f
private const val BODY_BASE_SP = 16f
private const val CAPTION_BASE_SP = 14f

fun appTextStyles(preset: FontPreset): AppTextStyles {
    val ratio = preset.scale
    return AppTextStyles(
        pageTitle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (PAGE_TITLE_BASE_SP * ratio).sp,
            lineHeight = (PAGE_TITLE_BASE_SP * ratio * 1.35f).sp,
        ),
        contactName = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (CONTACT_NAME_BASE_SP * ratio).sp,
            lineHeight = (CONTACT_NAME_BASE_SP * ratio * 1.35f).sp,
        ),
        button = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (BUTTON_BASE_SP * ratio).sp,
            lineHeight = (BUTTON_BASE_SP * ratio * 1.3f).sp,
        ),
        body = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (BODY_BASE_SP * ratio).sp,
            lineHeight = (BODY_BASE_SP * ratio * 1.4f).sp,
        ),
        caption = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (CAPTION_BASE_SP * ratio).sp,
            lineHeight = (CAPTION_BASE_SP * ratio * 1.4f).sp,
        ),
    )
}

/**
 * Material 3 typography built from the same numbers, so the framework widgets we
 * do use (buttons, text fields, dialogs, snackbars) grow with the app preset
 * instead of staying at their default sizes.
 */
fun materialTypography(preset: FontPreset): Typography {
    val styles = appTextStyles(preset)
    val base = Typography()
    return base.copy(
        displayLarge = styles.pageTitle,
        displayMedium = styles.pageTitle,
        displaySmall = styles.pageTitle,
        headlineLarge = styles.pageTitle,
        headlineMedium = styles.pageTitle,
        headlineSmall = styles.contactName,
        titleLarge = styles.button,
        titleMedium = styles.body,
        titleSmall = styles.caption,
        bodyLarge = styles.body,
        bodyMedium = styles.caption,
        bodySmall = styles.caption,
        labelLarge = styles.button,
        labelMedium = styles.caption,
        labelSmall = styles.caption,
    )
}

val LocalAppTextStyles = staticCompositionLocalOf<AppTextStyles> {
    error("AppTextStyles was read outside SilverPhoneTheme")
}
