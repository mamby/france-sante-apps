package net.mamby.health.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.LocalProfileDisplayLabels
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.theme.UiTokens

/**
 * Collects a concrete owner before opening an editor for person-owned information.
 * The typed name stays only in the navigation entry's in-memory ViewModel.
 */
@Composable
fun ProfileOwnerGateScreen(
    profiles: List<ProfileRecord>,
    proposedProfileId: UUID,
    onBack: () -> Unit,
    onProfileSelected: (UUID) -> Unit,
    onCreateProfile: (String, UUID, (Boolean) -> Unit) -> Unit,
    state: ProfileOwnerGateViewModel = viewModel(),
) {
    val labels = LocalProfileDisplayLabels.current
    val needsNewProfile = profiles.isEmpty() || state.adding
    BackHandler(enabled = state.saving) { /* Persistence must finish before leaving this flow. */ }

    AppScreenScaffold(
        title = stringResource(if (profiles.isEmpty()) R.string.profile_owner_gate_new_title else R.string.profile_owner_gate_title),
        onBack = if (state.saving) null else onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .withPagePadding(),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            item { PageHeader() }
            item {
                Text(
                    stringResource(
                        if (profiles.isEmpty()) {
                            R.string.profile_owner_gate_empty_body
                        } else {
                            R.string.profile_owner_gate_choose_body
                        },
                    ),
                )
            }

            if (!needsNewProfile) {
                profiles.forEach { record ->
                    item(key = record.profile.id) {
                        ListItem(
                            headlineContent = {
                                ProfileMarker(
                                    profile = record.profile,
                                    displayLabel = labels[record.profile.id] ?: record.profile.displayName,
                                )
                            },
                            modifier = Modifier.clickable {
                                onProfileSelected(record.profile.id)
                            },
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { state.adding = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_profile))
                    }
                }
            } else {
                item {
                    SectionCard(stringResource(R.string.profile_owner_gate_new_title)) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = { state.name = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving,
                            label = { Text(stringResource(R.string.display_name)) },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                state.saving = true
                                onCreateProfile(state.name.trim(), proposedProfileId) { succeeded ->
                                    if (!succeeded) state.saving = false
                                }
                            },
                            enabled = state.name.isNotBlank() && !state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.profile_owner_gate_add_continue))
                        }
                        if (profiles.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    state.adding = false
                                    state.name = ""
                                },
                                enabled = !state.saving,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}

class ProfileOwnerGateViewModel : ViewModel() {
    var name by mutableStateOf("")
    var adding by mutableStateOf(false)
    var saving by mutableStateOf(false)
}
