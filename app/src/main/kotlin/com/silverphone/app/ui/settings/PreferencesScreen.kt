package com.silverphone.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.silverphone.app.R
import com.silverphone.app.app.AppLanguage
import com.silverphone.app.domain.CountryCode
import com.silverphone.app.ui.components.CancelActionButton
import com.silverphone.app.ui.components.PrimaryActionButton
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

private const val LANGUAGE_SYSTEM = AppLanguage.SYSTEM
private const val LANGUAGE_CHINESE = "zh"
private const val LANGUAGE_ENGLISH = "en"

/** Quick fills for the most common dialling codes; the field takes anything. */
private val QUICK_CODES = listOf("+86", "+1", "+44", "+81", "+852")

/**
 * S12: the two preferences that make this build usable outside one country.
 *
 * The language choice lists its options in their own language, which is the only
 * wording a speaker of that language is guaranteed to recognise. The dialling code
 * is applied when a call is placed rather than when a contact is saved, so changing
 * it never rewrites what the family typed, and the field says what will actually be
 * dialled so the effect is visible before saving.
 */
@Composable
fun PreferencesScreen(
    state: PreferencesUiState,
    onLanguageSelected: (String) -> Unit,
    onCountryCodeChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            Text(
                text = stringResource(R.string.preferences_title),
                style = styles.pageTitle,
                color = AppColors.TextPrimary,
            )

            SectionTitle(stringResource(R.string.preferences_language_title))
            ChoiceRow(
                label = stringResource(R.string.preferences_language_system),
                selected = state.languageTag == LANGUAGE_SYSTEM,
                onClick = { onLanguageSelected(LANGUAGE_SYSTEM) },
            )
            ChoiceRow(
                // Shown in its own language on purpose.
                label = "简体中文",
                selected = state.languageTag == LANGUAGE_CHINESE,
                onClick = { onLanguageSelected(LANGUAGE_CHINESE) },
            )
            ChoiceRow(
                label = "English",
                selected = state.languageTag == LANGUAGE_ENGLISH,
                onClick = { onLanguageSelected(LANGUAGE_ENGLISH) },
            )
            Text(
                text = stringResource(R.string.preferences_language_note),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )

            SectionTitle(stringResource(R.string.preferences_country_title))
            OutlinedTextField(
                value = state.countryCode,
                onValueChange = onCountryCodeChanged,
                singleLine = true,
                isError = state.countryCodeInvalid,
                textStyle = styles.body,
                label = {
                    Text(
                        text = stringResource(R.string.preferences_country_custom),
                        style = styles.caption,
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.preferences_country_custom_hint),
                        style = styles.caption,
                    )
                },
                supportingText = {
                    Text(
                        text = if (state.countryCodeInvalid) {
                            stringResource(R.string.preferences_country_invalid)
                        } else {
                            stringResource(R.string.preferences_country_example, state.exampleDial)
                        },
                        style = styles.caption,
                        color = if (state.countryCodeInvalid) {
                            AppColors.DangerRed
                        } else {
                            AppColors.TextSecondary
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimens.inputHeight),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.touchGap / 2),
            ) {
                QUICK_CODES.forEach { code ->
                    QuickCodeChip(
                        code = code,
                        selected = state.countryCode == code,
                        onClick = { onCountryCodeChanged(code) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ChoiceRow(
                label = stringResource(R.string.preferences_country_none),
                selected = state.countryCode.isEmpty(),
                onClick = { onCountryCodeChanged("") },
            )

            Text(
                text = stringResource(R.string.preferences_country_note),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )
            Text(
                text = stringResource(R.string.preferences_country_short_note),
                style = styles.caption,
                color = AppColors.TextSecondary,
            )

            when (state.outcome) {
                SaveOutcome.SAVED -> Text(
                    text = stringResource(R.string.preferences_saved),
                    style = styles.body,
                    color = AppColors.CallGreen,
                )

                SaveOutcome.FAILED -> Text(
                    text = stringResource(R.string.preferences_save_failed),
                    style = styles.body,
                    color = AppColors.DangerRed,
                )

                SaveOutcome.NONE -> Unit
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.touchGap),
        ) {
            PrimaryActionButton(
                text = stringResource(R.string.preferences_save),
                icon = Icons.Filled.Check,
                enabled = !state.saving && !state.countryCodeInvalid,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
            CancelActionButton(
                text = stringResource(R.string.preferences_cancel),
                icon = Icons.Filled.Clear,
                onClick = onCancel,
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

/** A single choice, exposed as a selected radio option rather than by border alone. */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(AppColors.Surface)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) AppColors.Focus else AppColors.Outline,
                shape = RoundedCornerShape(dimens.cardCorner),
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.RadioButton
                this.selected = selected
                onClick(label = label) {
                    onClick()
                    true
                }
            }
            .heightIn(min = dimens.minTouchTarget)
            .padding(horizontal = dimens.cardInnerPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Clear,
            contentDescription = null,
            tint = if (selected) AppColors.Focus else AppColors.Outline,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            text = label,
            style = styles.body,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(start = dimens.cardInnerPadding),
        )
    }
}

@Composable
private fun QuickCodeChip(
    code: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(if (selected) AppColors.BrandLime else AppColors.Surface)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) AppColors.Ink else AppColors.Outline,
                shape = RoundedCornerShape(dimens.cardCorner),
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = code
                role = Role.RadioButton
                this.selected = selected
                onClick(label = code) {
                    onClick()
                    true
                }
            }
            .heightIn(min = dimens.minTouchTarget),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = code, style = styles.caption, color = AppColors.Ink)
    }
}
