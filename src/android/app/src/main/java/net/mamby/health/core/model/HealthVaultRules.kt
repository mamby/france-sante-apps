package net.mamby.health.core.model

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

fun ProfileRecord.summary(): HealthSummary = HealthSummary(
    bloodType = profile.bloodType,
    allergies = profile.allergies,
    chronicConditions = profile.chronicConditions,
    surgeries = profile.surgeries,
    emergencyContacts = profile.emergencyContacts,
    lastUpdatedAt = profile.lastUpdatedAt,
)

fun ProfileRecord.index(): List<VaultItem> = buildList {
    documents.mapTo(this) { VaultItem(it.id, VaultItemKind.DOCUMENT, it.title, it.updatedAt) }
    medications.mapTo(this) { VaultItem(it.id, VaultItemKind.MEDICATION, it.name, it.updatedAt) }
    appointments.mapTo(this) { VaultItem(it.id, VaultItemKind.APPOINTMENT, it.title, it.updatedAt) }
    vaccinations.mapTo(this) { VaultItem(it.id, VaultItemKind.VACCINATION, it.name, it.updatedAt) }
    reminders.mapTo(this) { VaultItem(it.id, VaultItemKind.REMINDER, it.title, it.updatedAt) }
    notes.mapTo(this) { VaultItem(it.id, VaultItemKind.NOTE, it.title, it.updatedAt) }
    measurements.mapTo(this) { measurement ->
        VaultItem(
            measurement.id,
            VaultItemKind.MEASUREMENT,
            measurement.type.searchableName(this@index),
            measurement.updatedAt,
        )
    }
    careDirectory.mapTo(this) { VaultItem(it.id, VaultItemKind.DIRECTORY_ENTRY, it.name, it.updatedAt) }
    familyHistory.mapTo(this) { VaultItem(it.id, VaultItemKind.FAMILY_HISTORY, it.condition, it.updatedAt) }
    directives.mapTo(this) { VaultItem(it.id, VaultItemKind.DIRECTIVE, it.title, it.updatedAt) }
    healthIdentifiers.mapTo(this) { VaultItem(it.id, VaultItemKind.IDENTIFIER, it.label, it.updatedAt) }
}.sortedByDescending(VaultItem::updatedAt)

fun ProfileRecord.documentCategoryPreference(
    category: BuiltInDocumentCategory,
): BuiltInDocumentCategoryPreference? =
    builtInDocumentCategoryPreferences.firstOrNull { it.category == category }

fun ProfileRecord.isDocumentCategoryAvailable(reference: DocumentCategoryRef): Boolean = when (reference) {
    is DocumentCategoryRef.BuiltIn -> documentCategoryPreference(reference.category)?.isHidden != true
    is DocumentCategoryRef.Custom -> customDocumentCategories.any { it.id == reference.id }
}

fun MeasurementTypeRef.searchableName(record: ProfileRecord): String = when (this) {
    is MeasurementTypeRef.BuiltIn -> type.name.replace('_', ' ')
    is MeasurementTypeRef.Custom -> record.customMeasurementTypes
        .firstOrNull { it.id == id }
        ?.name
        .orEmpty()
}

fun HealthVault.profileRecord(profileId: java.util.UUID): ProfileRecord =
    profiles.singleOrNull { it.profile.id == profileId }
        ?: throw NoSuchElementException("Profile not found: $profileId")

fun HealthVault.allDocuments(): List<MedicalDocument> = profiles.flatMap(ProfileRecord::documents)

