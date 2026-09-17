package com.silverphone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * One row of the family menu: a large glyph, a title, and one line of explanation.
 *
 * Shared by the family menu and the import/export screen so a family member meets
 * exactly the same shape on both, and so a change to the row only has to be made
 * once.
 */
@Composable
fun SettingsEntry(
    icon: Painter,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .heightIn(min = dimens.minTouchTarget)
            .padding(horizontal = dimens.cardInnerPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            // Decorative: the title next to it already says what this does.
            contentDescription = null,
            tint = AppColors.Ink,
            modifier = Modifier
                .size(dimens.primaryGlyph)
                .clearAndSetSemantics { },
        )
        Spacer(Modifier.width(dimens.cardInnerPadding))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = styles.body, color = AppColors.TextPrimary)
            Text(
                text = description,
                style = styles.caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
