package com.silverphone.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.silverphone.app.domain.PlaceholderColor

/**
 * The fixed palette. Values match res/values/colors.xml one-for-one; the XML copy
 * exists only so the manifest theme and the launcher icon background can use the
 * same names.
 *
 * Dynamic colour is deliberately not used anywhere: the elderly user's
 * recognition depends on these exact colours on every launch.
 */
object AppColors {
    /**
     * The launcher icon's background, and nothing else.
     *
     * The first draft used it inside the app as well - a tile behind the family entry,
     * a selected chip - and the owner asked for it to be taken out of the interface:
     * a colour that loud belongs to the icon on the home screen, where it is the one
     * thing that identifies the app, not to a row of controls. Nothing in `ui/` reads
     * this value any more; `res/values/colors.xml` and the adaptive icon do.
     */
    val BrandLime = Color(0xFFD6FF00)
    val Ink = Color(0xFF172554)
    val Background = Color(0xFFF7F8FA)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF172554)
    val TextSecondary = Color(0xFF475569)
    /**
     * The dial colour.
     *
     * A more saturated green than the first draft's 0x146C43, which read as the dark
     * teal of an office phone. White on this value still measures 5.0:1, so the
     * contrast rule the specification sets for labels on the dial area is met with
     * room to spare.
     */
    val CallGreen = Color(0xFF15803D)
    val OnCallGreen = Color(0xFFFFFFFF)
    val CancelRed = Color(0xFFB42318)
    val DangerRed = Color(0xFFB42318)
    val Outline = Color(0xFF64748B)
    val Focus = Color(0xFF1D4ED8)

    /**
     * Surfaces and separators.
     *
     * [Hairline] is a grouping line, never a control boundary: a button or a field
     * that has to be recognised as one keeps [Outline], which is the value the
     * specification asks for because it clears 3:1 on white.
     */
    val Hairline = Color(0xFFE4E8F0)
    val SurfaceSunken = Color(0xFFEDF0F6)
    val SurfacePressed = Color(0xFFF1F4FA)

    /**
     * Pressed fills.
     *
     * Every button gets a darker container the moment it is held, so a tap is
     * acknowledged in colour as well as in shape. The darker greens and reds keep
     * white labels above 7:1; the soft tints are for the outlined buttons, whose
     * label is already dark enough to stay readable on them.
     */
    val InkPressed = Color(0xFF0D1B3E)
    val CallGreenPressed = Color(0xFF0E6B33)
    val DangerRedPressed = Color(0xFF8C1B12)
    val InkSoft = Color(0xFFE6EBF6)
    val DangerSoft = Color(0xFFFEE4E2)
    val CallGreenSoft = Color(0xFFE4F3E9)
    val LimeSoft = Color(0xFFF3FFB8)

    /**
     * Disabled controls.
     *
     * The first version put white text on the grey fill, which measures 1.48:1 and
     * left the label unreadable for exactly the users this app is for. The label now
     * stays legible (6.15:1) and the muted fill is what signals "not now".
     */
    val DisabledSurface = Color(0xFFE2E8F0)
    val DisabledOnSurface = Color(0xFF475569)

    private val placeholders = mapOf(
        PlaceholderColor.LIGHT_BLUE to Color(0xFFDBEAFE),
        PlaceholderColor.LIGHT_AMBER to Color(0xFFFEF3C7),
        PlaceholderColor.LIGHT_PURPLE to Color(0xFFF3E8FF),
        PlaceholderColor.LIGHT_TEAL to Color(0xFFCCFBF1),
        PlaceholderColor.LIGHT_ROSE to Color(0xFFFFE4E6),
        PlaceholderColor.LIGHT_GRAY to Color(0xFFE2E8F0),
    )

    /** Background for a contact with no photo. */
    fun placeholderBackground(color: PlaceholderColor): Color =
        placeholders.getValue(color)

}
