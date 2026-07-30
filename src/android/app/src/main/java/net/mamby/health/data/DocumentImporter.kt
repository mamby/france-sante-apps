package net.mamby.health.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DocumentImportPolicy {
    const val MAX_DOCUMENT_BYTES: Long = 25L * 1024L * 1024L
}

enum class DocumentImportFailure {
    SOURCE_UNAVAILABLE,
    FILE_TOO_LARGE,
    UNSUPPORTED_MIME,
    UNSUPPORTED_SIGNATURE,
    MIME_MISMATCH,
}

class DocumentImportException(
    val reason: DocumentImportFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Singleton
class DocumentImporter @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun import(uri: Uri): ImportedDocumentData = withContext(Dispatchers.IO) {
        val metadata = try {
            readMetadata(uri)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw DocumentImportException(
                DocumentImportFailure.SOURCE_UNAVAILABLE,
                "The selected document metadata could not be read.",
                error,
            )
        }
        if (metadata.sizeBytes != null && metadata.sizeBytes > DocumentImportPolicy.MAX_DOCUMENT_BYTES) {
            throw DocumentImportException(
                DocumentImportFailure.FILE_TOO_LARGE,
                "Document exceeds the 25 MiB import limit.",
            )
        }

        val content = try {
            contentResolver.openInputStream(uri)?.use(::readBounded)
                ?: throw DocumentImportException(
                    DocumentImportFailure.SOURCE_UNAVAILABLE,
                    "The selected document could not be opened.",
                )
        } catch (error: DocumentImportException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw DocumentImportException(
                DocumentImportFailure.SOURCE_UNAVAILABLE,
                "The selected document could not be read.",
                error,
            )
        }

        try {
            val detectedMimeType = detectMimeType(content)
                ?: throw DocumentImportException(
                    DocumentImportFailure.UNSUPPORTED_SIGNATURE,
                    "The selected file is not a supported PDF or image.",
                )
            val declaredMimeType = normalizeMimeType(contentResolver.getType(uri))
            val acceptedMimeType = when {
                declaredMimeType == null || declaredMimeType == GENERIC_BINARY_MIME -> detectedMimeType
                declaredMimeType !in SUPPORTED_MIME_TYPES -> throw DocumentImportException(
                    DocumentImportFailure.UNSUPPORTED_MIME,
                    "The selected document has an unsupported MIME type.",
                )
                declaredMimeType != detectedMimeType -> throw DocumentImportException(
                    DocumentImportFailure.MIME_MISMATCH,
                    "The document content does not match its declared MIME type.",
                )
                else -> declaredMimeType
            }

            ImportedDocumentData(
                displayName = metadata.displayName,
                mimeType = acceptedMimeType,
                sizeBytes = content.size.toLong(),
                content = content,
            )
        } catch (error: Exception) {
            content.fill(0)
            throw error
        }
    }

    private fun readMetadata(uri: Uri): SourceMetadata {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use SourceMetadata(null, null)
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            SourceMetadata(
                displayName = nameIndex.takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getString),
                sizeBytes = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getLong),
            )
        } ?: SourceMetadata(null, null)
    }

    private fun readBounded(stream: InputStream): ByteArray {
        val output = SensitiveByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE_BYTES)
        var total = 0L
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > DocumentImportPolicy.MAX_DOCUMENT_BYTES) {
                    throw DocumentImportException(
                        DocumentImportFailure.FILE_TOO_LARGE,
                        "Document exceeds the 25 MiB import limit.",
                    )
                }
                output.write(buffer, 0, read)
            }
            return output.takeBytes()
        } finally {
            buffer.fill(0)
            output.close()
        }
    }

    private fun detectMimeType(bytes: ByteArray): String? = when {
        bytes.startsWith(PDF_SIGNATURE) -> MIME_PDF
        bytes.startsWith(JPEG_SIGNATURE) -> MIME_JPEG
        bytes.startsWith(PNG_SIGNATURE) -> MIME_PNG
        bytes.size >= WEBP_MINIMUM_HEADER_BYTES &&
            bytes.sliceMatches(0, RIFF_SIGNATURE) &&
            bytes.sliceMatches(8, WEBP_SIGNATURE) -> MIME_WEBP
        else -> null
    }

    private fun normalizeMimeType(value: String?): String? = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotEmpty)
        ?.let { if (it == "image/jpg") MIME_JPEG else it }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = sliceMatches(0, prefix)

    private fun ByteArray.sliceMatches(offset: Int, expected: ByteArray): Boolean =
        size >= offset + expected.size && expected.indices.all { index -> this[offset + index] == expected[index] }

    private data class SourceMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )

    companion object {
        private const val BUFFER_SIZE_BYTES = 16 * 1024
        private const val WEBP_MINIMUM_HEADER_BYTES = 12
        private const val GENERIC_BINARY_MIME = "application/octet-stream"
        private const val MIME_PDF = "application/pdf"
        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_PNG = "image/png"
        private const val MIME_WEBP = "image/webp"

        private val SUPPORTED_MIME_TYPES = setOf(MIME_PDF, MIME_JPEG, MIME_PNG, MIME_WEBP)
        private val PDF_SIGNATURE = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2d)
        private val JPEG_SIGNATURE = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
        private val RIFF_SIGNATURE = byteArrayOf(0x52, 0x49, 0x46, 0x46)
        private val WEBP_SIGNATURE = byteArrayOf(0x57, 0x45, 0x42, 0x50)
    }
}
