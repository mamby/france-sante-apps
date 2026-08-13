package net.mamby.health.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.asReference
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.crypto.AesGcmVaultCipher
import net.mamby.health.crypto.VaultKeyProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedVaultStoreInstrumentedTest {
    @Test
    fun partialReplacementFailure_keepsPreviousGenerationAndEncryptedBlobActive() = runBlocking {
        fixture().use { fixture ->
            val originalContent = "original encrypted document".encodeToByteArray()
            val originalDocument = document(
                id = ORIGINAL_DOCUMENT_ID,
                blobId = ORIGINAL_BLOB_ID,
                title = "Original",
                sizeBytes = originalContent.size.toLong(),
            )
            val originalVault = vault("Original owner", 1, listOf(originalDocument))
            fixture.store.replaceAtomically(
                originalVault,
                listOf(source(ORIGINAL_BLOB_ID, originalContent)),
            )
            val originalGeneration = requireNotNull(fixture.layout.activeGeneration())

            val firstReplacementContent = "first replacement document".encodeToByteArray()
            val secondReplacementContent = "second replacement document".encodeToByteArray()
            var invalidSourceOpened = false
            val replacementVault = vault(
                displayName = "Replacement owner",
                revision = 2,
                documents = listOf(
                    document(
                        id = FIRST_REPLACEMENT_DOCUMENT_ID,
                        blobId = FIRST_REPLACEMENT_BLOB_ID,
                        title = "First replacement",
                        sizeBytes = firstReplacementContent.size.toLong(),
                    ),
                    document(
                        id = SECOND_REPLACEMENT_DOCUMENT_ID,
                        blobId = SECOND_REPLACEMENT_BLOB_ID,
                        title = "Second replacement",
                        sizeBytes = secondReplacementContent.size.toLong(),
                    ),
                ),
            )

            try {
                fixture.store.replaceAtomically(
                    replacementVault,
                    listOf(
                        source(FIRST_REPLACEMENT_BLOB_ID, firstReplacementContent),
                        RestoreDocumentBlob(
                            blobId = SECOND_REPLACEMENT_BLOB_ID,
                            expectedSizeBytes = secondReplacementContent.size.toLong(),
                            openStream = {
                                invalidSourceOpened = true
                                ByteArrayInputStream(
                                    secondReplacementContent.copyOf(
                                        secondReplacementContent.size - 1,
                                    ),
                                )
                            },
                        ),
                    ),
                )
                fail("A truncated restore document must abort replacement")
            } catch (_: IllegalArgumentException) {
                // Expected: the new generation has already received its first encrypted blob.
            }

            assertTrue(invalidSourceOpened)
            assertEquals(originalGeneration.id, fixture.layout.activeGeneration()?.id)
            assertEquals(
                listOf(originalGeneration.id.toString()),
                originalGeneration.directory.parentFile
                    ?.listFiles()
                    .orEmpty()
                    .filter { it.isDirectory }
                    .map { it.name },
            )
            assertEquals(originalVault, fixture.store.load())
            assertBlobEquals(originalContent, fixture.blobStore.read(ORIGINAL_BLOB_ID))
            assertNull(fixture.blobStore.read(FIRST_REPLACEMENT_BLOB_ID))
            assertNull(fixture.blobStore.read(SECOND_REPLACEMENT_BLOB_ID))
        }
    }

    @Test
    fun loadAfterInterruptedReplacement_removesAbandonedGenerationAndKeepsActiveVault() =
        runBlocking {
            fixture().use { fixture ->
                val activeVault = vault(
                    displayName = "Active owner",
                    revision = 3,
                    documents = emptyList(),
                )
                fixture.store.save(activeVault)
                val activeGeneration = requireNotNull(fixture.layout.activeGeneration())
                val abandonedGeneration = fixture.layout.createGeneration()
                fixture.layout.writeAtomic(
                    fixture.layout.metadataFile(abandonedGeneration),
                    "partially written replacement".encodeToByteArray(),
                )
                assertTrue(abandonedGeneration.directory.isDirectory)

                assertEquals(activeVault, fixture.store.load())

                assertEquals(activeGeneration.id, fixture.layout.activeGeneration()?.id)
                assertTrue(activeGeneration.directory.isDirectory)
                assertFalse(abandonedGeneration.directory.exists())
            }
        }

    @Test
    fun authenticatedReplacement_recoversUnreadableVaultWithoutMutatingItDuringFailedLoad() =
        runBlocking {
            fixture().use { fixture ->
                val originalContent = "unreadable vault document".encodeToByteArray()
                val originalVault = vault(
                    displayName = "Unreadable owner",
                    revision = 7,
                    documents = listOf(
                        document(
                            id = ORIGINAL_DOCUMENT_ID,
                            blobId = ORIGINAL_BLOB_ID,
                            title = "Unreadable",
                            sizeBytes = originalContent.size.toLong(),
                        ),
                    ),
                )
                fixture.store.replaceAtomically(
                    originalVault,
                    listOf(source(ORIGINAL_BLOB_ID, originalContent)),
                )
                val unreadableGeneration = requireNotNull(fixture.layout.activeGeneration())
                fixture.layout.writeAtomic(
                    fixture.layout.metadataFile(unreadableGeneration),
                    byteArrayOf(0x01, 0x02, 0x03),
                )

                try {
                    fixture.store.load()
                    fail("Malformed encrypted metadata must be reported as vault corruption")
                } catch (_: VaultCorruptionException) {
                    // Expected.
                }

                assertEquals(unreadableGeneration.id, fixture.layout.activeGeneration()?.id)
                assertTrue(unreadableGeneration.directory.isDirectory)
                assertTrue(fixture.layout.blobFile(unreadableGeneration, ORIGINAL_BLOB_ID).isFile)

                val recoveredContent = "recovered encrypted document".encodeToByteArray()
                val recoveredVault = vault(
                    displayName = "Recovered owner",
                    revision = 8,
                    documents = listOf(
                        document(
                            id = RECOVERED_DOCUMENT_ID,
                            blobId = RECOVERED_BLOB_ID,
                            title = "Recovered",
                            sizeBytes = recoveredContent.size.toLong(),
                        ),
                    ),
                )
                fixture.store.replaceAtomically(
                    recoveredVault,
                    listOf(source(RECOVERED_BLOB_ID, recoveredContent)),
                )

                val recoveredGeneration = requireNotNull(fixture.layout.activeGeneration())
                assertNotEquals(unreadableGeneration.id, recoveredGeneration.id)
                assertFalse(unreadableGeneration.directory.exists())
                assertEquals(recoveredVault, fixture.store.load())
                assertNull(fixture.blobStore.read(ORIGINAL_BLOB_ID))
                assertBlobEquals(recoveredContent, fixture.blobStore.read(RECOVERED_BLOB_ID))
            }
        }

    private fun fixture(): Fixture {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(
            baseContext.cacheDir,
            "encrypted-vault-store-${UUID.randomUUID()}",
        ).apply {
            check(mkdirs()) { "Could not create isolated vault test directory" }
        }
        val context = IsolatedStorageContext(baseContext, directory)
        val keyProvider = FixedVaultKeyProvider()
        val cipher = AesGcmVaultCipher()
        return Fixture(
            directory = directory,
            store = EncryptedVaultStore(context, keyProvider, cipher),
            blobStore = EncryptedDocumentBlobStore(context, keyProvider, cipher),
            layout = LocalVaultLayout(directory),
        )
    }

    private fun vault(
        displayName: String,
        revision: Long,
        documents: List<MedicalDocument>,
    ): HealthVault = HealthVault.withProfile(
        now = NOW,
        profileId = PROFILE_ID,
        displayName = displayName,
    ).copy(
        revision = revision,
        profiles = listOf(
            HealthVault.withProfile(NOW, PROFILE_ID, displayName).profiles.single().copy(documents = documents),
        ),
        updatedAt = NOW,
    )

    private fun document(
        id: UUID,
        blobId: UUID,
        title: String,
        sizeBytes: Long,
    ) = MedicalDocument(
        id = id,
        title = title,
        category = BuiltInDocumentCategory.REPORTS.asReference(),
        documentDate = LocalDate.of(2026, 7, 30),
        source = "Test clinic",
        blobId = blobId,
        mimeType = "application/pdf",
        sizeBytes = sizeBytes,
        originalFileName = "$title.pdf",
        updatedAt = NOW,
    )

    private fun source(
        blobId: UUID,
        content: ByteArray,
        expectedSizeBytes: Long = content.size.toLong(),
    ) = RestoreDocumentBlob(
        blobId = blobId,
        expectedSizeBytes = expectedSizeBytes,
        openStream = { ByteArrayInputStream(content.copyOf()) },
    )

    private fun assertBlobEquals(expected: ByteArray, actual: ByteArray?) {
        val plaintext = requireNotNull(actual)
        try {
            assertArrayEquals(expected, plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private class Fixture(
        private val directory: File,
        val store: EncryptedVaultStore,
        val blobStore: EncryptedDocumentBlobStore,
        val layout: LocalVaultLayout,
    ) : Closeable {
        override fun close() {
            directory.deleteRecursively()
        }
    }

    private class IsolatedStorageContext(
        baseContext: Context,
        private val directory: File,
    ) : ContextWrapper(baseContext) {
        override fun getNoBackupFilesDir(): File = directory
    }

    private class FixedVaultKeyProvider : VaultKeyProvider {
        private val key: SecretKey = SecretKeySpec(
            ByteArray(AesGcmVaultCipher.KEY_SIZE_BYTES) { index -> (index + 1).toByte() },
            "AES",
        )

        override suspend fun getOrCreateKey(): SecretKey = key

        override suspend fun deleteKey() = Unit
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-30T10:00:00Z")
        val PROFILE_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val ORIGINAL_DOCUMENT_ID: UUID =
            UUID.fromString("20000000-0000-0000-0000-000000000001")
        val ORIGINAL_BLOB_ID: UUID =
            UUID.fromString("30000000-0000-0000-0000-000000000001")
        val FIRST_REPLACEMENT_DOCUMENT_ID: UUID =
            UUID.fromString("20000000-0000-0000-0000-000000000002")
        val FIRST_REPLACEMENT_BLOB_ID: UUID =
            UUID.fromString("30000000-0000-0000-0000-000000000002")
        val SECOND_REPLACEMENT_DOCUMENT_ID: UUID =
            UUID.fromString("20000000-0000-0000-0000-000000000003")
        val SECOND_REPLACEMENT_BLOB_ID: UUID =
            UUID.fromString("30000000-0000-0000-0000-000000000003")
        val RECOVERED_DOCUMENT_ID: UUID =
            UUID.fromString("20000000-0000-0000-0000-000000000004")
        val RECOVERED_BLOB_ID: UUID =
            UUID.fromString("30000000-0000-0000-0000-000000000004")
    }
}
