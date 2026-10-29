package com.example.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Input colours, derived from the active palette so light and dark stay consistent. */
internal data class InputFieldPalette(
    val text: Color, val container: Color, val cursor: Color, val label: Color,
    val placeholder: Color, val icon: Color, val outline: Color,
    val disabledText: Color, val disabledLabel: Color, val errorText: Color, val errorLabel: Color
)

internal fun inputFieldPalette(dark: Boolean): InputFieldPalette {
    val p = simPaletteFor(dark)
    return InputFieldPalette(
        text = p.ink,
        container = p.card,
        cursor = p.accent,
        label = p.muted,
        placeholder = p.muted,
        icon = p.muted,
        outline = p.muted,
        disabledText = p.muted,
        disabledLabel = p.muted,
        errorText = p.ink,
        errorLabel = p.danger
    )
}

@Composable
fun simOutlinedTextFieldColors(): TextFieldColors {
    val p = inputFieldPalette(LocalSimDark.current)
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = p.text, unfocusedTextColor = p.text,
        disabledTextColor = p.disabledText, errorTextColor = p.errorText,
        focusedContainerColor = p.container, unfocusedContainerColor = p.container,
        disabledContainerColor = p.container, errorContainerColor = p.container,
        cursorColor = p.cursor, errorCursorColor = p.errorLabel,
        focusedLabelColor = p.label, unfocusedLabelColor = p.label,
        disabledLabelColor = p.disabledLabel, errorLabelColor = p.errorLabel,
        focusedPlaceholderColor = p.placeholder, unfocusedPlaceholderColor = p.placeholder,
        disabledPlaceholderColor = p.disabledLabel, errorPlaceholderColor = p.placeholder,
        focusedLeadingIconColor = p.icon, unfocusedLeadingIconColor = p.icon,
        disabledLeadingIconColor = p.disabledLabel, errorLeadingIconColor = p.errorLabel,
        focusedTrailingIconColor = p.icon, unfocusedTrailingIconColor = p.icon,
        disabledTrailingIconColor = p.disabledLabel, errorTrailingIconColor = p.errorLabel,
        focusedBorderColor = p.outline, unfocusedBorderColor = p.outline,
        disabledBorderColor = p.outline, errorBorderColor = p.errorLabel,
        focusedSupportingTextColor = p.label, unfocusedSupportingTextColor = p.label,
        disabledSupportingTextColor = p.disabledLabel, errorSupportingTextColor = p.errorLabel
    )
}
