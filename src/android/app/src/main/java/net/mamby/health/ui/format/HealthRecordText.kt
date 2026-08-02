package net.mamby.health.ui.format

import androidx.annotation.StringRes
import net.mamby.health.R
import net.mamby.health.core.model.CareDirectiveKind
import net.mamby.health.core.model.CareDirectoryKind
import net.mamby.health.core.model.HealthIdentifierKind

@StringRes
fun CareDirectoryKind.labelResource(): Int = when (this) {
    CareDirectoryKind.DOCTOR -> R.string.directory_kind_doctor
    CareDirectoryKind.HOSPITAL -> R.string.directory_kind_hospital
    CareDirectoryKind.CLINIC -> R.string.directory_kind_clinic
    CareDirectoryKind.PHARMACY -> R.string.directory_kind_pharmacy
    CareDirectoryKind.LABORATORY -> R.string.directory_kind_laboratory
    CareDirectoryKind.OTHER -> R.string.directory_kind_other
}

@StringRes
fun CareDirectiveKind.labelResource(): Int = when (this) {
    CareDirectiveKind.ADVANCE_DIRECTIVE -> R.string.directive_kind_advance
    CareDirectiveKind.CARE_PREFERENCE -> R.string.directive_kind_preference
    CareDirectiveKind.PROCEDURE_CONSENT_RECORD -> R.string.directive_kind_consent
    CareDirectiveKind.OTHER -> R.string.directive_kind_other
}

@StringRes
fun HealthIdentifierKind.labelResource(): Int = when (this) {
    HealthIdentifierKind.NATIONAL_HEALTH -> R.string.identifier_kind_national_health
    HealthIdentifierKind.SOCIAL_SECURITY -> R.string.identifier_kind_social_security
    HealthIdentifierKind.INSURANCE -> R.string.identifier_kind_insurance
    HealthIdentifierKind.PATIENT -> R.string.identifier_kind_patient
    HealthIdentifierKind.OTHER -> R.string.identifier_kind_other
}
