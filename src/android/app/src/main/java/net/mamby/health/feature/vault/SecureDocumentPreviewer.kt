package net.mamby.health.feature.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.data.VaultRepository

data class RenderedDocumentPage(
    val image: ImageBitmap,
    val page: Int,
    val pageCount: Int,
)

@Singleton
class SecureDocumentPreviewer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
) {
    suspend fun render(
        profileId: UUID,
        document: MedicalDocument,
        requestedPage: Int,
    ): RenderedDocumentPage =
        withContext(Dispatchers.IO) {
            val bytes = vaultRepository.readDocumentBlob(profileId, document.blobId)
                ?: throw IOException("Encrypted document content is unavailable.")
            try {
                when (document.mimeType) {
                    MIME_PDF -> renderPdf(bytes, requestedPage)
                    MIME_JPEG, MIME_PNG, MIME_WEBP -> renderImage(bytes)
                    else -> throw IOException("Unsupported preview format.")
                }
            } finally {
                bytes.fill(0)
            }
        }

    private fun renderImage(bytes: ByteArray): RenderedDocumentPage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image document.")
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (pixels > MAX_SOURCE_PIXELS) throw IOException("Image dimensions are too large to preview safely.")

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_PREVIEW_EDGE || bounds.outHeight / sampleSize > MAX_PREVIEW_EDGE) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IOException("The image could not be decoded.")
        return RenderedDocumentPage(bitmap.asImageBitmap(), page = 0, pageCount = 1)
    }

    private fun renderPdf(bytes: ByteArray, requestedPage: Int): RenderedDocumentPage {
        val storageManager = context.getSystemService(StorageManager::class.java)
            ?: throw IOException("In-memory PDF preview is unavailable.")
        val callbackThread = HandlerThread("health-vault-pdf-preview").apply { start() }
        try {
            val descriptor = storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                ByteArrayProxyCallback(bytes),
                Handler(callbackThread.looper),
            )
            descriptor.use { parcelFileDescriptor ->
                PdfRenderer(parcelFileDescriptor).use { renderer ->
                    if (renderer.pageCount == 0) throw IOException("The PDF contains no pages.")
                    val pageIndex = requestedPage.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(pageIndex).use { page ->
                        val scale = (MAX_PREVIEW_EDGE.toFloat() / page.width.coerceAtLeast(page.height))
                            .coerceAtMost(1f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return RenderedDocumentPage(bitmap.asImageBitmap(), pageIndex, renderer.pageCount)
                    }
                }
            }
        } finally {
            callbackThread.quitSafely()
        }
    }

    private class ByteArrayProxyCallback(private val content: ByteArray) : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long = content.size.toLong()

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            if (offset >= content.size) return 0
            val count = minOf(size, content.size - offset.toInt())
            content.copyInto(data, destinationOffset = 0, startIndex = offset.toInt(), endIndex = offset.toInt() + count)
            return count
        }

        override fun onRelease() = Unit
    }

    companion object {
        private const val MIME_PDF = "application/pdf"
        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_PNG = "image/png"
        private const val MIME_WEBP = "image/webp"
        private const val MAX_PREVIEW_EDGE = 2_048
        private const val MAX_SOURCE_PIXELS = 40_000_000L
    }
}
