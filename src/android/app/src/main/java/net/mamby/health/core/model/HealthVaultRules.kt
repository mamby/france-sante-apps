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
}.sortedByDescending(VaultItem::updatedAt)

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
            }
        },
    )

    profiles.forEach { record ->
        requireVault(record.profile.displayName.isNotBlank()) { "Profile name is required." }
        record.documents.forEach { document ->
            requireVault(document.category != DocumentCategory.ALL) {
                "ALL is a filter and cannot be stored as a document category."
            }
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
        val documentIds = record.documents.mapTo(mutableSetOf(), MedicalDocument::id)
        record.appointments.forEach { appointment ->
            requireVault(appointment.reminderLeadMinutes == null || appointment.reminderLeadMinutes >= 0) {
                "Appointment reminder lead cannot be negative."
            }
            requireVault(appointment.relatedDocumentIds.all(documentIds::contains)) {
                "Appointment references a document outside its profile."
            }
        }
        record.reminders.forEach { reminder ->
            requireDateRange(reminder.startsOn, reminder.endsOn, "reminder")
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
        category: DocumentCategory = DocumentCategory.ALL,
    ): List<MedicalDocument> {
        val terms = query.trim()
            .lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)

        return documents.asSequence()
            .filter { category == DocumentCategory.ALL || it.category == category }
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
