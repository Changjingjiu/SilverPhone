package com.silverphone.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.silverphone.app.domain.FontPreset

/**
 * Layout and touch-target sizes for one font preset.
 *
 * Touch targets never shrink below the spec minimum, whatever the preset, and the
 * visual glyphs grow with the preset so they keep pace with the text.
 */
@Immutable
data class AppDimens(
    val pagePadding: Dp,
    val cardCorner: Dp,
    val cardGap: Dp,
    val touchGap: Dp,
    val minTouchTarget: Dp,
    val primaryButtonHeight: Dp,
    val inputHeight: Dp,
    /** Padding between a card's edge and its content. */
    val cardInnerPadding: Dp,
    /** Size of the glyph inside the primary call area of a card. */
    val primaryGlyph: Dp,
    /** Smaller glyph used by the family-settings entry and secondary actions. */
    val secondaryGlyph: Dp,
)

fun appDimens(preset: FontPreset): AppDimens {
    val glyph = when (preset) {
        FontPreset.STANDARD -> 40.dp
        FontPreset.LARGE -> 48.dp
        FontPreset.EXTRA_LARGE -> 56.dp
        FontPreset.HUGE -> 64.dp
    }
    return AppDimens(
        pagePadding = 16.dp,
        cardCorner = 16.dp,
        cardGap = 16.dp,
        touchGap = 12.dp,
        minTouchTarget = 56.dp,
        primaryButtonHeight = 64.dp,
        inputHeight = 64.dp,
        cardInnerPadding = 12.dp,
        primaryGlyph = glyph,
        secondaryGlyph = 32.dp,
    )
}
