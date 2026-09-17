package com.silverphone.app.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.domain.Contact
import com.silverphone.app.ui.components.ContactPhoto
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

private val PHOTO_CORNER = 12.dp
private const val PRESSED_SCALE = 0.97f
private const val PRESS_ANIMATION_MILLIS = 120

/** The test tag for one contact's card. */
fun cardTag(contactId: String): String = "contact-card-$contactId"

/**
 * One relative on the home screen.
 *
 * Only the green 撥打 area dials. The photo and the name are deliberately inert: the
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
    /** Side of the square photo, chosen by the grid so the card always fits. */
    photoSide: Dp = 0.dp,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Lets a test count the tap targets inside one card, which is how the
            // "the photo must not dial" rule is pinned.
            .testTag(cardTag(contact.id))
            .shadow(4.dp, RoundedCornerShape(dimens.cardCorner))
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .padding(dimens.cardInnerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContactPhoto(
            contact = contact,
            modifier = Modifier
                .size(photoSide)
                .clip(RoundedCornerShape(PHOTO_CORNER)),
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = contact.displayName,
            style = styles.contactName,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            // Names wrap instead of being squeezed or ellipsised: the name is part of
            // how the card is recognised. It stays a readable node of its own, so a
            // screen reader announces the person before the button that calls them.
            minLines = reservedNameLines,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

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
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = tween(PRESS_ANIMATION_MILLIS),
        label = "callScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 4.dp,
        animationSpec = tween(PRESS_ANIMATION_MILLIS),
        label = "callElevation",
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation,
                RoundedCornerShape(PHOTO_CORNER),
                ambientColor = AppColors.CallGreen,
                spotColor = AppColors.CallGreen,
            )
            .clip(RoundedCornerShape(PHOTO_CORNER))
            .background(AppColors.CallGreen)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = AppColors.OnCallGreen),
                enabled = enabled,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = spokenLabel
                role = Role.Button
            }
            .heightIn(min = dimens.primaryButtonHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            // Decorative: the button's own label carries the meaning.
            contentDescription = null,
            tint = AppColors.OnCallGreen,
            // Sized, not merely given a minimum: an ImageVector already has an
            // intrinsic 24 dp size, so heightIn only enlarged the box around it and the
            // handset stayed small next to a 25.6 sp label.
            modifier = Modifier.size(dimens.primaryGlyph),
        )
        Text(
            text = stringResource(R.string.home_call_badge),
            style = styles.button,
            color = AppColors.OnCallGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
