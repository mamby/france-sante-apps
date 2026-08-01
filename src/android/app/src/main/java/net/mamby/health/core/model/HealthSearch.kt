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
