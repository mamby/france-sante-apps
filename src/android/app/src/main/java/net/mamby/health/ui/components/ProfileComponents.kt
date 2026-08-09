package net.mamby.health.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.theme.LocalProfileAccentPalette
import net.mamby.health.ui.theme.UiTokens

val LocalProfileDisplayLabels = staticCompositionLocalOf<Map<UUID, String>> { emptyMap() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListFilterHeader(
    records: List<ProfileRecord>,
    selectedProfileId: UUID?,
    onSelected: (UUID?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chooserVisible by remember { mutableStateOf(false) }
    val labels = LocalProfileDisplayLabels.current
    ProfileFilterHeader(
        label = selectedProfileId?.let { labels[it] } ?: stringResource(R.string.all_profiles),
        profileId = selectedProfileId,
        onClick = { chooserVisible = true },
        modifier = modifier,
    )
    if (chooserVisible) {
        ModalBottomSheet(onDismissRequest = { chooserVisible = false }) {
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.all_profiles)) },
                        modifier = Modifier.clickable {
                            onSelected(null)
                            chooserVisible = false
                        },
                    )
                }
                items(records, key = { it.profile.id }) { record ->
                    ListItem(
                        headlineContent = { ProfileMarker(record.profile) },
                        modifier = Modifier.clickable {
                            onSelected(record.profile.id)
                            chooserVisible = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerField(
    records: List<ProfileRecord>,
    selectedProfileId: UUID?,
    onSelected: (UUID) -> Unit,
    onAddProfile: (String, (UUID) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chooserVisible by remember { mutableStateOf(false) }
    var addVisible by remember { mutableStateOf(false) }
    val labels = LocalProfileDisplayLabels.current
    OutlinedButton(
        onClick = { chooserVisible = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(selectedProfileId?.let { labels[it] } ?: stringResource(R.string.choose_profile))
    }
    if (chooserVisible) {
        ModalBottomSheet(onDismissRequest = { chooserVisible = false }) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(records, key = { it.profile.id }) { record ->
                    ListItem(
                        headlineContent = { ProfileMarker(record.profile) },
                        modifier = Modifier.clickable {
                            onSelected(record.profile.id)
                            chooserVisible = false
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.add_profile)) },
                        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            chooserVisible = false
                            addVisible = true
                        },
                    )
                }
            }
        }
    }
    if (addVisible) {
        var name by remember { mutableStateOf("") }
        FormDialog(
            title = stringResource(R.string.add_profile),
            saveEnabled = name.isNotBlank(),
            onDismiss = { addVisible = false },
            onSave = {
                onAddProfile(name.trim()) { profileId ->
                    onSelected(profileId)
                    addVisible = false
                }
            },
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.display_name)) },
                singleLine = true,
            )
        }
    }
}

/**
 * The interactive context header used by profile-filtered collection screens.
 * A null [profileId] represents the all-profiles filter.
 */
@Composable
fun ProfileFilterHeader(
    label: String,
    profileId: UUID?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibleLabel: String = label,
    actionLabel: String? = null,
) {
    val resolvedActionLabel = actionLabel ?: stringResource(R.string.profile_filter)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = resolvedActionLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleLabel
            }
            .padding(
                horizontal = UiTokens.ScreenPadding,
                vertical = UiTokens.CompactSpacing,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profileId == null) {
            AllProfilesAvatar()
        } else {
            ProfileAvatar(
                profileId = profileId,
                monogram = profileMonogram(label),
                size = UiTokens.ProfileAvatarSize,
            )
        }
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = UiTokens.ContentSpacing),
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

/** A non-interactive context header for content owned by exactly one profile. */
@Composable
fun ProfileOwnerHeader(
    profile: HealthProfile,
    modifier: Modifier = Modifier,
    displayLabel: String? = null,
    accessibleLabel: String? = null,
) {
    val resolvedLabel = displayLabel
        ?: LocalProfileDisplayLabels.current[profile.id]
        ?: profile.displayName
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleLabel ?: resolvedLabel
            }
            .padding(
                horizontal = UiTokens.ScreenPadding,
                vertical = UiTokens.CompactSpacing,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            profileId = profile.id,
            monogram = profileMonogram(profile.displayName),
            size = UiTokens.ProfileAvatarSize,
        )
        Text(
            text = resolvedLabel,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = UiTokens.ContentSpacing),
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A compact, text-backed owner marker for cards shown in a mixed-profile list. */
@Composable
fun ProfileMarker(
    profile: HealthProfile,
    modifier: Modifier = Modifier,
    displayLabel: String? = null,
    accessibleLabel: String? = null,
) {
    val resolvedLabel = displayLabel
        ?: LocalProfileDisplayLabels.current[profile.id]
        ?: profile.displayName
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibleLabel ?: resolvedLabel
        },
        horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            profileId = profile.id,
            monogram = profileMonogram(profile.displayName),
            size = UiTokens.ProfileMarkerAvatarSize,
            compact = true,
        )
        Text(
            text = resolvedLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Builds deterministic labels for duplicate profile names in vault order. The caller owns
 * localization and supplies the visible or spoken ordinal format.
 */
fun disambiguatedProfileLabels(
    profiles: List<HealthProfile>,
    duplicateLabel: (profile: HealthProfile, ordinal: Int, total: Int) -> String,
): Map<UUID, String> {
    val totals = profiles.groupingBy { it.displayName.profileNameKey() }.eachCount()
    val occurrences = mutableMapOf<String, Int>()

    return buildMap(profiles.size) {
        profiles.forEach { profile ->
            val key = profile.displayName.profileNameKey()
            val total = totals.getValue(key)
            val ordinal = occurrences.getOrDefault(key, 0) + 1
            occurrences[key] = ordinal
            put(
                profile.id,
                if (total == 1) profile.displayName else duplicateLabel(profile, ordinal, total),
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    profileId: UUID,
    monogram: String,
    size: Dp,
    compact: Boolean = false,
) {
    val colors = LocalProfileAccentPalette.current.colorsFor(profileId)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.container)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram,
            color = colors.onContainer,
            fontWeight = FontWeight.SemiBold,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
    }
}

@Composable
private fun AllProfilesAvatar() {
    Box(
        modifier = Modifier
            .size(UiTokens.ProfileAvatarSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Group,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun profileMonogram(displayName: String): String {
    val normalizedName = displayName.trim()
    val nameWords = normalizedName
        .splitToSequence(Regex("\\s+"))
        .filter { word -> word.any(Char::isLetter) }
        .take(2)
        .toList()
        .ifEmpty { listOf(normalizedName) }
    return nameWords.joinToString(separator = "") { word ->
        word.substring(0, word.offsetByCodePoints(0, 1)).uppercase(Locale.getDefault())
    }
}

private fun String.profileNameKey(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
