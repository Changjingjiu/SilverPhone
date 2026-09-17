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
 * Two rules keep the app from looking like several apps: every style below is one of
 * six roles (nothing in `ui/` sets its own size or weight), and the emphasis roles are
 * Medium rather than SemiBold, which is what the owner asked for after reading the
 * first build - the heavier weight made ordinary labels shout.
 *
 * The 1.0 preset is the ordinary size text has on a phone: 20 sp for a screen title,
 * 16 sp for the name on a card, 14 sp for body text and buttons, 12 sp for the small
 * print. It is the default because a page whose every line is already enlarged is a
 * page that is crowded rather than clear - the family raises the size when the person
 * reading it needs that, with the three presets above.
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
    /** The small heading above a group of rows, distinct from a paragraph. */
    val section: TextStyle,
)

private const val PAGE_TITLE_BASE_SP = 20f
private const val CONTACT_NAME_BASE_SP = 16f
private const val BUTTON_BASE_SP = 14f
private const val BODY_BASE_SP = 14f
private const val CAPTION_BASE_SP = 12f

fun appTextStyles(preset: FontPreset): AppTextStyles {
    val ratio = preset.scale
    return AppTextStyles(
        pageTitle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (PAGE_TITLE_BASE_SP * ratio).sp,
            lineHeight = (PAGE_TITLE_BASE_SP * ratio * 1.35f).sp,
            letterSpacing = 0.sp,
        ),
        contactName = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (CONTACT_NAME_BASE_SP * ratio).sp,
            lineHeight = (CONTACT_NAME_BASE_SP * ratio * 1.35f).sp,
            letterSpacing = 0.sp,
        ),
        button = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (BUTTON_BASE_SP * ratio).sp,
            lineHeight = (BUTTON_BASE_SP * ratio * 1.3f).sp,
            letterSpacing = 0.sp,
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
        // A hair of letter spacing is what separates "a heading over a group" from
        // "a sentence" without changing the size or the weight.
        section = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (CAPTION_BASE_SP * ratio).sp,
            lineHeight = (CAPTION_BASE_SP * ratio * 1.3f).sp,
            letterSpacing = 0.6.sp,
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