fun HealthVault.requireValid(): HealthVault = apply {
    if (version != HealthVault.CURRENT_VERSION) {
        throw UnsupportedVaultVersionException(version)
    }
    requireVault(revision >= 0) { "Vault revision cannot be negative." }
    requireVault(profiles.isNotEmpty()) { "A vault must contain at least one profile." }
    requireDistinct("profile", profiles.map { it.profile.id })
    requireDistinct("emergency contact", profiles.flatMap { it.profile.emergencyContacts }.map(EmergencyContact::id))
    requireDistinct("document", profiles.flatMap(ProfileRecord::documents).map(MedicalDocument::id))
    requireDistinct("document blob", profiles.flatMap(ProfileRecord::documents).map(MedicalDocument::blobId))
    requireDistinct("medication", profiles.flatMap(ProfileRecord::medications).map(Medication::id))
    requireDistinct("appointment", profiles.flatMap(ProfileRecord::appointments).map(Appointment::id))
    requireDistinct("vaccination", profiles.flatMap(ProfileRecord::vaccinations).map(Vaccination::id))
    requireDistinct("reminder", profiles.flatMap(ProfileRecord::reminders).map(Reminder::id))
    requireDistinct("note", profiles.flatMap(ProfileRecord::notes).map(HealthNote::id))
    requireDistinct("measurement", profiles.flatMap(ProfileRecord::measurements).map(HealthMeasurement::id))
    requireDistinct(
        "custom measurement type",
        profiles.flatMap(ProfileRecord::customMeasurementTypes).map(CustomMeasurementType::id),
    )
    requireDistinct(
        "directory entry",
        profiles.flatMap(ProfileRecord::careDirectory).map(CareDirectoryEntry::id),
    )
    requireDistinct(
        "family history entry",
        profiles.flatMap(ProfileRecord::familyHistory).map(FamilyHistoryEntry::id),
    )
    requireDistinct("directive", profiles.flatMap(ProfileRecord::directives).map(CareDirective::id))
    requireDistinct(
        "health identifier",
        profiles.flatMap(ProfileRecord::healthIdentifiers).map(HealthIdentifier::id),
    )
    requireDistinct(
        "custom document category",
        profiles.flatMap(ProfileRecord::customDocumentCategories).map(CustomDocumentCategory::id),
    )
    requireDistinct(
        "vault object",
        buildList {
            profiles.forEach { record ->
                add(record.profile.id)
                addAll(record.profile.emergencyContacts.map(EmergencyContact::id))
                addAll(record.documents.map(MedicalDocument::id))
                addAll(record.documents.map(MedicalDocument::blobId))
                addAll(record.medications.map(Medication::id))
                addAll(record.appointments.map(Appointment::id))
                addAll(record.vaccinations.map(Vaccination::id))
                addAll(record.reminders.map(Reminder::id))
                addAll(record.notes.map(HealthNote::id))
                addAll(record.measurements.map(HealthMeasurement::id))
                addAll(record.customMeasurementTypes.map(CustomMeasurementType::id))
                addAll(record.careDirectory.map(CareDirectoryEntry::id))
                addAll(record.familyHistory.map(FamilyHistoryEntry::id))
                addAll(record.directives.map(CareDirective::id))
                addAll(record.healthIdentifiers.map(HealthIdentifier::id))
                addAll(record.customDocumentCategories.map(CustomDocumentCategory::id))
            }
        },
    )

    profiles.forEach { record ->
        requireVault(record.profile.displayName.isNotBlank()) { "Profile name is required." }
        val directoryById = record.careDirectory.associateBy(CareDirectoryEntry::id)
        val documentIds = record.documents.mapTo(mutableSetOf(), MedicalDocument::id)
        requireDistinct(
            "built-in document category preference",
            record.builtInDocumentCategoryPreferences.map(BuiltInDocumentCategoryPreference::category),
        )
        record.builtInDocumentCategoryPreferences.forEach { preference ->
            requireVault(preference.labelOverride == null || preference.labelOverride.isNotBlank()) {
                "A document category label override cannot be blank."
            }
        }
        record.customDocumentCategories.forEach { category ->
            requireVault(category.name.isNotBlank()) { "Custom document category name is required." }
        }
        record.customMeasurementTypes.forEach { type ->
            requireVault(type.name.isNotBlank()) { "Custom measurement type name is required." }
            requireVault(type.suggestedUnit.isNotBlank()) { "Custom measurement unit is required." }
        }
        record.careDirectory.forEach { entry ->
            requireVault(entry.name.isNotBlank()) { "Directory entry name is required." }
            requireVault(entry.address.addressLines.none(String::isBlank)) {
                "Directory address lines cannot be blank."
            }
            requireVault(entry.phoneNumbers.none(String::isBlank)) {
                "Directory phone numbers cannot be blank."
            }
            requireVault(entry.emailAddresses.none(String::isBlank)) {
                "Directory email addresses cannot be blank."
            }
        }
        record.profile.primaryDoctorEntryId?.let { primaryDoctorId ->
            requireVault(directoryById[primaryDoctorId]?.kind == CareDirectoryKind.DOCTOR) {
                "The primary doctor must reference a doctor in the same profile."
            }
        }
        record.documents.forEach { document ->
            requireVault(document.title.isNotBlank()) { "Document title is required." }
            requireVault(document.source.isNotBlank()) { "Document source is required." }
            requireDocumentCategory(record, document.category)
            requireVault(document.sizeBytes >= 0) { "Document size cannot be negative." }
            requireVault(document.mimeType.isNotBlank()) { "Document MIME type is required." }
            requireDirectoryReference(directoryById, document.sourceEntryId, "document source")
        }
        record.medications.forEach { medication ->
            val schedule = medication.schedule
            requireVault(schedule.reminderTimes.distinct().size == schedule.reminderTimes.size) {
                "Medication reminder times must be unique."
            }
            requireDateRange(schedule.startsOn, schedule.endsOn, "medication")
            requireDirectoryReference(directoryById, medication.prescriberEntryId, "medication prescriber")
            requireDirectoryReference(directoryById, medication.pharmacyEntryId, "medication pharmacy")
        }
        record.appointments.forEach { appointment ->
            requireVault(appointment.reminderLeadMinutes == null || appointment.reminderLeadMinutes >= 0) {
                "Appointment reminder lead cannot be negative."
            }
            requireVault(appointment.relatedDocumentIds.all(documentIds::contains)) {
                "Appointment references a document outside its profile."
            }
            requireDirectoryReference(directoryById, appointment.clinicianEntryId, "appointment clinician")
            requireDirectoryReference(directoryById, appointment.facilityEntryId, "appointment facility")
        }
        record.vaccinations.forEach { vaccination ->
            requireDirectoryReference(directoryById, vaccination.providerEntryId, "vaccination provider")
        }
        record.reminders.forEach { reminder ->
            requireDateRange(reminder.startsOn, reminder.endsOn, "reminder")
        }
        record.notes.forEach { note ->
            requireVault(note.title.isNotBlank()) { "Health note title is required." }
            requireVault(note.body.isNotBlank()) { "Health note body is required." }
        }
        record.measurements.forEach { measurement -> requireMeasurement(record, measurement) }
        record.familyHistory.forEach { entry ->
            requireVault(entry.relationship.isNotBlank()) { "Family relationship is required." }
            requireVault(entry.condition.isNotBlank()) { "Family condition is required." }
            requireVault(entry.ageAtOnsetYears == null || entry.ageAtOnsetYears >= 0) {
                "Family history age at onset cannot be negative."
            }
        }
        record.directives.forEach { directive ->
            requireVault(directive.title.isNotBlank()) { "Directive title is required." }
            requireVault(directive.text.isNotBlank()) { "Directive text is required." }
            requireVault(directive.relatedDocumentIds.distinct().size == directive.relatedDocumentIds.size) {
                "Directive document references must be unique."
            }
            requireVault(directive.relatedDocumentIds.all(documentIds::contains)) {
                "Directive references a document outside its profile."
            }
        }
        record.healthIdentifiers.forEach { identifier ->
            requireVault(identifier.label.isNotBlank()) { "Health identifier label is required." }
            requireVault(identifier.value.isNotBlank()) { "Health identifier value is required." }
        }
    }
}

