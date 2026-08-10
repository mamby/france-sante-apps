package net.mamby.health.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import net.mamby.health.R
import net.mamby.health.ui.theme.UiTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(value, locale) {
        value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    OutlinedButton(onClick = { pickerVisible = true }, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label)
            Text(formatted)
        }
    }
    if (pickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        val dialogColors = DatePickerDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            tonalElevation = UiTokens.DialogTonalElevation,
            colors = dialogColors,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        pickerVisible = false
                    },
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(
                state = pickerState,
                colors = dialogColors,
                title = { Text(stringResource(R.string.date_picker_title)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(value, locale) {
        value.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }
    OutlinedButton(onClick = { pickerVisible = true }, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label)
            Text(formatted)
        }
    }
    if (pickerVisible) {
        val pickerState = rememberTimePickerState(value.hour, value.minute)
        AlertDialog(
            onDismissRequest = { pickerVisible = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = UiTokens.DialogTonalElevation,
            title = { Text(stringResource(R.string.time_picker_title)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(LocalTime.of(pickerState.hour, pickerState.minute))
                        pickerVisible = false
                    },
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
fun StringListEditor(
    label: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
) {
    var pending by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
        Text(label)
        values.forEach { value ->
            RemovableInputChip(value, onRemove = { onValuesChange(values - value) })
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = pending,
                onValueChange = { pending = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(label) },
            )
            Button(
                onClick = {
                    val next = pending.trim()
                    if (next.isNotEmpty() && values.none { it.equals(next, ignoreCase = true) }) {
                        onValuesChange(values + next)
                        pending = ""
                    }
                },
                enabled = pending.isNotBlank(),
            ) {
                Icon(painterResource(R.drawable.ic_lucide_plus), stringResource(R.string.add_item))
            }
        }
    }
}

@Composable
fun RemovableInputChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeActionLabel = stringResource(R.string.a11y_delete_item, label)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_x),
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.AvatarSize),
            )
        },
        modifier = modifier.semantics {
            onClick(label = removeActionLabel, action = null)
        },
    )
}

@Composable
fun SwitchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
