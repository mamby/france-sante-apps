package net.mamby.health.feature.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInMeasurementType
import net.mamby.health.core.model.CustomMeasurementType
import net.mamby.health.core.model.HealthMeasurement
import net.mamby.health.core.model.MeasurementReading
import net.mamby.health.core.model.MeasurementTypeRef
import net.mamby.health.core.model.MeasurementUnit
import net.mamby.health.core.model.MeasurementUnitRef
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.format.localizedValue
import net.mamby.health.ui.format.symbol
import net.mamby.health.ui.theme.UiTokens

@Composable
fun MeasurementsScreen(
    record: ProfileRecord,
    now: Instant,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onManageTypes: () -> Unit,
    onUpsert: (HealthMeasurement) -> Unit,
    onSelected: (UUID) -> Unit,
    creationRequest: Long = 0,
) {
    var adding by remember(record.profile.id) { mutableStateOf(false) }
    LaunchedEffect(creationRequest) { if (creationRequest > 0) adding = true }
    AppScreenScaffold(
        title = stringResource(R.string.measurements_title),
        onBack = onBack,
        profile = record.profile,
        onProfileClick = onProfileClick,
        actions = {
            IconButton(onClick = onManageTypes) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.manage_measurement_types))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_measurement))
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            if (record.measurements.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.no_measurements_title),
                        stringResource(R.string.no_measurements_body),
                    )
                }
            } else {
                items(
                    record.measurements.sortedByDescending(HealthMeasurement::measuredAt),
                    key = HealthMeasurement::id,
                ) { measurement ->
                    SectionCard(measurement.type.localizedLabel(record)) {
                        Text(measurement.reading.localizedValue())
                        Text(measurement.measuredAt.localizedDateTime(zoneId))
                        measurement.notes?.let { Text(it) }
                        Button(onClick = { onSelected(measurement.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        MeasurementDialog(
            record = record,
            existing = null,
            now = now,
            zoneId = zoneId,
            onDismiss = { adding = false },
            onSave = {
                onUpsert(it)
                adding = false
            },
        )
    }
}

@Composable
fun MeasurementDetailScreen(
    record: ProfileRecord,
    measurement: HealthMeasurement,
    now: Instant,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onUpsert: (HealthMeasurement) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(measurement.id) { mutableStateOf(false) }
    var deleting by remember(measurement.id) { mutableStateOf(false) }
    AppScreenScaffold(
        title = measurement.type.localizedLabel(record),
        onBack = onBack,
        profile = record.profile,
        onProfileClick = onProfileClick,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            SectionCard(stringResource(R.string.measurement_value)) {
                Text(measurement.reading.localizedValue())
                Text(measurement.measuredAt.localizedDateTime(zoneId))
                measurement.notes?.let { Text(it) }
            }
            Button(onClick = { editing = true }) { Text(stringResource(R.string.common_edit)) }
            OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.common_delete)) }
        }
    }
    if (editing) {
        MeasurementDialog(
            record = record,
            existing = measurement,
            now = now,
            zoneId = zoneId,
            onDismiss = { editing = false },
            onSave = {
                onUpsert(it)
                editing = false
            },
        )
    }
    if (deleting) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_measurement_title),
            message = stringResource(R.string.delete_measurement_message),
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                onDelete()
            },
        )
    }
}

