package net.mamby.health.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.asReference
import net.mamby.health.feature.vault.DocumentDetailScreen
import net.mamby.health.feature.vault.DocumentPreviewState
import net.mamby.health.ui.theme.HealthVaultTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentPreviewIsolationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun previewFromAnotherProfileIsNeverRenderedForTheOpenDocument() {
        val owner = profile("11111111-1111-4111-8111-111111111111", "Owner B")
        val document = document(
            id = "22222222-2222-4222-8222-222222222222",
            blobId = "33333333-3333-4333-8333-333333333333",
        )
        val otherOwnerId = UUID.fromString("44444444-4444-4444-8444-444444444444")
        val otherDocumentId = UUID.fromString("55555555-5555-4555-8555-555555555555")

        composeRule.setContent {
            HealthVaultTheme {
                DocumentDetailScreen(
                    document = document,
                    record = ProfileRecord(profile = owner, documents = listOf(document)),
                    preview = DocumentPreviewState.Ready(
                        profileId = otherOwnerId,
                        documentId = otherDocumentId,
                        image = ImageBitmap(1, 1),
                        page = 0,
                        pageCount = 1,
                    ),
                    onBack = {},
                    onLoadPreview = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        val previewLabel = composeRule.activity.getString(R.string.document_preview)
        composeRule.onNode(hasText(previewLabel) and hasClickAction()).assertExists()
    }

    private fun profile(id: String, name: String) = HealthProfile(
        id = UUID.fromString(id),
        displayName = name,
        lastUpdatedAt = NOW,
    )

    private fun document(id: String, blobId: String) = MedicalDocument(
        id = UUID.fromString(id),
        title = "Document B",
        category = BuiltInDocumentCategory.OTHER.asReference(),
        documentDate = LocalDate.of(2026, 8, 8),
        source = "Clinic",
        blobId = UUID.fromString(blobId),
        mimeType = "application/pdf",
        sizeBytes = 1,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-08T08:00:00Z")
    }
}
