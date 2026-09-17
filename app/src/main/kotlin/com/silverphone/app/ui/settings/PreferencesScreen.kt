package com.silverphone.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.silverphone.app.ui.components.AppTextField
import com.silverphone.app.ui.components.FamilyScreen
import com.silverphone.app.ui.components.PRESSED_SCALE_GENTLE
import com.silverphone.app.ui.components.PressHaptics
import com.silverphone.app.ui.components.QuietActionButton
import com.silverphone.app.ui.components.SectionCard
import com.silverphone.app.ui.components.pressScale
import com.silverphone.app.ui.components.rememberPressFeedback
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
 *
 * The page is split into two cards rather than one column of controls, so the two
 * questions - which language, which dialling code - are answered one at a time.
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

    FamilyScreen(
        title = stringResource(R.string.preferences_title),
        subtitle = stringResource(R.string.settings_preferences_desc),
        onBack = onCancel,
        backLabel = stringResource(R.string.action_back),
        modifier = modifier.fillMaxSize(),
        actions = {
            QuietActionButton(
                text = stringResource(R.string.preferences_save),
                enabled = !state.saving && !state.countryCodeInvalid,
                onClick = onSave,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceRoomy),
        ) {

            SectionCard(title = stringResource(R.string.preferences_language_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSnug)) {
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
                        modifier = Modifier.padding(top = dimens.spaceTight),
                    )
                }
            }

            SectionCard(title = stringResource(R.string.preferences_country_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.touchGap)) {
                    AppTextField(
                        value = state.countryCode,
                        onValueChange = onCountryCodeChanged,
                        isError = state.countryCodeInvalid,
                        label = stringResource(R.string.preferences_country_custom),
                        placeholder = stringResource(R.string.preferences_country_custom_hint),
                        supportingText = {
                            Text(
                                text = if (state.countryCodeInvalid) {
                                    stringResource(R.string.preferences_country_invalid)
                                } else {
                                    stringResource(
                                        R.string.preferences_country_example,
                                        state.exampleDial,
                                    )
                                },
                                style = styles.caption,
                                color = if (state.countryCodeInvalid) {
                                    AppColors.DangerRed
                                } else {
                                    AppColors.TextSecondary
                                },
                            )
                        },
                        minHeight = dimens.inputHeight,
                    )

                    Text(
                        text = stringResource(R.string.preferences_country_quick),
                        style = styles.caption,
                        color = AppColors.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSnug),
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
                }
            }

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
    }
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
    val shape = RoundedCornerShape(dimens.chipCorner)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE_GENTLE)
    PressHaptics(interactionSource)
    val fill by animateColorAsState(
        targetValue = when {
            press.pressed -> AppColors.SurfacePressed
            selected -> AppColors.InkSoft
            else -> AppColors.Surface
        },
        animationSpec = tween(90),
        label = "choiceFill",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(press.scale)
            .clip(shape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.Ink else AppColors.Hairline,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = AppColors.Ink),
                role = Role.RadioButton,
                onClick = onClick,
            )
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
            .padding(horizontal = dimens.cardInnerPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(dimens.secondaryGlyph),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(dimens.secondaryGlyph)
                        .clip(CircleShape)
                        .background(AppColors.Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = AppColors.Surface,
                        modifier = Modifier
                            .size(20.dp)
                            .clearAndSetSemantics { },
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(dimens.secondaryGlyph)
                        .clip(CircleShape)
                        .border(2.dp, AppColors.Outline, CircleShape),
                )
            }
        }
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
    val shape = RoundedCornerShape(dimens.chipCorner)

    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, pressedScale = PRESSED_SCALE_GENTLE)
    PressHaptics(interactionSource)
    val fill by animateColorAsState(
        targetValue = when {
            // Selected uses the same quiet ink tint as every other selected control;
            // the brand colour lives in the launcher icon, not in the interface.
            selected -> AppColors.InkSoft
            press.pressed -> AppColors.SurfacePressed
            else -> AppColors.Surface
        },
        animationSpec = tween(90),
        label = "codeFill",
    )

    Row(
        modifier = modifier
            .pressScale(press.scale)
            .clip(shape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.Ink else AppColors.Hairline,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = AppColors.Ink),
                role = Role.RadioButton,
                onClick = onClick,
            )
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
