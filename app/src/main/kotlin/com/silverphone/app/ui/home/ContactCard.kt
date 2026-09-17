package com.silverphone.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.Contact
import com.silverphone.app.ui.components.ContactPhoto
import com.silverphone.app.ui.components.PRESSED_SCALE
import com.silverphone.app.ui.components.PressHaptics
import com.silverphone.app.ui.components.pressScale
import com.silverphone.app.ui.components.rememberPressFeedback
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

private const val PRESS_ANIMATION_MILLIS = 90

/** The test tag for one contact's card. */
fun cardTag(contactId: String): String = "contact-card-$contactId"

/**
 * One relative on the home screen.
 *
 * The card is built the way a modern media card is built: the face bleeds to the
 * card's edges with no white frame around it, the name sits on a clean band under it,
 * and the green call block closes the card as a full-width footer. A soft shadow
 * lifts the whole thing off the page instead of an outline drawn around it.
 *
 * Only the green footer dials. The photo and the name are deliberately inert: the
 * photo is the largest thing on the card, so making the whole card a call target meant
 * a hand resting on it, or a scroll that ended on it, could place a call. Pointing at a
 * face to identify someone and then pressing a button is also the sequence people
 * already know from a phone's own contact list.
 *
 * [reservedNameLines] is the tallest name in this row. Reserving that many lines on
 * every card in the row keeps the row visually even without ever shrinking or
 * ellipsising a name.
 */
@Composable
fun ContactCard(
    contact: Contact,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reservedNameLines: Int = 1,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val shape = RoundedCornerShape(dimens.cardCorner)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Lets a test count the tap targets inside one card, which is how the
            // "the photo must not dial" rule is pinned.
            .testTag(cardTag(contact.id))
            .shadow(4.dp, shape, ambientColor = AppColors.Ink, spotColor = AppColors.Ink)
            .clip(shape)
            .background(AppColors.Surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContactPhoto(
            contact = contact,
            // Square, and as wide as the card: the face is what the elderly user
            // recognises, so nothing is spent on a frame around it.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Text(
            text = contact.displayName,
            style = styles.contactName,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            // Names wrap instead of being squeezed or ellipsised: the name is part of
            // how the card is recognised. It stays a readable node of its own, so a
            // screen reader announces the person before the button that calls them.
            minLines = reservedNameLines,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.cardInnerPadding, vertical = 10.dp),
        )

        // The dial block closes the card: the photo is the hero, the name labels it and
        // the green bar is the one thing on the tile that is meant to be pressed.
        CallButton(
            contactName = contact.displayName,
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The only dialling control on the card.
 *
 * The green block is a button in every sense: it carries the handset glyph and the
 * word 撥打, it is a single accessibility node with the spoken label, it takes the whole
 * width of the card, and it is at least [AppDimens.primaryButtonHeight] tall.
 *
 * It is also the control the elderly user presses most, so it answers a press in every
 * way the platform offers: it turns a darker green, it shrinks a little under the
 * finger, it carries a ripple, it gives a short tick, and it springs back on release.
 * Its bottom corners are rounded by the card that clips it.
 */
@Composable
private fun CallButton(
    contactName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    val spokenLabel = stringResource(R.string.home_call_semantics, contactName)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE)
    PressHaptics(interactionSource, enabled = enabled)
    // Straight across the top, round at the bottom to match the card that holds it: the
    // green block closes the tile instead of floating inside it.
    val shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomEnd = dimens.cardCorner,
        bottomStart = dimens.cardCorner,
    )

    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.SurfaceSunken
            press.pressed -> AppColors.CallGreenPressed
            else -> AppColors.CallGreen
        },
        animationSpec = tween(PRESS_ANIMATION_MILLIS),
        label = "callFill",
    )
    val labelColor = if (enabled) AppColors.OnCallGreen else AppColors.TextSecondary

    Row(
        modifier = modifier
            .pressScale(press.scale)
            .clip(shape)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    color = if (enabled) AppColors.OnCallGreen else AppColors.TextSecondary,
                    bounded = true,
                ),
                enabled = enabled,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = spokenLabel
                role = Role.Button
            }
            .heightIn(min = dimens.primaryButtonHeight)
            .padding(horizontal = dimens.cardInnerPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            // Decorative: the button's own label carries the meaning.
            contentDescription = null,
            tint = labelColor,
            // Sized, not merely given a minimum: an ImageVector already has an
            // intrinsic 24 dp size, so heightIn only enlarged the box around it.
            modifier = Modifier.size(dimens.secondaryGlyph),
        )
        Text(
            text = stringResource(R.string.home_call_badge),
            style = styles.contactName,
            color = labelColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = dimens.spaceSnug),
        )
    }
}
