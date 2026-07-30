package net.mamby.health.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.mamby.health.testing.TestDocumentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentImporterInstrumentedTest {
    private val importer = DocumentImporter(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun validProviderDocument_isVerifiedAndCopied() = runBlocking {
        val imported = importer.import(uri(TestDocumentProvider.VALID_PDF))

        assertEquals("valid-pdf.pdf", imported.displayName)
        assertEquals("application/pdf", imported.mimeType)
        assertEquals(imported.content.size.toLong(), imported.sizeBytes)
        assertTrue(imported.content.copyOfRange(0, 5).contentEquals("%PDF-".encodeToByteArray()))
        imported.content.fill(0)
    }

    @Test
    fun mismatchedProviderMime_isRejected() = runBlocking {
        assertFailure(TestDocumentProvider.MISMATCHED_PDF, DocumentImportFailure.MIME_MISMATCH)
    }

    @Test
    fun oversizedProviderMetadata_isRejectedBeforeReading() = runBlocking {
        assertFailure(TestDocumentProvider.OVERSIZED_PDF, DocumentImportFailure.FILE_TOO_LARGE)
    }

    @Test
    fun providerOpenFailure_isReportedWithoutFallback() = runBlocking {
        assertFailure(TestDocumentProvider.UNAVAILABLE_PDF, DocumentImportFailure.SOURCE_UNAVAILABLE)
    }

    private suspend fun assertFailure(path: String, expected: DocumentImportFailure) {
        try {
            importer.import(uri(path))
            fail("Expected a DocumentImportException")
        } catch (error: DocumentImportException) {
            assertEquals(expected, error.reason)
        }
    }

    private fun uri(path: String): Uri = Uri.Builder()
        .scheme("content")
        .authority(TestDocumentProvider.AUTHORITY)
        .appendPath(path)
        .build()
}
