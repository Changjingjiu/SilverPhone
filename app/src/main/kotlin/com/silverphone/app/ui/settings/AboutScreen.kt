package com.silverphone.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.silverphone.app.R
import com.silverphone.app.platform.openWebPage
import com.silverphone.app.platform.update.ProjectLinks
import com.silverphone.app.ui.components.BackActionButton
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.components.SettingsEntry
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * S13: what is installed, where the project lives, the update check, and what this
 * app does with the family's data.
 *
 * The three things a person opens this page for are answered without leaving it: the
 * version is read from the installed build, the address is shown as text as well as
 * being tappable, and the privacy section states the same promises the project itself
 * makes. Nothing here depends on a network answer except the check the family member
 * asks for, so the page is complete and readable on a phone that has never been
 * online.
 */
@Composable
fun AboutScreen(
    installedVersionName: String,
    installedVersionCode: Int,
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    // The address is already on screen as text, so a phone with no browser needs one
    // sentence of instruction rather than a dead tap.
    var addressWithoutBrowser by remember { mutableStateOf<String?>(null) }
    val openLink: (String) -> Unit = { url ->
        if (!context.openWebPage(url)) addressWithoutBrowser = url
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            Text(
                text = stringResource(R.string.about_title),
                style = styles.pageTitle,
                color = AppColors.TextPrimary,
            )

            Text(
                text = stringResource(
                    R.string.about_version,
                    installedVersionName,
                    installedVersionCode,
                ),
                style = styles.body,
                color = AppColors.TextPrimary,
            )

            SettingsEntry(
                icon = rememberVectorPainter(Icons.Filled.Share),
                title = stringResource(R.string.about_github),
                description = ProjectLinks.REPOSITORY_DISPLAY,
                onClick = { openLink(ProjectLinks.REPOSITORY) },
            )

            SectionTitle(stringResource(R.string.about_updates_title))
            PrimaryActionButton(
                text = stringResource(R.string.about_check_updates),
                icon = Icons.Filled.Refresh,
                enabled = updateState != UpdateState.Checking,
                onClick = onCheckForUpdates,
                modifier = Modifier.fillMaxWidth(),
            )

            when (updateState) {
                UpdateState.NotChecked -> StatusText(
                    text = stringResource(R.string.about_check_hint),
                    color = AppColors.TextSecondary,
                )

                UpdateState.Checking -> StatusText(
                    text = stringResource(R.string.about_checking),
                    color = AppColors.TextSecondary,
                )

                UpdateState.UpToDate -> StatusText(
                    text = stringResource(R.string.about_up_to_date),
                    color = AppColors.CallGreen,
                )

                is UpdateState.Available -> {
                    StatusText(
                        text = stringResource(R.string.about_update_available, updateState.version),
                        color = AppColors.CallGreen,
                    )
                    PrimaryActionButton(
                        text = stringResource(R.string.about_open_release),
                        icon = Icons.Filled.Share,
                        onClick = { openLink(updateState.releaseUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                UpdateState.NotPublishedYet -> {
                    StatusText(
                        text = stringResource(R.string.about_no_release),
                        color = AppColors.TextSecondary,
                    )
                    PrimaryActionButton(
                        text = stringResource(R.string.about_open_releases_page),
                        icon = Icons.Filled.Share,
                        onClick = { openLink(ProjectLinks.RELEASES) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                UpdateState.Failed -> StatusText(
                    text = stringResource(R.string.about_check_failed),
                    color = AppColors.DangerRed,
                )
            }

            addressWithoutBrowser?.let { url ->
                StatusText(
                    text = stringResource(R.string.about_no_browser, url),
                    color = AppColors.DangerRed,
                )
            }

            SectionTitle(stringResource(R.string.about_privacy_title))
            PrivacyParagraph(R.string.about_privacy_local)
            PrivacyParagraph(R.string.about_privacy_tracking)
            PrivacyParagraph(R.string.about_privacy_network)
            PrivacyParagraph(R.string.about_privacy_calls)
            PrivacyParagraph(R.string.about_privacy_export)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.pagePadding),
        ) {
            BackActionButton(
                text = stringResource(R.string.action_back),
                icon = Icons.Filled.Info,
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current
    Text(
        text = text,
        style = styles.contactName,
        color = AppColors.TextPrimary,
        modifier = Modifier.padding(top = dimens.touchGap),
    )
}

/** One line of answer under the check button: plain news, in the colour of its weight. */
@Composable
private fun StatusText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    val styles = LocalAppTextStyles.current
    Text(text = text, style = styles.body, color = color)
}

@Composable
private fun PrivacyParagraph(@StringRes text: Int) {
    val styles = LocalAppTextStyles.current
    Text(
        text = stringResource(text),
        style = styles.body,
        color = AppColors.TextSecondary,
    )
}
