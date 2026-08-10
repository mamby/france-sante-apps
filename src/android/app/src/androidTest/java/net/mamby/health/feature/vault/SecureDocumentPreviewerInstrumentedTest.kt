package net.mamby.health.feature.vault

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.asReference
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Vaccination
import net.mamby.health.data.ImportedDocumentData
import net.mamby.health.data.MedicalDocumentDraft
import net.mamby.health.data.RestoreDocumentBlob
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState
import net.mamby.health.testing.StubVaultRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureDocumentPreviewerInstrumentedTest {
    @Test
    fun authenticatedImage_isDecodedAndPlaintextBufferIsCleared() = runBlocking {
        val plaintext = createPng(width = 24, height = 12)
        val repository = PreviewVaultRepository(plaintext)
        val document = document("image/png", plaintext.size.toLong())
        val previewer = SecureDocumentPreviewer(ApplicationProvider.getApplicationContext(), repository)

        val rendered = previewer.render(PROFILE_ID, document, requestedPage = 0)

        assertEquals(24, rendered.image.width)
        assertEquals(12, rendered.image.height)
        assertEquals(0, rendered.page)
        assertEquals(1, rendered.pageCount)
        assertTrue("Preview plaintext should be cleared after decoding", plaintext.all { it == 0.toByte() })
    }

    @Test
    fun authenticatedPdf_isRenderedThroughAnInMemoryDescriptorAndCleared() = runBlocking {
        val plaintext = createPdf(width = 120, height = 80)
        val previewer = SecureDocumentPreviewer(
            ApplicationProvider.getApplicationContext(),
            PreviewVaultRepository(plaintext),
        )

        val rendered = previewer.render(
            PROFILE_ID,
            document("application/pdf", plaintext.size.toLong()),
            requestedPage = 0,
        )

        assertEquals(120, rendered.image.width)
        assertEquals(80, rendered.image.height)
        assertEquals(0, rendered.page)
        assertEquals(1, rendered.pageCount)
        assertTrue("Preview plaintext should be cleared after rendering", plaintext.all { it == 0.toByte() })
    }

    @Test
    fun missingEncryptedBlob_failsWithoutCreatingPreview() {
        val previewer = SecureDocumentPreviewer(
            ApplicationProvider.getApplicationContext(),
            PreviewVaultRepository(null),
        )

        assertThrows(IOException::class.java) {
            runBlocking { previewer.render(PROFILE_ID, document("image/png", 0), requestedPage = 0) }
        }
    }

    private fun document(mimeType: String, size: Long) = MedicalDocument(
        id = UUID.fromString("0b45740d-cbf5-4b0b-b321-50dd33acf4d4"),
        title = "Synthetic image",
        category = BuiltInDocumentCategory.REPORTS.asReference(),
        documentDate = LocalDate.of(2026, 7, 30),
        source = "Instrumented test",
        blobId = BLOB_ID,
        mimeType = mimeType,
        sizeBytes = size,
        updatedAt = Instant.parse("2026-07-30T08:00:00Z"),
    )

    private fun createPng(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(14, 124, 115))
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun createPdf(width: Int, height: Int): ByteArray {
        val pdf = PdfDocument()
        return try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            page.canvas.drawColor(Color.rgb(14, 124, 115))
            pdf.finishPage(page)
            ByteArrayOutputStream().use { output ->
                pdf.writeTo(output)
                output.toByteArray()
            }
        } finally {
            pdf.close()
        }
    }

    private class PreviewVaultRepository(private val plaintext: ByteArray?) : StubVaultRepository() {
        override suspend fun readDocumentBlob(profileId: UUID, blobId: UUID): ByteArray? =
            plaintext?.takeIf { profileId == PROFILE_ID && blobId == BLOB_ID }
    }

    private companion object {
        val BLOB_ID: UUID = UUID.fromString("8ff3e7d6-92ed-4164-8b63-4fbddae1d4f8")
        val PROFILE_ID: UUID = UUID.fromString("2f59f953-d6a2-4577-af28-3576c994094f")
    }
}
