package com.silverphone.app.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.silverphone.app.R
import com.silverphone.app.platform.phone.DialProblem
import com.silverphone.app.ui.components.BackActionButton
import com.silverphone.app.ui.components.MessageState
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors

/**
 * S11: what the elderly user sees when a tap did not reach the system dialer.
 *
 * The copy is short, names one action the family can take, and never claims a
 * call happened. The permission itself is not requested from here: the family
 * turns it on in settings, which is also what the on-screen hint says.
 */
@Composable
fun DialHelpScreen(
    problem: DialProblem,
    onBackHome: () -> Unit,
    onOpenFamilySettings: () -> Unit,
    onAcknowledgeAndReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (problem) {
        DialProblem.PermissionMissing -> Icons.Filled.Call
        DialProblem.NoPhoneApp -> Icons.Filled.Warning
        DialProblem.DispatchFailed -> Icons.Filled.Refresh
        DialProblem.ContactUnavailable -> Icons.Filled.Warning
        DialProblem.StorageUnavailable -> Icons.Filled.Warning
    }
    val title = when (problem) {
        DialProblem.PermissionMissing -> stringResource(R.string.help_no_permission)
        DialProblem.NoPhoneApp -> stringResource(R.string.help_no_phone_app)
        DialProblem.DispatchFailed -> stringResource(R.string.help_dispatch_failed)
        DialProblem.ContactUnavailable -> stringResource(R.string.help_bad_contact)
        DialProblem.StorageUnavailable -> stringResource(R.string.help_storage_unavailable)
    }
    val hint = when (problem) {
        DialProblem.PermissionMissing -> stringResource(R.string.help_no_permission_hint)
        DialProblem.NoPhoneApp -> stringResource(R.string.help_no_phone_app_hint)
        DialProblem.DispatchFailed -> stringResource(R.string.help_dispatch_failed_hint)
        DialProblem.ContactUnavailable -> stringResource(R.string.help_bad_contact_hint)
        DialProblem.StorageUnavailable -> stringResource(R.string.help_storage_unavailable_hint)
    }

    MessageState(
        icon = icon,
        title = title,
        hint = hint,
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background),
        actions = {
            // Only failures that the family can act on offer the settings route.
            if (problem == DialProblem.PermissionMissing ||
                problem == DialProblem.ContactUnavailable
            ) {
                PrimaryActionButton(
                    text = stringResource(R.string.help_family_settings),
                    icon = Icons.Filled.Settings,
                    onClick = {
                        onAcknowledgeAndReturn()
                        onOpenFamilySettings()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BackActionButton(
                text = stringResource(R.string.help_back_home),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBackHome,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
