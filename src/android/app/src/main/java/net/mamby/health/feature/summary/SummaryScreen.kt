package net.mamby.health.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.FamilyHistoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FloatingAddButton
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.labelResource
import net.mamby.health.ui.format.localizedDate
import net.mamby.health.ui.theme.UiTokens

@Composable
fun SummaryScreen(
    record: ProfileRecord,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onAddEmergencyContact: () -> Unit,
    onEditEmergencyContact: (UUID) -> Unit,
    onAddVaccination: () -> Unit,
    onEditVaccination: (UUID) -> Unit,
    onAddFamilyHistory: () -> Unit,
    onEditFamilyHistory: (UUID) -> Unit,
    onAddDirective: () -> Unit,
    onEditDirective: (UUID) -> Unit,
    onAddIdentifier: () -> Unit,
    onEditIdentifier: (UUID) -> Unit,
    onEmergencyContactSelected: (UUID) -> Unit,
    onVaccinationSelected: (UUID) -> Unit,
    onFamilyHistorySelected: (UUID) -> Unit,
    onDirectiveSelected: (UUID) -> Unit,
    onIdentifierSelected: (UUID) -> Unit,
) {
    val profile = record.profile
    AppScreenScaffold(
        title = stringResource(R.string.health_info_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingAddButton(
                label = stringResource(R.string.add_vaccination),
                onClick = onAddVaccination,
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(UiTokens.CardMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = innerPadding.withPagePadding(),
            horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageHeader()
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileOwnerHeader(profile)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionCard(stringResource(R.string.summary_title)) {
                    LabeledValue(stringResource(R.string.display_name), profile.displayName)
                    LabeledValue(stringResource(R.string.blood_type), profile.bloodType.orEmpty())
                    LabeledValue(stringResource(R.string.allergies), profile.allergies.joinToString())
                    LabeledValue(
                        stringResource(R.string.chronic_conditions),
                        profile.chronicConditions.joinToString(),
                    )
                    LabeledValue(stringResource(R.string.surgeries), profile.surgeries.joinToString())
                    Button(onClick = onEditProfile) {
                        Text(stringResource(R.string.edit_profile))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.family_history_title))
            }
            items(record.familyHistory, key = FamilyHistoryEntry::id) { entry ->
                SectionCard(entry.condition) {
                    Text(entry.relationship)
                    entry.ageAtOnsetYears?.let {
                        Text(stringResource(R.string.family_history_age_at_onset_value, it))
                    }
                    entry.notes?.let { Text(it) }
                    OutlinedButton(onClick = { onFamilyHistorySelected(entry.id) }) {
                        Text(stringResource(R.string.common_open))
                    }
                    Button(onClick = { onEditFamilyHistory(entry.id) }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = onAddFamilyHistory) {
                    Text(stringResource(R.string.add_family_history))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.directives_title))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.directives_disclaimer))
            }
            items(record.directives, key = CareDirective::id) { directive ->
                SectionCard(directive.title) {
                    Text(stringResource(directive.kind.labelResource()))
                    Text(directive.recordedOn.localizedDate())
                    Text(directive.text)
                    OutlinedButton(onClick = { onDirectiveSelected(directive.id) }) {
                        Text(stringResource(R.string.common_open))
                    }
                    Button(onClick = { onEditDirective(directive.id) }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = onAddDirective) {
                    Text(stringResource(R.string.add_directive))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.health_identifiers_title))
            }
            items(record.healthIdentifiers, key = HealthIdentifier::id) { identifier ->
                SectionCard(identifier.label) {
                    Text(maskIdentifier(identifier.value))
                    identifier.issuer?.let { Text(it) }
                    OutlinedButton(onClick = { onIdentifierSelected(identifier.id) }) {
                        Text(stringResource(R.string.common_open))
                    }
                    Button(onClick = { onEditIdentifier(identifier.id) }) {
                        Text(stringResource(R.string.common_edit))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = onAddIdentifier) {
                    Text(stringResource(R.string.add_health_identifier))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.emergency_contacts))
            }
            if (profile.emergencyContacts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.emergency_contacts),
                        stringResource(R.string.common_not_set),
                    )
                }
            } else {
                items(profile.emergencyContacts, key = { it.id }) { contact ->
                    SectionCard(contact.name) {
                        Text(contact.relationship)
                        Text(contact.phoneNumber)
                        contact.notes?.let { Text(it) }
                        OutlinedButton(onClick = { onEmergencyContactSelected(contact.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                        Button(onClick = { onEditEmergencyContact(contact.id) }) {
                            Text(stringResource(R.string.common_edit))
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = onAddEmergencyContact) {
                    Text(stringResource(R.string.add_emergency_contact))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.vaccinations))
            }
            if (record.vaccinations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(R.string.vaccinations),
                        stringResource(R.string.common_not_set),
                    )
                }
            } else {
                items(
                    items = record.vaccinations.sortedByDescending { it.dateAdministered },
                    key = { it.id },
                ) { vaccination ->
                    SectionCard(vaccination.name) {
                        Text(vaccination.dateAdministered.localizedDate())
                        vaccination.provider?.let { Text(it) }
                        vaccination.nextDueOn?.let { Text(it.localizedDate()) }
                        OutlinedButton(onClick = { onVaccinationSelected(vaccination.id) }) {
                            Text(stringResource(R.string.common_open))
                        }
                        Button(onClick = { onEditVaccination(vaccination.id) }) {
                            Text(stringResource(R.string.common_edit))
                        }
                    }
                }
            }
        }
    }
}
