package com.silverphone.app.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.FilledActionButton
import com.silverphone.app.ui.components.SectionCard
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * S09: export every contact to one archive.
 *
 * The page states exactly what goes in and what does not, says plainly that the
 * file is not encrypted, and only offers save and share once the archive has
 * actually been produced. Opening the share sheet is described as opening the
 * share sheet, never as the other person having received anything.
 */
@Composable
fun ExportScreen(
    state: ExportUiState,
    /** The screen supplies the file-name prefix: it is the only part a person reads. */
    onGenerate: (String) -> Unit,
    onSaveTargetChosen: (android.net.Uri?) -> Unit,
    onBuildShareIntent: () -> android.content.Intent?,
    onChooserFor: (android.content.Intent) -> android.content.Intent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> onSaveTargetChosen(uri) }

    FamilyScreen(
        title = stringResource(R.string.export_title),
        subtitle = stringResource(R.string.settings_export_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {

            // What goes in the file, what does not, and who should receive it: three
            // facts that belong together, in one card, above the button that makes it.
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSnug)) {
                    Text(
                        text = stringResource(
                            R.string.export_summary,
                            state.contactCount,
                            state.photoCount,
                        ),
                        style = styles.button,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.export_includes),
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.export_excludes),
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.export_privacy),
                        style = styles.caption,
                        color = AppColors.DangerRed,
                    )
                }
            }

            StatusMessage(state.exportStatus)

            if (state.contactCount == 0) {
                Text(
                    text = stringResource(R.string.export_empty),
                    style = styles.caption,
                    color = AppColors.TextSecondary,
                )
            }

            // The actions follow the archive they act on, at the end of the page,
            // instead of sitting in a bar that covers it.
            val filePrefix = stringResource(R.string.export_file_prefix)
            FilledActionButton(
                text = stringResource(R.string.export_generate),
                icon = Icons.Filled.Refresh,
                enabled = state.canGenerate,
                onClick = { onGenerate(filePrefix) },
            )

            if (state.generating) {
                Text(
                    text = stringResource(R.string.export_generating),
                    style = styles.caption,
                    color = AppColors.TextSecondary,
                )
            }

            val generated = state.generated
            if (generated != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.touchGap)) {
                    FilledActionButton(
                        text = stringResource(R.string.export_save),
                        icon = Icons.Filled.Check,
                        enabled = !state.saving,
                        onClick = { saveLauncher.launch(generated.file.name) },
                        modifier = Modifier.weight(1f),
                    )
                    FilledActionButton(
                        text = stringResource(R.string.export_share),
                        icon = Icons.Filled.Share,
                        enabled = !state.saving,
                        emphasis = false,
                        onClick = {
                            val intent = onBuildShareIntent()
                            if (intent != null) {
                                context.startActivity(onChooserFor(intent))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(dimens.spaceSnug))
        }
    }
}

@Composable
private fun StatusMessage(status: ExportStatus?) {
    if (status == null) return
    val styles = LocalAppTextStyles.current
    val dimens = LocalAppDimens.current

    val text = when (status) {
        ExportStatus.SAVED -> stringResource(R.string.export_saved)
        ExportStatus.SAVE_FAILED -> stringResource(R.string.export_save_failed)
        ExportStatus.SAVE_LEFT_OVER_PARTIAL_FILE -> stringResource(R.string.export_partial_leftover)
        ExportStatus.SHARE_OPENED -> stringResource(R.string.export_share_opened)
        ExportStatus.SAVE_CANCELLED -> stringResource(R.string.export_cancelled)
        ExportStatus.GENERATE_FAILED -> stringResource(R.string.export_failed)
        ExportStatus.NO_CONTACTS -> stringResource(R.string.export_empty)
    }
    val danger = status == ExportStatus.SAVE_FAILED ||
        status == ExportStatus.SAVE_LEFT_OVER_PARTIAL_FILE ||
        status == ExportStatus.GENERATE_FAILED

    Text(
        text = text,
        style = styles.body,
        color = if (danger) AppColors.DangerRed else AppColors.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.touchGap),
    )
}
