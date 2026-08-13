package net.mamby.health.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.EmptyState
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LocalProfileDisplayLabels
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.theme.UiTokens

@Composable
fun ProfileManagementScreen(
    profiles: List<ProfileRecord>,
    onBack: (() -> Unit)? = null,
    onAdd: (String) -> Unit,
    onRename: (UUID, HealthProfile) -> Unit,
    onDelete: (UUID) -> Unit,
) {
    val profileLabels = LocalProfileDisplayLabels.current
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<HealthProfile?>(null) }
    var deleting by remember { mutableStateOf<HealthProfile?>(null) }

    AppScreenScaffold(title = stringResource(R.string.profiles_title), onBack = onBack) { padding ->
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
            if (profiles.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.people_empty_title),
                    body = stringResource(R.string.people_empty_body),
                )
            }
            profiles.forEach { record ->
                SectionCard(profileLabels[record.profile.id] ?: record.profile.displayName) {
                    Button(onClick = { renaming = record.profile }) {
                        Text(stringResource(R.string.rename_profile))
                    }
                    OutlinedButton(
                        onClick = { deleting = record.profile },
                    ) {
                        Text(stringResource(R.string.delete_profile))
                    }
                }
            }
            Button(onClick = { adding = true }) { Text(stringResource(R.string.add_profile)) }
        }
    }

    if (adding) {
        ProfileNameDialog(
            title = stringResource(R.string.add_profile),
            initialName = "",
            onDismiss = { adding = false },
            onSave = {
                adding = false
                onAdd(it)
            },
        )
    }
    renaming?.let { profile ->
        ProfileNameDialog(
            title = stringResource(
                R.string.rename_profile_named,
                profileLabels[profile.id] ?: profile.displayName,
            ),
            initialName = profile.displayName,
            onDismiss = { renaming = null },
            onSave = { name ->
                renaming = null
                onRename(profile.id, profile.copy(displayName = name, lastUpdatedAt = Instant.now()))
            },
        )
    }
    deleting?.let { profile ->
        DeleteProfileDialog(
            profile = profile,
            displayLabel = profileLabels[profile.id] ?: profile.displayName,
            onDismiss = { deleting = null },
            onDelete = {
                deleting = null
                onDelete(profile.id)
            },
        )
    }
}

@Composable
fun ProfileNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    FormDialog(
        title = title,
        saveEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = { onSave(name.trim()) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.display_name)) },
            singleLine = true,
        )
    }
}

@Composable
private fun DeleteProfileDialog(
    profile: HealthProfile,
    displayLabel: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmation by remember(profile.id) { mutableStateOf("") }
    FormDialog(
        title = stringResource(R.string.delete_profile_named, displayLabel),
        saveLabel = stringResource(R.string.common_delete),
        saveEnabled = confirmation == profile.displayName,
        onDismiss = onDismiss,
        onSave = onDelete,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            Text(stringResource(R.string.delete_profile_message, profile.displayName))
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(stringResource(R.string.delete_profile_confirmation, profile.displayName)) },
                singleLine = true,
            )
        }
    }
}
