package com.postpci.drrrp.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.LaunchedEffect

/**
 * Shared, terse building blocks for the staff baseline wizard's ~60 fields — every step composes
 * these rather than hand-rolling a TextField per field. Kept in `ui.common` since the Log Entry
 * bottom sheet (Stage 4) could reasonably reuse the same colors/patterns later.
 *
 * The callback is always the LAST parameter (modifier comes before it, with a default) so every
 * call site below can use trailing-lambda syntax — Kotlin only allows that for the actual last
 * parameter of the function.
 */
@Composable
fun drrrpFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/**
 * Every field below attaches this: without it, the wizard's forms only relied on the enclosing
 * LazyColumn's own scroll to keep a field visible above the keyboard, which never actually
 * happens on its own — Compose doesn't move scroll position just because a field gained focus,
 * so the keyboard could sit directly over whatever field staff had just tapped (worse the lower
 * on the screen it was) with no way to see what they were typing short of manually dragging the
 * list up first. This makes each field bring *itself* into view the instant it's focused, the
 * same guarantee the classic View-system got from `adjustResize` + a focused EditText's own
 * `requestRectangleOnScreen` — Compose has no such automatic behavior, so every field has to ask
 * for it explicitly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isFocused, isImeVisible) {
        if (isFocused) {
            kotlinx.coroutines.delay(100L)
            requester.bringIntoView()
        }
    }

    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent {
            isFocused = it.isFocused
            if (it.isFocused) {
                scope.launch {
                    requester.bringIntoView()
                }
            }
        }
}

@Composable
fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        colors = drrrpFieldColors(),
        modifier = modifier.fillMaxWidth().padding(top = 10.dp).bringIntoViewOnFocus(),
    )
}

@Composable
fun FormPasswordField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = drrrpFieldColors(),
        modifier = modifier.fillMaxWidth().padding(top = 10.dp).bringIntoViewOnFocus(),
    )
}

@Composable
fun FormNumberField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = drrrpFieldColors(),
        modifier = modifier.fillMaxWidth().padding(top = 10.dp).bringIntoViewOnFocus(),
    )
}

@Composable
fun FormDecimalField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = drrrpFieldColors(),
        modifier = modifier.fillMaxWidth().padding(top = 10.dp).bringIntoViewOnFocus(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDateField(label: String, value: LocalDate?, modifier: Modifier = Modifier, onValueChange: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Tap to pick a date") },
        colors = drrrpFieldColors(),
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
    )
    // A transparent clickable overlay would be neater, but a small text trigger avoids fighting
    // the read-only field's own touch handling.
    TextButton(onClick = { showPicker = true }) {
        Text(if (value == null) "Set date" else "Change date", color = AccentYellowGold)
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    onValueChange(millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> FormChipGroup(label: String, options: List<T>, selected: T?, optionLabel: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentYellowGold, selectedLabelColor = Color(0xFF241A00)),
                )
            }
        }
    }
}

@Composable
fun FormToggle(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentYellowGold, checkedTrackColor = AccentYellowGold.copy(alpha = 0.4f)),
        )
    }
}

@Composable
fun FormSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = AccentYellowGold,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
    )
}

@Composable
fun WizardNavButtons(
    showBack: Boolean,
    nextLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(modifier = modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp)) {
        if (showBack) {
            TextButton(onClick = onBack, modifier = Modifier.padding(end = 12.dp)) {
                Text("Back", color = TextSecondary)
            }
        }
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
            modifier = Modifier.weight(1f),
        ) { Text(nextLabel) }
    }
}
