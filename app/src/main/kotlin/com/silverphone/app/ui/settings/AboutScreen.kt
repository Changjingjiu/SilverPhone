package com.silverphone.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.silverphone.app.R
import com.silverphone.app.platform.openWebPage
import com.silverphone.app.platform.update.ProjectLinks
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.QuietActionButton
import com.silverphone.app.ui.components.SectionCard
import com.silverphone.app.ui.components.StatusCard
import com.silverphone.app.ui.components.StatusTone
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

    FamilyScreen(
        title = stringResource(R.string.about_title),
        subtitle = stringResource(R.string.settings_about_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceRoomy),
        ) {

            // The app and its version, on one line, and the update check as a text
            // action beside it. No card, no filled button: this page is a label, a
            // link and a paragraph, and dressing any of them up as a control made
            // the page look busier than the app itself.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = styles.button,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(
                            R.string.about_version,
                            installedVersionName,
                        ),
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                }
                QuietActionButton(
                    text = stringResource(R.string.about_check_updates),
                    enabled = updateState != UpdateState.Checking,
                    onClick = onCheckForUpdates,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.cardCorner))
                    .clickable(
                        role = Role.Button,
                        onClick = { openLink(ProjectLinks.REPOSITORY) },
                    )
                    .padding(vertical = dimens.cardInnerPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    tint = AppColors.Ink,
                    modifier = Modifier
                        .size(dimens.secondaryGlyph)
                        .clearAndSetSemantics { },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = dimens.cardInnerPadding),
                ) {
                    Text(
                        text = stringResource(R.string.about_github),
                        style = styles.button,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        text = ProjectLinks.REPOSITORY_DISPLAY,
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                }
            }

            // Nothing is shown here until the family member asks for a check: the
            // permanent "this asks GitHub for..." paragraph was a paragraph about a
            // button, on a page that already has the button.
            if (updateState != UpdateState.NotChecked) {
            SectionCard(title = stringResource(R.string.about_updates_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.touchGap)) {
                    when (updateState) {
                        UpdateState.NotChecked -> Unit

                        UpdateState.Checking -> StatusCard(
                            text = stringResource(R.string.about_checking),
                            tone = StatusTone.NEUTRAL,
                        )

                        UpdateState.UpToDate -> StatusCard(
                            text = stringResource(R.string.about_up_to_date),
                            tone = StatusTone.GOOD,
                        )

                        is UpdateState.Available -> {
                            StatusCard(
                                text = stringResource(
                                    R.string.about_update_available,
                                    updateState.version,
                                ),
                                tone = StatusTone.GOOD,
                            )
                            QuietActionButton(
                                text = stringResource(R.string.about_open_release),
                                onClick = { openLink(updateState.releaseUrl) },
                            )
                        }

                        UpdateState.NotPublishedYet -> {
                            StatusCard(
                                text = stringResource(R.string.about_no_release),
                                tone = StatusTone.NEUTRAL,
                            )
                            QuietActionButton(
                                text = stringResource(R.string.about_open_releases_page),
                                onClick = { openLink(ProjectLinks.RELEASES) },
                            )
                        }

                        UpdateState.Failed -> StatusCard(
                            text = stringResource(R.string.about_check_failed),
                            tone = StatusTone.DANGER,
                        )
                    }

                    addressWithoutBrowser?.let { url ->
                        StatusCard(
                            text = stringResource(R.string.about_no_browser, url),
                            tone = StatusTone.DANGER,
                        )
                    }
                }
            }
            }

            SectionCard(title = stringResource(R.string.about_privacy_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSnug)) {
                    PrivacyParagraph(R.string.about_privacy_local)
                    PrivacyParagraph(R.string.about_privacy_tracking)
                    PrivacyParagraph(R.string.about_privacy_network)
                    PrivacyParagraph(R.string.about_privacy_calls)
                    PrivacyParagraph(R.string.about_privacy_export)
                }
            }
        }
    }
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