class VaultValidationException(message: String) : IllegalArgumentException(message)

class UnsupportedVaultVersionException(val foundVersion: Int) :
    IllegalArgumentException("Unsupported vault version: $foundVersion")

object DocumentSearch {
    fun search(
        documents: List<MedicalDocument>,
        query: String = "",
        category: DocumentCategoryRef? = null,
    ): List<MedicalDocument> {
        val terms = query.trim()
            .lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)

        return documents.asSequence()
            .filter { category == null || it.category == category }
            .filter { document ->
                if (terms.isEmpty()) return@filter true
                val searchable = buildString {
                    append(document.title).append(' ')
                    append(document.source).append(' ')
                    append(document.notes.orEmpty()).append(' ')
                    append(document.originalFileName.orEmpty()).append(' ')
                    append(document.mimeType).append(' ')
                    append(document.tags.joinToString(" "))
                }.lowercase(Locale.ROOT)
                terms.all(searchable::contains)
            }
            .sortedWith(compareByDescending<MedicalDocument>(MedicalDocument::documentDate).thenBy(MedicalDocument::title))
            .toList()
    }
}

object RecurrenceCalculator {
    fun nextOccurrence(reminder: Reminder, now: Instant, zoneId: ZoneId): Instant? {
        if (!reminder.isEnabled) return null

        val first = atZone(reminder.startsOn, reminder.timeOfDay, zoneId)
        val nowAtZone = now.atZone(zoneId)
        val candidate = when (reminder.recurrence) {
            ReminderRecurrence.NONE -> first.takeIf { it.toInstant().isAfter(now) }
            ReminderRecurrence.DAILY -> nextDaily(first, nowAtZone)
            ReminderRecurrence.WEEKLY -> nextWeekly(reminder, first, nowAtZone, zoneId)
            ReminderRecurrence.MONTHLY -> nextMonthly(first, nowAtZone, zoneId)
        } ?: return null

        val endsOn = reminder.endsOn
        return candidate.takeIf { endsOn == null || !it.toLocalDate().isAfter(endsOn) }?.toInstant()
    }

