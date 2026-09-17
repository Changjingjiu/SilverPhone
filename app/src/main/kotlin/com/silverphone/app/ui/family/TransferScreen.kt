package com.silverphone.app.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.SettingsEntry
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * Moving contacts between phones: the entry point to the file import and export screens.
 *
 * Reading a file and writing one are the same job seen from two ends - one phone
 * hands a ZIP to another - so they share a single entry in the family menu and live
 * together on this screen. They used to be two separate menu rows, which made the
 * menu longer and asked a family member to choose a direction before knowing which
 * one they needed.
 *
 * The wording still says which end is which: importing reads the file this phone
 * receives, exporting writes the file this phone hands over.
 */
@Composable
fun TransferScreen(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current

    FamilyScreen(
        title = stringResource(R.string.transfer_title),
        subtitle = stringResource(R.string.transfer_subtitle),
        onBack = onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier.fillMaxSize(),
    ) {

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.touchGap,
                bottom = dimens.pagePadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Refresh),
                    title = stringResource(R.string.settings_import_file),
                    description = stringResource(R.string.settings_import_file_desc),
                    onClick = onImport,
                )
            }
            item {
                SettingsEntry(
                    icon = rememberVectorPainter(Icons.Filled.Share),
                    title = stringResource(R.string.settings_export),
                    description = stringResource(R.string.settings_export_desc),
                    onClick = onExport,
                )
            }
        }

    }
}
