package com.silverphone.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * The shared press feedback.
 *
 * A tap has to be acknowledged three times over, because the people using this app
 * are the least likely to notice a subtle one: the control shrinks under the finger,
 * its fill turns to the darker pressed tone, and the platform gives a short tick.
 * The release is a spring with a little give in it, so the control visibly comes
 * back rather than cutting straight to its resting size.
 *
 * Every one of these is optional to the machine - a device with haptics turned off,
 * or a system animation scale of zero, still completes the action.
 */

/** How far a filled control - a button, a card row - shrinks while it is held. */
const val PRESSED_SCALE = 0.96f

/** The same idea for a large surface, where a deeper shrink would look like a slip. */
const val PRESSED_SCALE_GENTLE = 0.985f

private const val PRESS_IN_MILLIS = 90

/** Where a control is in its press cycle, and how big it should be drawn. */
@Immutable
data class PressFeedback(
    val pressed: Boolean,
    val scale: Float,
)

/**
 * The press cycle for one control.
 *
 * Pressing is fast and linear so the feedback arrives with the touch; releasing is a
 * spring that overshoots by a hair, which is what makes the control read as springing
 * back instead of fading back.
 */
@Composable
fun rememberPressFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = PRESSED_SCALE,
): PressFeedback {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) {
            tween(PRESS_IN_MILLIS)
        } else {
            spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
        },
        label = "pressScale",
    )
    return remember(pressed, scale) { PressFeedback(pressed = pressed, scale = scale) }
}

/** Applies a press scale without changing the control's measured size. */
fun Modifier.pressScale(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}

/**
 * A short tick as the finger lands.
 *
 * [HapticFeedbackConstants.VIRTUAL_KEY] is the lightest tick the platform exposes on
 * every supported release, and it is silenced by the system when the user has turned
 * haptic feedback off, so this never has to ask.
 */
@Composable
fun PressHaptics(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
) {
    if (!enabled) return
    val view = LocalView.current
    LaunchedEffect(interactionSource, view) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press && view.isHapticFeedbackEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
}