    private fun nextDaily(first: ZonedDateTime, now: ZonedDateTime): ZonedDateTime {
        if (first.isAfter(now)) return first
        var candidateDate = now.toLocalDate()
        var candidate = atZone(candidateDate, first.toLocalTime(), first.zone)
        if (!candidate.isAfter(now)) {
            candidateDate = candidateDate.plusDays(1)
            candidate = atZone(candidateDate, first.toLocalTime(), first.zone)
        }
        return candidate
    }

    private fun nextWeekly(
        reminder: Reminder,
        first: ZonedDateTime,
        now: ZonedDateTime,
        zoneId: ZoneId,
    ): ZonedDateTime? {
        val activeDays = reminder.daysOfWeek.ifEmpty { setOf(first.dayOfWeek) }
        for (offset in 0L..14L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in activeDays) continue
            val candidate = atZone(date, reminder.timeOfDay, zoneId)
            if (!candidate.isBefore(first) && candidate.isAfter(now)) return candidate
        }
        return null
    }

    private fun nextMonthly(
        first: ZonedDateTime,
        now: ZonedDateTime,
        zoneId: ZoneId,
    ): ZonedDateTime {
        if (first.isAfter(now)) return first
        val startMonth = YearMonth.from(first)
        val nowMonth = YearMonth.from(now)
        var elapsedMonths = startMonth.until(nowMonth, java.time.temporal.ChronoUnit.MONTHS)
        var candidate = monthlyCandidate(first, elapsedMonths, zoneId)
        if (!candidate.isAfter(now)) candidate = monthlyCandidate(first, ++elapsedMonths, zoneId)
        return candidate
    }

    private fun monthlyCandidate(first: ZonedDateTime, months: Long, zoneId: ZoneId): ZonedDateTime {
        val month = YearMonth.from(first).plusMonths(months)
        val date = month.atDay(first.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
        return atZone(date, first.toLocalTime(), zoneId)
    }

    private fun atZone(date: LocalDate, time: LocalTime, zoneId: ZoneId): ZonedDateTime =
        try {
            ZonedDateTime.of(date, time, zoneId)
        } catch (error: DateTimeException) {
            throw VaultValidationException("Invalid local reminder time: ${error.message}")
        }
}

private fun requireDocumentCategory(record: ProfileRecord, reference: DocumentCategoryRef) {
    requireVault(record.isDocumentCategoryAvailable(reference)) {
        "Document references a missing or hidden category."
    }
}

private fun requireDirectoryReference(
    entries: Map<java.util.UUID, CareDirectoryEntry>,
    id: java.util.UUID?,
    label: String,
) {
    requireVault(id == null || id in entries) { "The $label must belong to the same profile." }
}

