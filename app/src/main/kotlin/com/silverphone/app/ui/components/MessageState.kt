package com.silverphone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * A whole-screen message: empty, loading, permission or failure.
 *
 * Always scrollable, because at the largest font preset these screens are taller
 * than a phone display and the actions must stay reachable. The content never
 * shows a stack trace or an internal code.
 */
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.pagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The glyph sits on its own tinted disc: it gives the empty and failure states
        // a centre of gravity, and it keeps the picture from reading as a stray icon
        // floating above a paragraph.
        Box(
            modifier = Modifier
                .size(dimens.messageGlyph)
                .clip(CircleShape)
                .background(AppColors.SurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.Ink,
                modifier = Modifier
                    .size(dimens.messageGlyph / 2)
                    .clearAndSetSemantics { },
            )
        }
        Spacer(Modifier.height(dimens.spaceLoose))
        Text(
            text = title,
            // A message is a sentence, not a heading: at the standard size the title
            // outline made an empty screen shout across the whole display.
            style = styles.contactName,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hint != null) {
            Spacer(Modifier.height(dimens.touchGap))
            Text(
                text = hint,
                style = styles.body,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (actions != null) {
            Spacer(Modifier.height(dimens.spaceLoose + dimens.spaceSnug))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
            ) {
                actions()
            }
        }
    }
}
