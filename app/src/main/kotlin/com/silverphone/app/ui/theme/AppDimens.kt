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
    /** Height of the buttons that are not the main action on their screen. */
    val secondaryButtonHeight: Dp,
    val inputHeight: Dp,
    /** Padding between a card's edge and its content. */
    val cardInnerPadding: Dp,
    /** Size of the glyph inside the primary call area of a card. */
    val primaryGlyph: Dp,
    /** Smaller glyph used by the family-settings entry and secondary actions. */
    val secondaryGlyph: Dp,
    /** Glyph inside a full-width action button, between the two above. */
    val buttonGlyph: Dp,
    /** The four steps every screen uses for space between blocks. */
    val spaceTight: Dp,
    val spaceSnug: Dp,
    val spaceRoomy: Dp,
    val spaceLoose: Dp,
    /** The line between a title and the content under it. */
    val rule: Dp,
    /** Corner of the tinted square that holds a row's glyph. */
    val chipCorner: Dp,
    /** Side of that square, which also sets its glyph. */
    val chipSize: Dp,
    /** Side of the tinted circle behind a whole-screen message glyph. */
    val messageGlyph: Dp,
    /** Corner of the photo inside a card, one step tighter than the card. */
    val photoCorner: Dp,
)

fun appDimens(preset: FontPreset): AppDimens {
    val glyph = when (preset) {
        FontPreset.STANDARD -> 40.dp
        FontPreset.LARGE -> 48.dp
        FontPreset.EXTRA_LARGE -> 56.dp
        FontPreset.HUGE -> 64.dp
    }
    // Row glyphs and chevrons are smaller than the call handset on purpose: they point
    // at something, they are not the thing being pressed.
    val secondary = when (preset) {
        FontPreset.STANDARD -> 26.dp
        FontPreset.LARGE -> 30.dp
        FontPreset.EXTRA_LARGE -> 34.dp
        FontPreset.HUGE -> 38.dp
    }
    val button = when (preset) {
        FontPreset.STANDARD -> 30.dp
        FontPreset.LARGE -> 36.dp
        FontPreset.EXTRA_LARGE -> 42.dp
        FontPreset.HUGE -> 48.dp
    }
    // The containers around glyphs grow with the glyph, so a row never has a small
    // picture floating in a large hole at the bigger presets.
    val chip = when (preset) {
        FontPreset.STANDARD -> 44.dp
        FontPreset.LARGE -> 52.dp
        FontPreset.EXTRA_LARGE -> 60.dp
        FontPreset.HUGE -> 68.dp
    }
    return AppDimens(
        pagePadding = 16.dp,
        // Material 3's large-increased corner. A card the size of a hand has to look
        // softer than a 16 dp box, which is what the first draft used.
        cardCorner = 20.dp,
        cardGap = 16.dp,
        touchGap = 12.dp,
        minTouchTarget = 56.dp,
        primaryButtonHeight = 64.dp,
        secondaryButtonHeight = 56.dp,
        inputHeight = 64.dp,
        cardInnerPadding = 12.dp,
        primaryGlyph = glyph,
        secondaryGlyph = secondary,
        buttonGlyph = button,
        spaceTight = 4.dp,
        spaceSnug = 8.dp,
        spaceRoomy = 16.dp,
        spaceLoose = 24.dp,
        rule = 4.dp,
        chipCorner = 14.dp,
        chipSize = chip,
        messageGlyph = 96.dp,
        photoCorner = 14.dp,
    )
}
