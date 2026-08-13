package net.mamby.health.core.model

import java.net.URI
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
    vaccinations.mapTo(this) { VaultItem(it.id, VaultItemKind.VACCINATION, it.name, it.updatedAt) }
    measurements.mapTo(this) { measurement ->
        VaultItem(
            measurement.id,
            VaultItemKind.MEASUREMENT,
            measurement.type.searchableName(this@index),
            measurement.updatedAt,
        )
    }
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

fun HealthVault.scheduleIndex(): List<VaultItem> = schedules
    .map { VaultItem(it.id, VaultItemKind.SCHEDULE, it.title, it.updatedAt) }
    .sortedByDescending(VaultItem::updatedAt)

fun HealthVault.contactIndex(): List<VaultItem> = contacts
    .map { VaultItem(it.id, VaultItemKind.CONTACT, it.name, it.updatedAt) }
    .sortedByDescending(VaultItem::updatedAt)

fun HealthVault.requireValid(): HealthVault = apply {
    if (version != HealthVault.CURRENT_VERSION) {
        throw UnsupportedVaultVersionException(version)
    }
    requireVault(revision >= 0) { "Vault revision cannot be negative." }
    requireDistinct("profile", profiles.map { it.profile.id })
    requireDistinct("emergency contact", profiles.flatMap { it.profile.emergencyContacts }.map(EmergencyContact::id))
    requireDistinct("document", profiles.flatMap(ProfileRecord::documents).map(MedicalDocument::id))
    requireDistinct("document blob", profiles.flatMap(ProfileRecord::documents).map(MedicalDocument::blobId))
    requireDistinct("medication", profiles.flatMap(ProfileRecord::medications).map(Medication::id))
    requireDistinct("vaccination", profiles.flatMap(ProfileRecord::vaccinations).map(Vaccination::id))
    requireDistinct("note", notes.map(HealthNote::id))
    requireDistinct("schedule", schedules.map(Schedule::id))
    requireDistinct("contact", contacts.map(VaultContact::id))
    requireDistinct("measurement", profiles.flatMap(ProfileRecord::measurements).map(HealthMeasurement::id))
    requireDistinct(
        "custom measurement type",
        profiles.flatMap(ProfileRecord::customMeasurementTypes).map(CustomMeasurementType::id),
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
                addAll(record.vaccinations.map(Vaccination::id))
                addAll(record.measurements.map(HealthMeasurement::id))
                addAll(record.customMeasurementTypes.map(CustomMeasurementType::id))
                addAll(record.familyHistory.map(FamilyHistoryEntry::id))
                addAll(record.directives.map(CareDirective::id))
                addAll(record.healthIdentifiers.map(HealthIdentifier::id))
                addAll(record.customDocumentCategories.map(CustomDocumentCategory::id))
            }
            addAll(notes.map(HealthNote::id))
            addAll(schedules.map(Schedule::id))
            addAll(contacts.map(VaultContact::id))
        },
    )

    notes.forEach { note ->
        requireVault(note.title.isNotBlank()) { "Health note title is required." }
        requireVault(note.body.isNotBlank()) { "Health note body is required." }
    }

    schedules.forEach(::requireSchedule)

    contacts.forEach { contact ->
        requireVault(contact.name.isNotBlank()) { "Contact name is required." }
        requireVault(contact.phoneNumbers.none(String::isBlank)) {
            "Contact phone numbers cannot be blank."
        }
        requireVault(contact.emailAddresses.none(String::isBlank)) {
            "Contact email addresses cannot be blank."
        }
        requireVault(contact.websites.none(String::isBlank)) {
            "Contact websites cannot be blank."
        }
        requireVault(contact.websites.all(::isAbsoluteHttpUrl)) {
            "Contact websites must be absolute HTTP or HTTPS URLs."
        }
        requireVault(contact.addresses.none(String::isBlank)) {
            "Contact addresses cannot be blank."
        }
    }

    profiles.forEach { record ->
        requireVault(record.profile.displayName.isNotBlank()) { "Profile name is required." }
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
        record.documents.forEach { document ->
            requireVault(document.title.isNotBlank()) { "Document title is required." }
            requireVault(document.source.isNotBlank()) { "Document source is required." }
            requireDocumentCategory(record, document.category)
            requireVault(document.sizeBytes >= 0) { "Document size cannot be negative." }
            requireVault(document.mimeType.isNotBlank()) { "Document MIME type is required." }
        }
        record.medications.forEach { medication ->
            val schedule = medication.schedule
            requireVault(schedule.reminderTimes.distinct().size == schedule.reminderTimes.size) {
                "Medication reminder times must be unique."
            }
            requireDateRange(schedule.startsOn, schedule.endsOn, "medication")
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
    fun nextOccurrence(medication: Medication, now: Instant, zoneId: ZoneId): Instant? {
        if (!medication.isActive) return null
        if (
            medication.schedule.recurrence == ReminderRecurrence.WEEKLY &&
            medication.schedule.daysOfWeek.isEmpty() &&
            medication.schedule.startsOn == null
        ) return null
        return medication.schedule.reminderTimes.minOfOrNull { timeOfDay ->
            nextOccurrence(
                recurrence = medication.schedule.recurrence,
                startsOn = medication.schedule.startsOn,
                endsOn = medication.schedule.endsOn,
                timeOfDay = timeOfDay,
                daysOfWeek = medication.schedule.daysOfWeek,
                now = now,
                zoneId = zoneId,
            ) ?: Instant.MAX
        }?.takeUnless { it == Instant.MAX }
    }

    private fun nextOccurrence(
        recurrence: ReminderRecurrence,
        startsOn: LocalDate?,
        endsOn: LocalDate?,
        timeOfDay: LocalTime,
        daysOfWeek: Set<java.time.DayOfWeek>,
        now: Instant,
        zoneId: ZoneId,
    ): Instant? {
        val nowAtZone = now.atZone(zoneId)
        val firstDate = startsOn ?: nowAtZone.toLocalDate()
        val first = atZone(firstDate, timeOfDay, zoneId)
        val candidate = when (recurrence) {
            ReminderRecurrence.NONE -> startsOn?.let { first.takeIf { it.toInstant().isAfter(now) } }
            ReminderRecurrence.DAILY -> nextDaily(first, nowAtZone)
            ReminderRecurrence.WEEKLY -> nextWeekly(daysOfWeek, timeOfDay, first, nowAtZone, zoneId)
            ReminderRecurrence.MONTHLY -> startsOn?.let { nextMonthly(first, nowAtZone, zoneId) }
        } ?: return null

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
        daysOfWeek: Set<java.time.DayOfWeek>,
        timeOfDay: LocalTime,
        first: ZonedDateTime,
        now: ZonedDateTime,
        zoneId: ZoneId,
    ): ZonedDateTime? {
        val activeDays = daysOfWeek.ifEmpty { setOf(first.dayOfWeek) }
        for (offset in 0L..14L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in activeDays) continue
            val candidate = atZone(date, timeOfDay, zoneId)
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

data class ScheduleOccurrence(
    val startsAt: Instant,
    val endsAt: Instant?,
)

object ScheduleCalculator {
    fun nextOccurrence(schedule: Schedule, after: Instant, zoneId: ZoneId): ScheduleOccurrence? =
        when (val timing = schedule.timing) {
            is ScheduleTiming.InstantTimed -> timing.startsAt
                .takeIf { it.isAfter(after) }
                ?.let { ScheduleOccurrence(it, timing.endsAt) }
            is ScheduleTiming.LocalTimed -> nextLocalStart(
                startsOn = timing.startsOn,
                timeOfDay = timing.timeOfDay,
                recurrence = schedule.recurrence,
                after = after,
                zoneId = zoneId,
            )?.let { start ->
                ScheduleOccurrence(
                    startsAt = start.toInstant(),
                    endsAt = timing.durationMinutes?.let { start.plusMinutes(it).toInstant() },
                )
            }
            is ScheduleTiming.AllDay -> nextLocalStart(
                startsOn = timing.startsOn,
                timeOfDay = LocalTime.MIDNIGHT,
                recurrence = schedule.recurrence,
                after = after,
                zoneId = zoneId,
            )?.let { start ->
                val dayCount = timing.endsOn
                    ?.let { java.time.temporal.ChronoUnit.DAYS.between(timing.startsOn, it) + 1 }
                    ?: 1
                ScheduleOccurrence(start.toInstant(), start.plusDays(dayCount).toInstant())
            }
        }

    fun nextAlert(schedule: Schedule, after: Instant, zoneId: ZoneId): Instant? {
        val alert = schedule.alert ?: return null
        if (alert is ScheduleAlert.Timed) {
            val occurrence = nextOccurrence(schedule, after.plusSeconds(alert.minutesBefore * 60), zoneId) ?: return null
            return occurrence.startsAt.minusSeconds(alert.minutesBefore * 60)
        }
        alert as ScheduleAlert.AllDay
        val startOfToday = after.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
        var occurrence = nextOccurrence(schedule, startOfToday.minusNanos(1), zoneId) ?: return null
        repeat(alert.daysBefore + 2) {
            val candidate = occurrence.startsAt
                .atZone(zoneId)
                .toLocalDate()
                .minusDays(alert.daysBefore.toLong())
                .atTime(alert.timeOfDay)
                .atZone(zoneId)
                .toInstant()
            if (candidate.isAfter(after)) return candidate
            occurrence = nextOccurrence(schedule, occurrence.startsAt, zoneId) ?: return null
        }
        return null
    }

    private fun nextLocalStart(
        startsOn: LocalDate,
        timeOfDay: LocalTime,
        recurrence: ScheduleRecurrence,
        after: Instant,
        zoneId: ZoneId,
    ): ZonedDateTime? {
        val afterAtZone = after.atZone(zoneId)
        val first = atScheduleZone(startsOn, timeOfDay, zoneId)
        val candidate = when (recurrence) {
            ScheduleRecurrence.None -> first.takeIf { it.isAfter(afterAtZone) }
            is ScheduleRecurrence.Daily -> {
                if (first.isAfter(afterAtZone)) first else {
                    val date = afterAtZone.toLocalDate().let { current ->
                        if (atScheduleZone(current, timeOfDay, zoneId).isAfter(afterAtZone)) current else current.plusDays(1)
                    }.coerceAtLeast(startsOn)
                    atScheduleZone(date, timeOfDay, zoneId)
                }
            }
            is ScheduleRecurrence.Weekly -> {
                var result: ZonedDateTime? = null
                for (offset in 0L..14L) {
                    val date = afterAtZone.toLocalDate().plusDays(offset)
                    if (date.isBefore(startsOn) || date.dayOfWeek !in recurrence.daysOfWeek) continue
                    val value = atScheduleZone(date, timeOfDay, zoneId)
                    if (value.isAfter(afterAtZone)) {
                        result = value
                        break
                    }
                }
                result
            }
            is ScheduleRecurrence.Monthly -> {
                var month = YearMonth.from(maxOf(startsOn, afterAtZone.toLocalDate()))
                var result: ZonedDateTime? = null
                repeat(3) {
                    val date = month.atDay(recurrence.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
                    val value = atScheduleZone(date, timeOfDay, zoneId)
                    if (!date.isBefore(startsOn) && value.isAfter(afterAtZone)) {
                        result = value
                        return@repeat
                    }
                    month = month.plusMonths(1)
                }
                result
            }
        } ?: return null
        return candidate.takeIf { recurrence.repeatUntil == null || !it.toLocalDate().isAfter(recurrence.repeatUntil) }
    }

    private fun atScheduleZone(date: LocalDate, time: LocalTime, zoneId: ZoneId): ZonedDateTime =
        try {
            ZonedDateTime.of(date, time, zoneId)
        } catch (error: DateTimeException) {
            throw VaultValidationException("Invalid local schedule time: ${error.message}")
        }
}

fun Schedule.normalized(): Schedule {
    val uniquePeople = linkedMapOf<String, String>()
    people.map(String::trim).filter(String::isNotEmpty).forEach { name ->
        uniquePeople.putIfAbsent(name.lowercase(Locale.ROOT), name)
    }
    return copy(
        title = title.trim(),
        people = uniquePeople.values.toList(),
        location = location?.trim()?.takeIf(String::isNotEmpty),
        notes = notes?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun requireSchedule(schedule: Schedule) {
    requireVault(schedule.title.isNotBlank()) { "Schedule title is required." }
    requireVault(schedule.people.all { it.isNotBlank() }) { "Concerned people cannot be blank." }
    requireVault(schedule.people.map { it.lowercase(Locale.ROOT) }.distinct().size == schedule.people.size) {
        "Concerned people must be unique ignoring case."
    }
    when (val timing = schedule.timing) {
        is ScheduleTiming.InstantTimed -> {
            requireVault(timing.endsAt == null || timing.endsAt.isAfter(timing.startsAt)) {
                "A timed schedule must end after it starts."
            }
            requireVault(schedule.recurrence == ScheduleRecurrence.None) {
                "An instant schedule cannot recur."
            }
        }
        is ScheduleTiming.LocalTimed -> requireVault(timing.durationMinutes == null || timing.durationMinutes > 0) {
            "A timed schedule duration must be positive."
        }
        is ScheduleTiming.AllDay -> requireDateRange(timing.startsOn, timing.endsOn, "schedule")
    }
    val startsOn = when (val timing = schedule.timing) {
        is ScheduleTiming.InstantTimed -> null
        is ScheduleTiming.LocalTimed -> timing.startsOn
        is ScheduleTiming.AllDay -> timing.startsOn
    }
    schedule.recurrence.repeatUntil?.let { repeatUntil ->
        requireVault(startsOn == null || !repeatUntil.isBefore(startsOn)) {
            "A schedule repeat-until date cannot precede its start date."
        }
    }
    when (val recurrence = schedule.recurrence) {
        ScheduleRecurrence.None, is ScheduleRecurrence.Daily -> Unit
        is ScheduleRecurrence.Weekly -> requireVault(recurrence.daysOfWeek.isNotEmpty()) {
            "A weekly schedule requires at least one day."
        }
        is ScheduleRecurrence.Monthly -> requireVault(recurrence.dayOfMonth in 1..31) {
            "A monthly schedule day must be between 1 and 31."
        }
    }
    when (val alert = schedule.alert) {
        null -> Unit
        is ScheduleAlert.Timed -> {
            requireVault(schedule.timing !is ScheduleTiming.AllDay) { "An all-day schedule requires an all-day alert." }
            requireVault(alert.minutesBefore >= 0) { "A schedule alert lead cannot be negative." }
        }
        is ScheduleAlert.AllDay -> {
            requireVault(schedule.timing is ScheduleTiming.AllDay) { "A timed schedule requires a timed alert." }
            requireVault(alert.daysBefore in 0..1) { "An all-day alert must be on the day or one day before." }
        }
    }
}

private fun requireDocumentCategory(record: ProfileRecord, reference: DocumentCategoryRef) {
    requireVault(record.isDocumentCategoryAvailable(reference)) {
        "Document references a missing or hidden category."
    }
}

private fun isAbsoluteHttpUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.isAbsolute && uri.host != null &&
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
}.getOrDefault(false)

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