private fun requireMeasurement(record: ProfileRecord, measurement: HealthMeasurement) {
    when (val type = measurement.type) {
        is MeasurementTypeRef.BuiltIn -> requireBuiltInMeasurement(type.type, measurement.reading)
        is MeasurementTypeRef.Custom -> {
            requireVault(record.customMeasurementTypes.any { it.id == type.id }) {
                "Measurement references a missing custom type."
            }
            val reading = measurement.reading as? MeasurementReading.Scalar
            requireVault(reading != null) { "Custom measurements must contain a scalar reading." }
            requireVault(reading!!.value.isFinite()) { "Measurement value must be finite." }
            val unit = reading.unit as? MeasurementUnitRef.Custom
            requireVault(unit?.symbol?.isNotBlank() == true) {
                "Custom measurement unit is required."
            }
        }
    }
}

private fun requireBuiltInMeasurement(
    type: BuiltInMeasurementType,
    reading: MeasurementReading,
) {
    if (type == BuiltInMeasurementType.BLOOD_PRESSURE) {
        val pressure = reading as? MeasurementReading.BloodPressure
        requireVault(pressure != null) { "Blood pressure requires systolic and diastolic values." }
        requireVault(pressure!!.systolic.isFinite() && pressure.systolic > 0) {
            "Systolic pressure must be finite and positive."
        }
        requireVault(pressure.diastolic.isFinite() && pressure.diastolic > 0) {
            "Diastolic pressure must be finite and positive."
        }
        requireVault(
            pressure.pulseBeatsPerMinute == null ||
                pressure.pulseBeatsPerMinute.isFinite() && pressure.pulseBeatsPerMinute > 0,
        ) { "Blood pressure pulse must be finite and positive." }
        requireVault(
            pressure.unit == MeasurementUnitRef.BuiltIn(MeasurementUnit.MILLIMETERS_OF_MERCURY),
        ) { "Blood pressure must use millimeters of mercury." }
        return
    }

    val scalar = reading as? MeasurementReading.Scalar
    requireVault(scalar != null) { "Built-in measurement requires a scalar reading." }
    requireVault(scalar!!.value.isFinite()) { "Measurement value must be finite." }
    if (type != BuiltInMeasurementType.TEMPERATURE) {
        requireVault(scalar.value > 0) { "Measurement value must be positive." }
    }
    val unit = (scalar.unit as? MeasurementUnitRef.BuiltIn)?.unit
    val allowedUnits = when (type) {
        BuiltInMeasurementType.WEIGHT -> setOf(MeasurementUnit.KILOGRAM, MeasurementUnit.POUND)
        BuiltInMeasurementType.HEIGHT -> setOf(MeasurementUnit.CENTIMETER, MeasurementUnit.INCH)
        BuiltInMeasurementType.PULSE -> setOf(MeasurementUnit.BEATS_PER_MINUTE)
        BuiltInMeasurementType.TEMPERATURE -> setOf(MeasurementUnit.CELSIUS, MeasurementUnit.FAHRENHEIT)
        BuiltInMeasurementType.OXYGEN_SATURATION -> setOf(MeasurementUnit.PERCENT)
        BuiltInMeasurementType.BLOOD_GLUCOSE -> setOf(
            MeasurementUnit.MILLIGRAMS_PER_DECILITER,
            MeasurementUnit.MILLIMOLES_PER_LITER,
        )
        BuiltInMeasurementType.BLOOD_PRESSURE -> emptySet()
    }
    requireVault(unit in allowedUnits) { "Measurement unit does not match its type." }
}

private fun requireDistinct(label: String, ids: List<*>) {
    requireVault(ids.distinct().size == ids.size) { "Duplicate $label identifier." }
}

private fun requireDateRange(startsOn: LocalDate?, endsOn: LocalDate?, label: String) {
    requireVault(startsOn == null || endsOn == null || !endsOn.isBefore(startsOn)) {
        "The $label end date cannot be before its start date."
    }
}

private inline fun requireVault(condition: Boolean, message: () -> String) {
    if (!condition) throw VaultValidationException(message())
}