@Composable
fun ManageMeasurementTypesScreen(
    record: ProfileRecord,
    onBack: () -> Unit,
    onUpsert: (CustomMeasurementType) -> Unit,
    onDelete: (UUID) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomMeasurementType?>(null) }
    AppScreenScaffold(
        title = stringResource(R.string.manage_measurement_types),
        onBack = onBack,
        profile = record.profile,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_measurement_type))
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.withScreenPadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.built_in_measurement_types_body))
            }
            items(record.customMeasurementTypes, key = CustomMeasurementType::id) { type ->
                val usageCount = record.measurements.count {
                    (it.type as? MeasurementTypeRef.Custom)?.id == type.id
                }
                SectionCard(type.name) {
                    Text(type.suggestedUnit)
                    Text(stringResource(R.string.measurement_type_usage, usageCount))
                    Button(onClick = { editing = type }) { Text(stringResource(R.string.common_edit)) }
                    OutlinedButton(
                        onClick = { onDelete(type.id) },
                        enabled = usageCount == 0,
                    ) { Text(stringResource(R.string.common_delete)) }
                }
            }
        }
    }
    if (adding || editing != null) {
        CustomMeasurementTypeDialog(
            existing = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = {
                onUpsert(it)
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun CustomMeasurementTypeDialog(
    existing: CustomMeasurementType?,
    onDismiss: () -> Unit,
    onSave: (CustomMeasurementType) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var unit by remember(existing?.id) { mutableStateOf(existing?.suggestedUnit.orEmpty()) }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_measurement_type else R.string.edit_measurement_type),
        saveEnabled = name.isNotBlank() && unit.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                CustomMeasurementType(
                    id = existing?.id ?: UUID.randomUUID(),
                    name = name.trim(),
                    suggestedUnit = unit.trim(),
                    updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.measurement_type_name)) })
            OutlinedTextField(unit, { unit = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.measurement_type_unit)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementDialog(
    record: ProfileRecord,
    existing: HealthMeasurement?,
    now: Instant,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (HealthMeasurement) -> Unit,
) {
    val types = remember(record) {
        BuiltInMeasurementType.entries.map { MeasurementTypeRef.BuiltIn(it) } +
            record.customMeasurementTypes.map { MeasurementTypeRef.Custom(it.id) }
    }
    val initialType = existing?.type ?: MeasurementTypeRef.BuiltIn(BuiltInMeasurementType.WEIGHT)
    val initialInstant = existing?.measuredAt ?: now
    var type by remember(existing?.id) { mutableStateOf(initialType) }
    var value by remember(existing?.id) {
        mutableStateOf((existing?.reading as? MeasurementReading.Scalar)?.value?.toString().orEmpty())
    }
    var unit by remember(existing?.id) {
        mutableStateOf(
            (existing?.reading as? MeasurementReading.Scalar)?.unit ?: defaultUnit(initialType, record),
        )
    }
    var systolic by remember(existing?.id) {
        mutableStateOf((existing?.reading as? MeasurementReading.BloodPressure)?.systolic?.toString().orEmpty())
    }
    var diastolic by remember(existing?.id) {
        mutableStateOf((existing?.reading as? MeasurementReading.BloodPressure)?.diastolic?.toString().orEmpty())
    }
    var pulse by remember(existing?.id) {
        mutableStateOf((existing?.reading as? MeasurementReading.BloodPressure)?.pulseBeatsPerMinute?.toString().orEmpty())
    }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var date by remember(existing?.id) { mutableStateOf(initialInstant.atZone(zoneId).toLocalDate()) }
    var time by remember(existing?.id) { mutableStateOf(initialInstant.atZone(zoneId).toLocalTime()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val isBloodPressure = type == MeasurementTypeRef.BuiltIn(BuiltInMeasurementType.BLOOD_PRESSURE)
    val allowedUnits = allowedUnits(type, record)
    val reading = if (isBloodPressure) {
        val systolicValue = parseNumber(systolic, locale)
        val diastolicValue = parseNumber(diastolic, locale)
        val pulseValue = pulse.takeIf(String::isNotBlank)?.let { parseNumber(it, locale) }
        if (
            systolicValue != null && systolicValue > 0 &&
            diastolicValue != null && diastolicValue > 0 &&
            (pulse.isBlank() || pulseValue != null && pulseValue > 0)
        ) {
            MeasurementReading.BloodPressure(
                systolicValue,
                diastolicValue,
                pulseValue,
                unit,
            )
        } else null
    } else {
        parseNumber(value, locale)
            ?.takeIf { candidate ->
                val builtIn = (type as? MeasurementTypeRef.BuiltIn)?.type
                builtIn == null || builtIn == BuiltInMeasurementType.TEMPERATURE || candidate > 0
            }
            ?.let { MeasurementReading.Scalar(it, unit) }
    }
    FormDialog(
        title = stringResource(if (existing == null) R.string.add_measurement else R.string.edit_measurement),
        saveEnabled = reading != null,
        onDismiss = onDismiss,
        onSave = {
            reading?.let {
                onSave(
                    HealthMeasurement(
                        id = existing?.id ?: UUID.randomUUID(),
                        type = type,
                        reading = it,
                        measuredAt = date.atTime(time).atZone(zoneId).toInstant(),
                        notes = notes.trim().ifBlank { null },
                        updatedAt = existing?.updatedAt ?: Instant.EPOCH,
                    ),
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            ExposedDropdownMenuBox(typeExpanded, { typeExpanded = it }) {
                OutlinedTextField(
                    value = type.localizedLabel(record),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text(stringResource(R.string.measurement_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                )
                ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) {
                    types.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.localizedLabel(record)) },
                            onClick = {
                                type = candidate
                                unit = defaultUnit(candidate, record)
                                typeExpanded = false
                            },
                        )
                    }
                }
            }
            if (isBloodPressure) {
                DecimalField(systolic, { systolic = it }, stringResource(R.string.measurement_systolic))
                DecimalField(diastolic, { diastolic = it }, stringResource(R.string.measurement_diastolic))
                DecimalField(pulse, { pulse = it }, stringResource(R.string.measurement_optional_pulse))
                OutlinedTextField(
                    value = unit.displaySymbol(),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    label = { Text(stringResource(R.string.measurement_unit)) },
                )
            } else {
                DecimalField(value, { value = it }, stringResource(R.string.measurement_value))
                ExposedDropdownMenuBox(unitExpanded, { unitExpanded = it }) {
                    OutlinedTextField(
                        value = unit.displaySymbol(),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        readOnly = true,
                        label = { Text(stringResource(R.string.measurement_unit)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                    )
                    ExposedDropdownMenu(unitExpanded, { unitExpanded = false }) {
                        allowedUnits.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.displaySymbol()) },
                                onClick = {
                                    unit = candidate
                                    unitExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            DateField(stringResource(R.string.measurement_date), date, { date = it })
            TimeField(stringResource(R.string.measurement_time), time, { time = it })
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_notes)) }, minLines = 2)
        }
    }
}

@Composable
private fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

private fun defaultUnit(type: MeasurementTypeRef, record: ProfileRecord): MeasurementUnitRef = when (type) {
    is MeasurementTypeRef.Custom -> MeasurementUnitRef.Custom(
        record.customMeasurementTypes.firstOrNull { it.id == type.id }?.suggestedUnit.orEmpty(),
    )
    is MeasurementTypeRef.BuiltIn -> MeasurementUnitRef.BuiltIn(
        when (type.type) {
            BuiltInMeasurementType.WEIGHT -> MeasurementUnit.KILOGRAM
            BuiltInMeasurementType.HEIGHT -> MeasurementUnit.CENTIMETER
            BuiltInMeasurementType.BLOOD_PRESSURE -> MeasurementUnit.MILLIMETERS_OF_MERCURY
            BuiltInMeasurementType.PULSE -> MeasurementUnit.BEATS_PER_MINUTE
            BuiltInMeasurementType.TEMPERATURE -> MeasurementUnit.CELSIUS
            BuiltInMeasurementType.OXYGEN_SATURATION -> MeasurementUnit.PERCENT
            BuiltInMeasurementType.BLOOD_GLUCOSE -> MeasurementUnit.MILLIMOLES_PER_LITER
        },
    )
}

private fun allowedUnits(type: MeasurementTypeRef, record: ProfileRecord): List<MeasurementUnitRef> = when (type) {
    is MeasurementTypeRef.Custom -> listOf(defaultUnit(type, record))
    is MeasurementTypeRef.BuiltIn -> when (type.type) {
        BuiltInMeasurementType.WEIGHT -> listOf(MeasurementUnit.KILOGRAM, MeasurementUnit.POUND)
        BuiltInMeasurementType.HEIGHT -> listOf(MeasurementUnit.CENTIMETER, MeasurementUnit.INCH)
        BuiltInMeasurementType.BLOOD_PRESSURE -> listOf(MeasurementUnit.MILLIMETERS_OF_MERCURY)
        BuiltInMeasurementType.PULSE -> listOf(MeasurementUnit.BEATS_PER_MINUTE)
        BuiltInMeasurementType.TEMPERATURE -> listOf(MeasurementUnit.CELSIUS, MeasurementUnit.FAHRENHEIT)
        BuiltInMeasurementType.OXYGEN_SATURATION -> listOf(MeasurementUnit.PERCENT)
        BuiltInMeasurementType.BLOOD_GLUCOSE -> listOf(
            MeasurementUnit.MILLIGRAMS_PER_DECILITER,
            MeasurementUnit.MILLIMOLES_PER_LITER,
        )
    }.map { MeasurementUnitRef.BuiltIn(it) }
}

private fun MeasurementUnitRef.displaySymbol(): String = when (this) {
    is MeasurementUnitRef.BuiltIn -> unit.symbol()
    is MeasurementUnitRef.Custom -> symbol
}

private fun parseNumber(value: String, locale: java.util.Locale): Double? {
    val text = value.trim()
    if (text.isEmpty()) return null
    val position = ParsePosition(0)
    val parsed = NumberFormat.getNumberInstance(locale).parse(text, position)?.toDouble()
    return parsed?.takeIf { position.index == text.length && position.errorIndex < 0 && it.isFinite() }
}
