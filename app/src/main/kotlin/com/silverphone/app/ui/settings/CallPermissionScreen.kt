package com.silverphone.app.ui.settings

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.silverphone.app.R
import com.silverphone.app.platform.openAppSettingsPage
import com.silverphone.app.ui.components.FilledActionButton
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.SectionCard
import com.silverphone.app.ui.components.StatusCard
import com.silverphone.app.ui.components.StatusTone
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * S11: the permission and usage page.
 *
 * The state shown is read from the platform each time the screen appears rather than
 * from a stored flag. The permission itself is normally requested inline, from the tap
 * that needed it; this page is where a family member can grant it directly, and where
 * the instructions and the call boundary are explained.
 *
 * Each fact is stated once: the two-SIM behaviour is a step in the instructions and
 * not a second section repeating it, and the call-boundary note stands on its own.
 */
@Composable
fun CallPermissionScreen(
    granted: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    FamilyScreen(
        title = stringResource(R.string.settings_permission),
        subtitle = stringResource(R.string.settings_permission_desc),
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

            StatusCard(
                text = stringResource(
                    if (granted) R.string.help_permission_granted else R.string.help_no_permission,
                ),
                tone = if (granted) StatusTone.GOOD else StatusTone.DANGER,
                icon = if (granted) Icons.Filled.Check else Icons.Filled.Warning,
            )

            if (!granted) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.touchGap)) {
                    Text(
                        text = stringResource(R.string.help_permission_denied_hint),
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                    FilledActionButton(
                        text = stringResource(R.string.help_grant_call_permission),
                        icon = Icons.Filled.Call,
                        onClick = onRequestPermission,
                    )
                    FilledActionButton(
                        text = stringResource(R.string.help_open_app_settings),
                        icon = Icons.Filled.Settings,
                        emphasis = false,
                        onClick = { context.openAppSettingsPage() },
                    )
                }
            }

            SectionCard(title = stringResource(R.string.help_usage_title)) {
                Text(
                    text = stringResource(R.string.help_usage_steps),
                    style = styles.body,
                    color = AppColors.TextSecondary,
                )
            }

            SectionCard(title = stringResource(R.string.help_notes_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSnug)) {
                    Text(
                        text = stringResource(R.string.help_sim_note),
                        style = styles.body,
                        color = AppColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.help_call_boundary),
                        style = styles.body,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }
    }
}
