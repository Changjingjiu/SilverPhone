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
    val BrandLime = Color(0xFFD6FF00)
    val Ink = Color(0xFF172554)
    val Background = Color(0xFFF7F8FA)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF172554)
    val TextSecondary = Color(0xFF475569)
    val CallGreen = Color(0xFF146C43)
    val OnCallGreen = Color(0xFFFFFFFF)
    val CancelRed = Color(0xFFB42318)
    val DangerRed = Color(0xFFB42318)
    val Outline = Color(0xFF64748B)
    val Focus = Color(0xFF1D4ED8)

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
