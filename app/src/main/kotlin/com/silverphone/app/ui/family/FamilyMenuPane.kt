package com.silverphone.app.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.SettingsEntry
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * The family menu, as the list pane of the settings scaffold.
 *
 * The order is fixed so it is learnable by position. Each entry carries a large
 * glyph, a title and one line of explanation, and the two ways of bringing people
 * in are worded so they can never be confused with each other: one reads the
 * phone's own address book, the other reads a file from another phone.
 *
 * The row whose screen is open beside the menu is drawn as selected, which is the
 * only cue a two-pane layout needs.
 */
@Composable
fun FamilyMenuPane(
    contactCount: Int,
    selected: FamilySection?,
    onSelect: (FamilySection) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    FamilyScreen(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_count, contactCount),
        onBack = onExit,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.spaceRoomy,
                bottom = dimens.pagePadding * 2,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            item(
                key = FamilySection.MANAGE,
                contentType = "entry",
            ) {
                MenuEntry(
                    icon = rememberVectorPainter(Icons.Filled.Person),
                    title = R.string.settings_manage,
                    description = R.string.settings_manage_desc,
                    section = FamilySection.MANAGE,
                    selected = selected,
                    onSelect = onSelect,
                    // Rows keep their identity and slide when the order changes,
                    // instead of the list blinking.
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = FamilySection.CONTACTS_IMPORT, contentType = "entry") {
                MenuEntry(
                    icon = rememberVectorPainter(Icons.Filled.Add),
                    title = R.string.settings_import_contacts,
                    description = R.string.settings_import_contacts_desc,
                    section = FamilySection.CONTACTS_IMPORT,
                    selected = selected,
                    onSelect = onSelect,
                    // Rows keep their identity and slide when the order changes,
                    // instead of the list blinking.
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = FamilySection.TRANSFER, contentType = "entry") {
                MenuEntry(
                    icon = rememberVectorPainter(Icons.Filled.Refresh),
                    title = R.string.settings_transfer,
                    description = R.string.settings_transfer_desc,
                    section = FamilySection.TRANSFER,
                    selected = selected,
                    onSelect = onSelect,
                    // Rows keep their identity and slide when the order changes,
                    // instead of the list blinking.
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = FamilySection.FONT, contentType = "entry") {
                MenuEntry(
                    icon = rememberVectorPainter(Icons.Filled.Edit),
                    title = R.string.settings_font,
                    description = R.string.settings_font_desc,
                    section = FamilySection.FONT,
                    selected = selected,
                    onSelect = onSelect,
                    // Rows keep their identity and slide when the order changes,
                    // instead of the list blinking.
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = FamilySection.PREFERENCES, contentType = "entry") {
                MenuEntry(
                    icon = painterResource(R.drawable.ic_language),
                    title = R.string.settings_preferences,
                    description = R.string.settings_preferences_desc,
                    section = FamilySection.PREFERENCES,
                    selected = selected,
                    onSelect = onSelect,
                    // Rows keep their identity and slide when the order changes,
                    // instead of the list blinking.
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = FamilySection.PERMISSION, contentType = "entry") {
                MenuEntry(
                    icon = rememberVectorPainter(Icons.Filled.Call),
                    title = R.string.settings_permission,
                    description = R.string.settings_permission_desc,
                    section = FamilySection.PERMISSION,
                    selected = selected,
                    onSelect = onSelect,
                    // Rows keep their identity and slide when the order changes,
                    // instead of the list blinking.
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = FamilySection.ABOUT, contentType = "entry") {
                MenuEntry(
                    icon = rememberVectorPainter(Icons.Filled.Info),
                    title = R.string.settings_about,
                    description = R.string.settings_about_desc,
                    section = FamilySection.ABOUT,
                    selected = selected,
                    onSelect = onSelect,
                    // Rows keep their identity and slide when the order changes,
                    // instead of the list blinking.
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun MenuEntry(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: Int,
    description: Int,
    section: FamilySection,
    selected: FamilySection?,
    onSelect: (FamilySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsEntry(
        icon = icon,
        title = stringResource(title),
        description = stringResource(description),
        selected = section == selected,
        onClick = { onSelect(section) },
        modifier = modifier,
    )
}

/**
 * What the detail pane shows on a wide window before anything is chosen.
 *
 * A two-pane layout that opens with one pane empty needs to say what the empty half
 * is for; otherwise it reads as a screen that failed to load.
 */
@Composable
fun EmptyDetailPane(modifier: Modifier = Modifier) {
    val dimens = LocalAppDimens.current
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(dimens.pagePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_detail_empty),
            style = LocalAppTextStyles.current.body,
            color = AppColors.TextSecondary,
        )
    }
}
