package com.silverphone.app.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowSizeClass

/**
 * The window this composition is running in, in Google's own vocabulary.
 *
 * Every structural decision in the app - one column of relatives or two, one pane or
 * two - reads these values, which the adaptive library derives from the window itself.
 * Nothing branches on a device model, a screen diagonal or a raw pixel count: the same
 * size class produces the same layout on a phone, on a tablet, on a foldable, and on a
 * phone in split screen, which is the point of the breakpoints.
 */
@Immutable
data class WindowLayout(
    val width: WidthClass,
    val height: HeightClass,
) {
    enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

    enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

    /** Narrower than 600 dp: a phone standing up, or a small split-screen window. */
    val isCompactWidth: Boolean get() = width == WidthClass.COMPACT

    /** 840 dp and wider: a tablet, a desktop window, or a phone unfolded. */
    val isExpandedWidth: Boolean get() = width == WidthClass.EXPANDED

    /** Two panes need the width; a short window keeps the single-pane flow. */
    val supportsTwoPanes: Boolean get() = width != WidthClass.COMPACT
}

@Composable
fun rememberWindowLayout(): WindowLayout {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return remember(sizeClass) {
        WindowLayout(
            width = sizeClass.widthClass(),
            height = sizeClass.heightClass(),
        )
    }
}

private fun WindowSizeClass.widthClass(): WindowLayout.WidthClass = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
        WindowLayout.WidthClass.EXPANDED

    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
        WindowLayout.WidthClass.MEDIUM

    else -> WindowLayout.WidthClass.COMPACT
}

private fun WindowSizeClass.heightClass(): WindowLayout.HeightClass = when {
    isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND) ->
        WindowLayout.HeightClass.EXPANDED

    isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
        WindowLayout.HeightClass.MEDIUM

    else -> WindowLayout.HeightClass.COMPACT
}
