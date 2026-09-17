package com.silverphone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.silverphone.app.domain.Contact
import com.silverphone.app.domain.PlaceholderColor
import com.silverphone.app.platform.photos.ContactPhotoModel
import com.silverphone.app.ui.theme.AppColors

/** The single image loader, shared by every photo in the app. */
val LocalImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("ImageLoader was read outside the application root")
}

/**
 * A person outline on the contact's stored placeholder colour.
 *
 * The colour is part of how a card is recognised, so it comes from the stored
 * contact and never changes between launches. The outline is shared by all
 * contacts: it is a mild hint, not a substitute for a real photo, which is why
 * the management screen also says the photo is missing.
 */
@Composable
fun PlaceholderAvatar(
    color: PlaceholderColor,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(AppColors.placeholderBackground(color)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = AppColors.Ink,
            modifier = Modifier
                .fillMaxSize(0.62f)
                .clearAndSetSemantics { },
        )
    }
}

/**
 * The picture area of a card: the stored photo when there is one, otherwise the
 * placeholder. Decorative from a screen-reader point of view, because the card
 * itself carries the single spoken label.
 */
@Composable
fun ContactPhoto(
    contact: Contact,
    modifier: Modifier = Modifier,
) {
    val sha256 = contact.photoSha256
    if (sha256 == null) {
        // A placeholder needs no image loader at all, so it is not looked up: a
        // contacts list made only of placeholders must not depend on one.
        PlaceholderAvatar(color = contact.placeholderColor, modifier = modifier)
        return
    }
    AsyncImage(
        model = ContactPhotoModel(contactId = contact.id, sha256 = sha256),
        contentDescription = null,
        imageLoader = LocalImageLoader.current,
        contentScale = ContentScale.Crop,
        modifier = modifier.clearAndSetSemantics { },
    )
}
