package net.mamby.health.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.FamilyHistoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Vaccination
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.detailTitleBarActions
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

@Composable
fun EmergencyContactDetailScreen(
    record: ProfileRecord,
    contact: EmergencyContact,
    onBack: (() -> Unit)?,
    onEdit: (() -> Unit)? = null,
) = HealthInfoDetailScaffold(record, contact.name, onBack, onEdit) {
    LabeledValue(stringResource(R.string.contact_relationship), contact.relationship)
    LabeledValue(stringResource(R.string.contact_phone), contact.phoneNumber)
    contact.notes?.let { LabeledValue(stringResource(R.string.common_notes), it) }
}

@Composable
fun VaccinationDetailScreen(
    record: ProfileRecord,
    vaccination: Vaccination,
    onBack: (() -> Unit)?,
    onEdit: (() -> Unit)? = null,
) = HealthInfoDetailScaffold(record, vaccination.name, onBack, onEdit) {
    LabeledValue(stringResource(R.string.vaccination_date), vaccination.dateAdministered.localizedDate())
    vaccination.provider?.let { LabeledValue(stringResource(R.string.vaccination_provider), it) }
    vaccination.lotNumber?.let { LabeledValue(stringResource(R.string.vaccination_lot_number), it) }
    vaccination.nextDueOn?.let { LabeledValue(stringResource(R.string.vaccination_next_due), it.localizedDate()) }
    vaccination.notes?.let { LabeledValue(stringResource(R.string.common_notes), it) }
}

@Composable
fun FamilyHistoryDetailScreen(
    record: ProfileRecord,
    entry: FamilyHistoryEntry,
    onBack: (() -> Unit)?,
    onEdit: (() -> Unit)? = null,
) = HealthInfoDetailScaffold(record, entry.condition, onBack, onEdit) {
    LabeledValue(stringResource(R.string.family_relationship), entry.relationship)
    entry.ageAtOnsetYears?.let {
        LabeledValue(stringResource(R.string.family_age_at_onset), it.toString())
    }
    entry.notes?.let { LabeledValue(stringResource(R.string.common_notes), it) }
}

@Composable
fun CareDirectiveDetailScreen(
    record: ProfileRecord,
    directive: CareDirective,
    onBack: (() -> Unit)?,
    onEdit: (() -> Unit)? = null,
) = HealthInfoDetailScaffold(record, directive.title, onBack, onEdit) {
    Text(stringResource(R.string.directives_disclaimer))
    LabeledValue(stringResource(R.string.directive_kind), stringResource(directive.kind.labelResource()))
    LabeledValue(stringResource(R.string.directive_date), directive.recordedOn.localizedDate())
    LabeledValue(stringResource(R.string.directive_text), directive.text)
    if (directive.relatedDocumentIds.isNotEmpty()) {
        LabeledValue(
            stringResource(R.string.directive_related_documents),
            directive.relatedDocumentIds.mapNotNull { id -> record.documents.firstOrNull { it.id == id }?.title }.joinToString(),
        )
    }
}

@Composable
fun HealthIdentifierDetailScreen(
    record: ProfileRecord,
    identifier: HealthIdentifier,
    onBack: (() -> Unit)?,
    onEdit: (() -> Unit)? = null,
) {
    var revealed by remember(identifier.id) { mutableStateOf(false) }
    HealthInfoDetailScaffold(record, identifier.label, onBack, onEdit) {
        LabeledValue(stringResource(R.string.identifier_kind), stringResource(identifier.kind.labelResource()))
        LabeledValue(
            stringResource(R.string.identifier_value),
            if (revealed) identifier.value else maskIdentifier(identifier.value),
        )
        Button(onClick = { revealed = !revealed }) {
            Text(stringResource(if (revealed) R.string.hide_identifier_value else R.string.reveal_identifier_value))
        }
        identifier.issuer?.let { LabeledValue(stringResource(R.string.identifier_issuer), it) }
        identifier.country?.let { LabeledValue(stringResource(R.string.identifier_country), it) }
        identifier.notes?.let { LabeledValue(stringResource(R.string.common_notes), it) }
    }
}

@Composable
private fun HealthInfoDetailScaffold(
    record: ProfileRecord,
    title: String,
    onBack: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    AppScreenScaffold(
        title = title,
        onBack = onBack,
        actions = detailTitleBarActions(onEdit = onEdit),
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
            ProfileOwnerHeader(record.profile)
            content()
        }
    }
}

internal fun maskIdentifier(value: String): String = when {
    value.isBlank() -> ""
    value.length <= 4 -> "••••"
    else -> "•••• ${value.takeLast(4)}"
}
