package net.mamby.health.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import java.time.Instant
import java.util.UUID
import net.mamby.health.core.model.VaultContact
import net.mamby.health.feature.contacts.ContactEditorScreen
import net.mamby.health.feature.contacts.ContactsScreen
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.ui.components.AppNavigationSuite
import net.mamby.health.ui.components.EditorBackgroundPane
import net.mamby.health.ui.components.appNavigationSuiteType
import net.mamby.health.ui.theme.HealthVaultTheme

@PreviewTest
@Preview(name = "Contact editor compact", widthDp = 400, heightDp = 1_000)
@Composable
fun contactEditorCompact() {
    ContactEditorPreviewContent()
}

@PreviewTest
@Preview(name = "Contact editor expanded", widthDp = 1_200, heightDp = 800)
@Composable
fun contactEditorExpanded() {
    ContactEditorPreviewContent()
}

@PreviewTest
@Preview(
    name = "Contact editor dark",
    widthDp = 400,
    heightDp = 1_000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun contactEditorDark() {
    ContactEditorPreviewContent(darkTheme = true)
}

@PreviewTest
@Preview(name = "Contact editor large text", widthDp = 400, heightDp = 1_000, fontScale = 1.5f)
@Composable
fun contactEditorLargeText() {
    ContactEditorPreviewContent()
}

@PreviewTest
@Preview(name = "Contact editor Arabic RTL", widthDp = 400, heightDp = 1_000, locale = "ar")
@Composable
fun contactEditorArabicRtl() {
    ContactEditorPreviewContent()
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ContactEditorPreviewContent(darkTheme: Boolean = false) {
    HealthVaultTheme(darkTheme = darkTheme) {
        val adaptiveInfo = currentWindowAdaptiveInfoV2()
        val layoutType = appNavigationSuiteType(adaptiveInfo)
        AppNavigationSuite(
            selectedDestination = TopLevelDestination.Contacts,
            layoutType = layoutType,
            isMoreSelected = false,
            onDestinationSelected = {},
            onMoreSelected = {},
            navigationVisible = false,
        ) {
            if (layoutType == NavigationSuiteType.NavigationRail) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxSize(),
                    ) {
                        EditorBackgroundPane(editorActive = true) {
                            ContactsScreen(
                                contacts = CONTACTS,
                                onAdd = {},
                                onSelected = {},
                            )
                        }
                    }
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxSize(),
                    ) {
                        ContactEditorScreen(
                            existing = CONTACTS.first(),
                            onCancel = {},
                            onSave = { _, _ -> },
                        )
                    }
                }
            } else {
                ContactEditorScreen(
                    existing = CONTACTS.first(),
                    onCancel = {},
                    onSave = { _, _ -> },
                )
            }
        }
    }
}

private val CONTACTS = listOf(
    VaultContact(
        id = UUID.fromString("99999999-9999-4999-8999-999999999999"),
        name = "Samira Haddad",
        phoneNumbers = listOf("+33 6 12 34 56 78"),
        emailAddresses = listOf("samira@example.com"),
        websites = listOf("https://example.com"),
        addresses = listOf("10 rue de la Paix, Paris"),
        notes = "Family doctor",
        updatedAt = Instant.parse("2026-08-13T08:00:00Z"),
    ),
    VaultContact(
        id = UUID.fromString("88888888-8888-4888-8888-888888888888"),
        name = "Dr. Martin",
        updatedAt = Instant.parse("2026-08-12T08:00:00Z"),
    ),
    VaultContact(
        id = UUID.fromString("77777777-7777-4777-8777-777777777777"),
        name = "Pharmacy",
        updatedAt = Instant.parse("2026-08-11T08:00:00Z"),
    ),
)
