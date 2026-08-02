package net.mamby.health.core.model

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

enum class HealthSearchGroup {
    HEALTH_RECORDS,
    MEDICATIONS,
    APPOINTMENTS,
}

sealed interface HealthSearchTarget {
    data object HealthInfo : HealthSearchTarget

    data class EmergencyContact(val id: UUID) : HealthSearchTarget

    data class Vaccination(val id: UUID) : HealthSearchTarget

    data class Document(val id: UUID) : HealthSearchTarget

    data class Medication(val id: UUID) : HealthSearchTarget

    data class Appointment(val id: UUID) : HealthSearchTarget

    data class Note(val id: UUID) : HealthSearchTarget

    data class Measurement(val id: UUID) : HealthSearchTarget

    data class DirectoryEntry(val id: UUID) : HealthSearchTarget

    data class FamilyHistory(val id: UUID) : HealthSearchTarget

    data class Directive(val id: UUID) : HealthSearchTarget

    data class Identifier(val id: UUID) : HealthSearchTarget
}

data class HealthSearchResult(
    val profileId: UUID,
    val group: HealthSearchGroup,
    val primaryText: String,
    val secondaryText: String? = null,
    val target: HealthSearchTarget,
)

object HealthSearch {
    fun search(record: ProfileRecord, query: String): List<HealthSearchResult> {
        val terms = query.searchTerms()
        if (terms.isEmpty()) return emptyList()
        val profileId = record.profile.id

        return buildList {
            val healthDetails = buildList {
                record.profile.bloodType?.let(::add)
                addAll(record.profile.allergies)
                addAll(record.profile.chronicConditions)
                addAll(record.profile.surgeries)
            }
            if (healthDetails.matches(terms)) {
                add(
                    HealthSearchResult(
                        profileId = profileId,
                        group = HealthSearchGroup.HEALTH_RECORDS,
                        primaryText = record.profile.displayName,
                        secondaryText = healthDetails.joinToString(SEPARATOR).takeIf(String::isNotBlank),
                        target = HealthSearchTarget.HealthInfo,
                    ),
                )
            }
            record.profile.emergencyContacts
                .filter { listOf(it.name, it.relationship, it.phoneNumber, it.notes.orEmpty()).matches(terms) }
                .forEach { contact ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            contact.name,
                            listOf(contact.relationship, contact.phoneNumber)
                                .filter(String::isNotBlank)
                                .joinToString(SEPARATOR),
                            HealthSearchTarget.EmergencyContact(contact.id),
                        ),
                    )
                }
            record.vaccinations
                .filter {
                    listOf(it.name, it.provider.orEmpty(), it.lotNumber.orEmpty()).matches(terms)
                }
                .sortedByDescending(Vaccination::dateAdministered)
                .forEach { vaccination ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            vaccination.name,
                            vaccination.provider,
                            HealthSearchTarget.Vaccination(vaccination.id),
                        ),
                    )
                }
            record.documents
                .filter {
                    listOf(
                        it.title,
                        it.source,
                        it.notes.orEmpty(),
                        it.originalFileName.orEmpty(),
                        it.mimeType,
                        *it.tags.toTypedArray(),
                    ).matches(terms)
                }
                .sortedWith(compareByDescending<MedicalDocument>(MedicalDocument::documentDate).thenBy(MedicalDocument::title))
                .forEach { document ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            document.title,
                            document.source,
                            HealthSearchTarget.Document(document.id),
                        ),
                    )
                }
            record.medications
                .filter { listOf(it.name, it.dose, it.instructions, it.notes.orEmpty()).matches(terms) }
                .sortedWith(compareByDescending<Medication>(Medication::isActive).thenBy(Medication::name))
                .forEach { medication ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.MEDICATIONS,
                            medication.name,
                            medication.dose,
                            HealthSearchTarget.Medication(medication.id),
                        ),
                    )
                }
            record.appointments
                .filter {
                    listOf(it.title, it.clinician, it.location, it.notes.orEmpty()).matches(terms)
                }
                .sortedBy(Appointment::startsAt)
                .forEach { appointment ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.APPOINTMENTS,
                            appointment.title,
                            listOf(appointment.clinician, appointment.location)
                                .filter(String::isNotBlank)
                                .joinToString(SEPARATOR),
                            HealthSearchTarget.Appointment(appointment.id),
                        ),
                    )
                }
            record.notes
                .filter { listOf(it.title, it.body).matches(terms) }
                .sortedByDescending(HealthNote::notedAt)
                .forEach { note ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            note.title,
                            note.body,
                            HealthSearchTarget.Note(note.id),
                        ),
                    )
                }
            record.measurements
                .filter {
                    listOf(it.type.searchableName(record), it.notes.orEmpty()).matches(terms)
                }
                .sortedByDescending(HealthMeasurement::measuredAt)
                .forEach { measurement ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            measurement.type.searchableName(record),
                            measurement.notes,
                            HealthSearchTarget.Measurement(measurement.id),
                        ),
                    )
                }
            record.careDirectory
                .filter { entry ->
                    listOf(
                        entry.name,
                        entry.specialty.orEmpty(),
                        entry.organization.orEmpty(),
                        entry.address.addressLines.joinToString(" "),
                        entry.address.locality.orEmpty(),
                        entry.address.region.orEmpty(),
                        entry.address.postalCode.orEmpty(),
                        entry.address.country.orEmpty(),
                        entry.phoneNumbers.joinToString(" "),
                        entry.emailAddresses.joinToString(" "),
                        entry.notes.orEmpty(),
                    ).matches(terms)
                }
                .sortedBy(CareDirectoryEntry::name)
                .forEach { entry ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            entry.name,
                            listOf(entry.specialty.orEmpty(), entry.organization.orEmpty())
                                .filter(String::isNotBlank)
                                .joinToString(SEPARATOR),
                            HealthSearchTarget.DirectoryEntry(entry.id),
                        ),
                    )
                }
            record.familyHistory
                .filter {
                    listOf(it.relationship, it.condition, it.notes.orEmpty()).matches(terms)
                }
                .sortedBy(FamilyHistoryEntry::relationship)
                .forEach { entry ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            entry.condition,
                            entry.relationship,
                            HealthSearchTarget.FamilyHistory(entry.id),
                        ),
                    )
                }
            record.directives
                .filter { listOf(it.title, it.text).matches(terms) }
                .sortedByDescending(CareDirective::recordedOn)
                .forEach { directive ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            directive.title,
                            directive.text,
                            HealthSearchTarget.Directive(directive.id),
                        ),
                    )
                }
            record.healthIdentifiers
                .filter {
                    // Identifier values are deliberately excluded from the searchable content.
                    listOf(it.label, it.issuer.orEmpty()).matches(terms)
                }
                .sortedBy(HealthIdentifier::label)
                .forEach { identifier ->
                    add(
                        HealthSearchResult(
                            profileId,
                            HealthSearchGroup.HEALTH_RECORDS,
                            identifier.label,
                            identifier.issuer,
                            HealthSearchTarget.Identifier(identifier.id),
                        ),
                    )
                }
        }
    }

    private fun String.searchTerms(): List<String> = normalized()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)

    private fun Iterable<String>.matches(terms: List<String>): Boolean {
        val searchable = joinToString(" ").normalized()
        return terms.all(searchable::contains)
    }

    private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .filterNot { Character.getType(it) in IGNORED_MARK_TYPES }
        .lowercase(Locale.ROOT)

    private const val SEPARATOR = " · "
    private val IGNORED_MARK_TYPES: Set<Int> = setOf(
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
    )
}
