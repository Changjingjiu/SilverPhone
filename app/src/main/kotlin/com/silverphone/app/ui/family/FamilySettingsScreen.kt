package com.silverphone.app.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.ui.components.BackActionButton
import com.silverphone.app.ui.components.SettingsEntry
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * S03: the family menu.
 *
 * The order is fixed so it is learnable by position. Each entry carries a large
 * glyph, a title and one line of explanation, and the two ways of bringing people
 * in are worded so they can never be confused with each other: one reads the
 * phone's own address book, the other reads a file from another phone.
 */
@Composable
fun FamilySettingsScreen(
    contactCount: Int,
    onManage: () -> Unit,
    onContactsImport: () -> Unit,
    onTransfer: () -> Unit,
    onFont: () -> Unit,
    onPreferences: () -> Unit,
    onPermission: () -> Unit,
    onAbout: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = styles.pageTitle,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.pagePadding,
            ),
        )
        Text(
            text = stringResource(R.string.settings_count, contactCount),
            style = styles.caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = 4.dp,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.touchGap,
                bottom = dimens.pagePadding * 2,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Person),
                    title = stringResource(R.string.settings_manage),
                    description = stringResource(R.string.settings_manage_desc),
                    onClick = onManage,
                )
            }
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Add),
                    title = stringResource(R.string.settings_import_contacts),
                    description = stringResource(R.string.settings_import_contacts_desc),
                    onClick = onContactsImport,
                )
            }
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Refresh),
                    title = stringResource(R.string.settings_transfer),
                    description = stringResource(R.string.settings_transfer_desc),
                    onClick = onTransfer,
                )
            }
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Edit),
                    title = stringResource(R.string.settings_font),
                    description = stringResource(R.string.settings_font_desc),
                    onClick = onFont,
                )
            }
            item {
                SettingsEntry(
                    icon = painterResource(R.drawable.ic_language),
                    title = stringResource(R.string.settings_preferences),
                    description = stringResource(R.string.settings_preferences_desc),
                    onClick = onPreferences,
                )
            }
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Call),
                    title = stringResource(R.string.settings_permission),
                    description = stringResource(R.string.settings_permission_desc),
                    onClick = onPermission,
                )
            }
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Info),
                    title = stringResource(R.string.settings_about),
                    description = stringResource(R.string.settings_about_desc),
                    onClick = onAbout,
                )
            }
            item {
                // A little more air before the way out than between the entries.
                Spacer(Modifier.height(dimens.pagePadding))
                BackActionButton(
                    text = stringResource(R.string.settings_back_home),
                    icon = Icons.Filled.Info,
                    onClick = onBackHome,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
