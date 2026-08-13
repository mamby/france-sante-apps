package net.mamby.health.feature.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
import net.mamby.health.feature.ProfileOwned
import net.mamby.health.feature.ownedItems
import net.mamby.health.ui.components.AppEditorScaffold
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.ConfirmDeleteDialog
import net.mamby.health.ui.components.DateField
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.DropdownTrailingIcon
import net.mamby.health.ui.components.EditorFieldPair
import net.mamby.health.ui.components.EditorSection
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.TimeField
import net.mamby.health.ui.components.rememberEditorState
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.format.localizedLabel
import net.mamby.health.ui.format.localizedValue
import net.mamby.health.ui.format.symbol
import net.mamby.health.ui.theme.UiTokens

@Composable
fun MeasurementsScreen(
    records: List<ProfileRecord>,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onManageTypes: (UUID?) -> Unit,
    onAdd: (UUID?) -> Unit,
    onSelected: (UUID, UUID) -> Unit,
) {
    var filterProfileId by remember { mutableStateOf<UUID?>(null) }
    val filteredRecords = filterProfileId?.let { id -> records.filter { it.profile.id == id } } ?: records
    val measurements = remember(filteredRecords) {
        filteredRecords.ownedItems(ProfileRecord::measurements).sortedWith(
            compareByDescending<ProfileOwned<HealthMeasurement>> { it.value.measuredAt }
                .thenBy { it.profileId }
                .thenBy { it.value.id },
        )
    }
    AppScreenScaffold(
        title = stringResource(R.string.measurements_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { onManageTypes(filterProfileId) }) {
                Icon(
                    painterResource(R.drawable.ic_lucide_sliders_horizontal),
                    stringResource(R.string.manage_measurement_types),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAdd(filterProfileId ?: records.singleOrNull()?.profile?.id) },
            ) {
                Icon(painterResource(R.drawable.ic_lucide_plus), stringResource(R.string.add_measurement))
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = padding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileFilterChip(records, filterProfileId, { filterProfileId = it })
            }
            if (measurements.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.no_measurements_title),
                        stringResource(R.string.no_measurements_body),
                    )
                }
            } else {
                items(
                    measurements,
                    key = { "${it.profileId}:${it.value.id}" },
                ) { owned ->
                    val measurement = owned.value
                    SectionCard(measurement.type.localizedLabel(owned.record)) {
                        if (filterProfileId == null && records.size > 1) ProfileMarker(owned.profile)
                        Text(measurement.reading.localizedValue())
                        Text(measurement.measuredAt.localizedDateTime(zoneId))
                        measurement.notes?.let { Text(it) }
                        Button(onClick = { onSelected(owned.profileId, measurement.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MeasurementDetailScreen(
    record: ProfileRecord,
    measurement: HealthMeasurement,
    zoneId: ZoneId,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var deleting by remember(measurement.id) { mutableStateOf(false) }
    AppScreenScaffold(
        title = measurement.type.localizedLabel(record),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .consumeWindowInsets(padding)
                .withPagePadding(),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            PageHeader()
            ProfileOwnerHeader(record.profile)
            SectionCard(stringResource(R.string.measurement_value)) {
                Text(measurement.reading.localizedValue())
                Text(measurement.measuredAt.localizedDateTime(zoneId))
                measurement.notes?.let { Text(it) }
            }
            Button(onClick = onEdit) { Text(stringResource(R.string.common_edit)) }
            OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.common_delete)) }
        }
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
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(
                    painterResource(R.drawable.ic_lucide_plus),
                    stringResource(R.string.add_measurement_type),
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = padding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileOwnerHeader(record.profile)
            }
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

data class MeasurementDraft(
    val profileId: UUID,
    val id: UUID,
    val type: MeasurementTypeRef,
    val value: String,
    val unit: MeasurementUnitRef,
    val systolic: String,
    val diastolic: String,
    val pulse: String,
    val notes: String,
    val date: LocalDate,
    val time: LocalTime,
    val updatedAt: Instant,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementEditorScreen(
    records: List<ProfileRecord>,
    existingOwner: ProfileRecord?,
    existing: HealthMeasurement?,
    initialProfileId: UUID,
    now: Instant,
    zoneId: ZoneId,
    onCancel: () -> Unit,
    onSave: (UUID, HealthMeasurement, (Boolean) -> Unit) -> Unit,
) {
    val initialOwner = existingOwner
        ?: records.firstOrNull { it.profile.id == initialProfileId }
    val editorState = rememberEditorState {
        existing.toDraft(initialProfileId, initialOwner, now, zoneId)
    }
    val draft = editorState.value
    val selectedOwner = existingOwner?.takeIf { it.profile.id == draft.profileId }
        ?: records.firstOrNull { it.profile.id == draft.profileId }
    val formRecord = selectedOwner ?: initialOwner
    val types = remember(formRecord) {
        BuiltInMeasurementType.entries.map { MeasurementTypeRef.BuiltIn(it) } +
            formRecord?.customMeasurementTypes.orEmpty().map { MeasurementTypeRef.Custom(it.id) }
    }
    var typeExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val isBloodPressure = draft.type == MeasurementTypeRef.BuiltIn(BuiltInMeasurementType.BLOOD_PRESSURE)
    val allowedUnits = allowedUnits(draft.type, formRecord)
    val reading = if (isBloodPressure) {
        val systolicValue = parseNumber(draft.systolic, locale)
        val diastolicValue = parseNumber(draft.diastolic, locale)
        val pulseValue = draft.pulse.takeIf(String::isNotBlank)?.let { parseNumber(it, locale) }
        if (
            systolicValue != null && systolicValue > 0 &&
            diastolicValue != null && diastolicValue > 0 &&
            (draft.pulse.isBlank() || pulseValue != null && pulseValue > 0)
        ) {
            MeasurementReading.BloodPressure(
                systolicValue,
                diastolicValue,
                pulseValue,
                draft.unit,
            )
        } else null
    } else {
        parseNumber(draft.value, locale)
            ?.takeIf { candidate ->
                val builtIn = (draft.type as? MeasurementTypeRef.BuiltIn)?.type
                builtIn == null || builtIn == BuiltInMeasurementType.TEMPERATURE || candidate > 0
            }
            ?.let { MeasurementReading.Scalar(it, draft.unit) }
    }

    AppEditorScaffold(
        title = stringResource(if (existing == null) R.string.add_measurement else R.string.edit_measurement),
        isDirty = editorState.isDirty,
        saveEnabled = selectedOwner != null && reading != null,
        isSaving = editorState.isSaving,
        onCancel = onCancel,
        onSave = {
            val validReading = reading ?: return@AppEditorScaffold
            editorState.isSaving = true
            onSave(draft.profileId, draft.toMeasurement(validReading, zoneId)) { saved ->
                editorState.isSaving = false
                if (saved) onCancel()
            }
        },
    ) {
        EditorSection(stringResource(R.string.measurements_title)) {
            EditorFieldPair(
                first = { modifier ->
                    selectedOwner?.let { ProfileOwnerHeader(it.profile, modifier) }
                },
                second = { modifier ->
                    MeasurementTypeField(
                        record = formRecord,
                        types = types,
                        type = draft.type,
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it },
                        onSelected = { candidate ->
                            editorState.value = draft.copy(
                                type = candidate,
                                unit = defaultUnit(candidate, formRecord),
                            )
                            typeExpanded = false
                        },
                        modifier = modifier,
                    )
                },
            )
        }
        EditorSection(stringResource(R.string.measurement_value)) {
            if (isBloodPressure) {
                EditorFieldPair(
                    first = { modifier ->
                        DecimalField(
                            draft.systolic,
                            { editorState.value = draft.copy(systolic = it) },
                            stringResource(R.string.measurement_systolic),
                            modifier,
                        )
                    },
                    second = { modifier ->
                        DecimalField(
                            draft.diastolic,
                            { editorState.value = draft.copy(diastolic = it) },
                            stringResource(R.string.measurement_diastolic),
                            modifier,
                        )
                    },
                )
                EditorFieldPair(
                    first = { modifier ->
                        DecimalField(
                            draft.pulse,
                            { editorState.value = draft.copy(pulse = it) },
                            stringResource(R.string.measurement_optional_pulse),
                            modifier,
                        )
                    },
                    second = { modifier ->
                        OutlinedTextField(
                            value = draft.unit.displaySymbol(),
                            onValueChange = {},
                            modifier = modifier,
                            readOnly = true,
                            label = { Text(stringResource(R.string.measurement_unit)) },
                        )
                    },
                )
            } else {
                EditorFieldPair(
                    first = { modifier ->
                        DecimalField(
                            draft.value,
                            { editorState.value = draft.copy(value = it) },
                            stringResource(R.string.measurement_value),
                            modifier,
                        )
                    },
                    second = { modifier ->
                        ExposedDropdownMenuBox(
                            expanded = unitExpanded,
                            onExpandedChange = { unitExpanded = it },
                            modifier = modifier,
                        ) {
                            OutlinedTextField(
                                value = draft.unit.displaySymbol(),
                                onValueChange = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                readOnly = true,
                                label = { Text(stringResource(R.string.measurement_unit)) },
                                trailingIcon = { DropdownTrailingIcon(unitExpanded) },
                            )
                            ExposedDropdownMenu(unitExpanded, { unitExpanded = false }) {
                                allowedUnits.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text(candidate.displaySymbol()) },
                                        onClick = {
                                            editorState.value = draft.copy(unit = candidate)
                                            unitExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
            EditorFieldPair(
                first = { modifier ->
                    Column(modifier) {
                        DateField(
                            stringResource(R.string.measurement_date),
                            draft.date,
                            { editorState.value = draft.copy(date = it) },
                        )
                    }
                },
                second = { modifier ->
                    Column(modifier) {
                        TimeField(
                            stringResource(R.string.measurement_time),
                            draft.time,
                            { editorState.value = draft.copy(time = it) },
                        )
                    }
                },
            )
        }
        OutlinedTextField(
            value = draft.notes,
            onValueChange = { editorState.value = draft.copy(notes = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.common_notes)) },
            minLines = 2,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementTypeField(
    record: ProfileRecord?,
    types: List<MeasurementTypeRef>,
    type: MeasurementTypeRef,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (MeasurementTypeRef) -> Unit,
    modifier: Modifier,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = type.editorLabel(record),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.measurement_type)) },
            trailingIcon = { DropdownTrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded, { onExpandedChange(false) }) {
            types.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.editorLabel(record)) },
                    onClick = { onSelected(candidate) },
                )
            }
        }
    }
}

private fun HealthMeasurement?.toDraft(
    profileId: UUID,
    record: ProfileRecord?,
    now: Instant,
    zoneId: ZoneId,
): MeasurementDraft {
    val initialType = this?.type ?: MeasurementTypeRef.BuiltIn(BuiltInMeasurementType.WEIGHT)
    val initialInstant = this?.measuredAt ?: now
    return MeasurementDraft(
        profileId = profileId,
        id = this?.id ?: UUID.randomUUID(),
        type = initialType,
        value = (this?.reading as? MeasurementReading.Scalar)?.value?.toString().orEmpty(),
        unit = (this?.reading as? MeasurementReading.Scalar)?.unit ?: defaultUnit(initialType, record),
        systolic = (this?.reading as? MeasurementReading.BloodPressure)?.systolic?.toString().orEmpty(),
        diastolic = (this?.reading as? MeasurementReading.BloodPressure)?.diastolic?.toString().orEmpty(),
        pulse = (this?.reading as? MeasurementReading.BloodPressure)?.pulseBeatsPerMinute?.toString().orEmpty(),
        notes = this?.notes.orEmpty(),
        date = initialInstant.atZone(zoneId).toLocalDate(),
        time = initialInstant.atZone(zoneId).toLocalTime(),
        updatedAt = this?.updatedAt ?: Instant.EPOCH,
    )
}

private fun MeasurementDraft.toMeasurement(
    reading: MeasurementReading,
    zoneId: ZoneId,
): HealthMeasurement = HealthMeasurement(
    id = id,
    type = type,
    reading = reading,
    measuredAt = date.atTime(time).atZone(zoneId).toInstant(),
    notes = notes.trim().ifBlank { null },
    updatedAt = updatedAt,
)

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

private fun defaultUnit(type: MeasurementTypeRef, record: ProfileRecord?): MeasurementUnitRef = when (type) {
    is MeasurementTypeRef.Custom -> MeasurementUnitRef.Custom(
        record?.customMeasurementTypes?.firstOrNull { it.id == type.id }?.suggestedUnit.orEmpty(),
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

private fun allowedUnits(type: MeasurementTypeRef, record: ProfileRecord?): List<MeasurementUnitRef> = when (type) {
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

@Composable
private fun MeasurementTypeRef.editorLabel(record: ProfileRecord?): String = when {
    record != null -> localizedLabel(record)
    this is MeasurementTypeRef.BuiltIn -> stringResource(type.labelResource())
    else -> stringResource(R.string.measurement_custom)
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
