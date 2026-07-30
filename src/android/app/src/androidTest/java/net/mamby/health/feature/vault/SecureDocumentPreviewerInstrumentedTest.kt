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
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.Vaccination
import net.mamby.health.data.ImportedDocumentData
import net.mamby.health.data.MedicalDocumentDraft
import net.mamby.health.data.RestoreDocumentBlob
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState
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

        val rendered = previewer.render(document, requestedPage = 0)

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

        val rendered = previewer.render(document("application/pdf", plaintext.size.toLong()), requestedPage = 0)

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
            runBlocking { previewer.render(document("image/png", 0), requestedPage = 0) }
        }
    }

    private fun document(mimeType: String, size: Long) = MedicalDocument(
        id = UUID.fromString("0b45740d-cbf5-4b0b-b321-50dd33acf4d4"),
        title = "Synthetic image",
        category = DocumentCategory.REPORTS,
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

    private class PreviewVaultRepository(private val plaintext: ByteArray?) : VaultRepository {
        override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.Loading)

        override suspend fun readDocumentBlob(blobId: UUID): ByteArray? =
            plaintext?.takeIf { blobId == BLOB_ID }

        override fun searchDocuments(query: String, category: DocumentCategory): Flow<List<MedicalDocument>> =
            emptyFlow()

        override suspend fun initialize(): Unit = unused()
        override suspend fun createEmpty(displayName: String): Unit = unused()
        override suspend fun updateProfile(profile: HealthProfile): Unit = unused()
        override suspend fun upsertEmergencyContact(contact: EmergencyContact): Unit = unused()
        override suspend fun deleteEmergencyContact(contactId: UUID): Unit = unused()
        override suspend fun importDocument(
            draft: MedicalDocumentDraft,
            imported: ImportedDocumentData,
        ): MedicalDocument = unused()
        override suspend fun updateDocument(document: MedicalDocument): Unit = unused()
        override suspend fun deleteDocument(documentId: UUID): Unit = unused()
        override suspend fun upsertMedication(medication: Medication): Unit = unused()
        override suspend fun deleteMedication(medicationId: UUID): Unit = unused()
        override suspend fun upsertAppointment(appointment: Appointment): Unit = unused()
        override suspend fun deleteAppointment(appointmentId: UUID): Unit = unused()
        override suspend fun upsertVaccination(vaccination: Vaccination): Unit = unused()
        override suspend fun deleteVaccination(vaccinationId: UUID): Unit = unused()
        override suspend fun upsertReminder(reminder: Reminder): Unit = unused()
        override suspend fun deleteReminder(reminderId: UUID): Unit = unused()
        override suspend fun exportSnapshot(): HealthVault = unused()
        override suspend fun copyDocumentBlob(blobId: UUID, output: OutputStream): Long = unused()
        override suspend fun restore(vault: HealthVault, documentBlobs: List<RestoreDocumentBlob>): Unit = unused()
        override suspend fun deleteVault(): Unit = unused()

        private fun <T> unused(): T = error("Not used by preview tests")
    }

    private companion object {
        val BLOB_ID: UUID = UUID.fromString("8ff3e7d6-92ed-4164-8b63-4fbddae1d4f8")
    }
}
