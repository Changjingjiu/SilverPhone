package com.silverphone.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.silverphone.app.ui.theme.AppColors
import com.silverphone.app.ui.theme.LocalAppDimens
import com.silverphone.app.ui.theme.LocalAppTextStyles

/**
 * The one text field.
 *
 * The border is the accessible grey the specification asks for, the fill is the card
 * white so a field reads as a place to write rather than a hole in the page, and the
 * label sits inside rather than floating over the content.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minHeight: Dp? = null,
) {
    val dimens = LocalAppDimens.current
    val styles = LocalAppTextStyles.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        textStyle = styles.body,
        label = label?.let { { Text(it, style = styles.caption) } },
        placeholder = placeholder?.let { { Text(it, style = styles.caption) } },
        supportingText = supportingText,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(dimens.cardCorner),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            disabledTextColor = AppColors.TextSecondary,
            focusedContainerColor = AppColors.Surface,
            unfocusedContainerColor = AppColors.Surface,
            disabledContainerColor = AppColors.Surface,
            focusedBorderColor = AppColors.Ink,
            unfocusedBorderColor = AppColors.Outline,
            disabledBorderColor = AppColors.Hairline,
            errorBorderColor = AppColors.DangerRed,
            focusedLabelColor = AppColors.Ink,
            unfocusedLabelColor = AppColors.TextSecondary,
            disabledLabelColor = AppColors.TextSecondary,
            errorLabelColor = AppColors.DangerRed,
            cursorColor = AppColors.Ink,
            errorCursorColor = AppColors.DangerRed,
            focusedLeadingIconColor = AppColors.Ink,
            unfocusedLeadingIconColor = AppColors.TextSecondary,
            focusedTrailingIconColor = AppColors.Ink,
            unfocusedTrailingIconColor = AppColors.TextSecondary,
            errorTrailingIconColor = AppColors.DangerRed,
            focusedSupportingTextColor = AppColors.TextSecondary,
            unfocusedSupportingTextColor = AppColors.TextSecondary,
            errorSupportingTextColor = AppColors.DangerRed,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                // A minimum, never a fixed height: the label floats above the field's
                // own outline and the supporting text sits below it, so pinning the
                // box to one number pushes the label out of its layout.
                if (minHeight == null) Modifier else Modifier.heightIn(min = minHeight),
            ),
    )
}
