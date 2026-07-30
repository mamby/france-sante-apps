package net.mamby.health.data

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal class LocalVaultLayout(noBackupFilesDir: File) {
    private val root = File(noBackupFilesDir, ROOT_DIRECTORY_NAME)
    private val generations = File(root, GENERATIONS_DIRECTORY_NAME)
    private val activePointer = File(root, ACTIVE_GENERATION_FILE_NAME)

    fun activeGeneration(): Generation? {
        val pointer = AtomicFile(activePointer)
        val value = try {
            pointer.openRead().use { input ->
                input.readBytes().toString(StandardCharsets.US_ASCII).trim()
            }
        } catch (error: FileNotFoundException) {
            return null
        }
        val id = runCatching { UUID.fromString(value) }
            .getOrElse { throw VaultCorruptionException("Active vault generation pointer is invalid.", it) }
        val directory = File(generations, id.toString())
        if (!directory.isDirectory) {
            throw VaultCorruptionException("Active vault generation is missing.")
        }
        return Generation(id, directory)
    }

    fun createGeneration(): Generation {
        generations.mkdirsOrThrow()
        val id = UUID.randomUUID()
        val directory = File(generations, id.toString())
        directory.mkdirsOrThrow()
        File(directory, BLOBS_DIRECTORY_NAME).mkdirsOrThrow()
        return Generation(id, directory)
    }

    fun activate(generation: Generation) {
        root.mkdirsOrThrow()
        writeAtomic(activePointer, generation.id.toString().toByteArray(StandardCharsets.US_ASCII))
    }

    fun metadataFile(generation: Generation): File = File(generation.directory, METADATA_FILE_NAME)

    fun blobsDirectory(generation: Generation): File =
        File(generation.directory, BLOBS_DIRECTORY_NAME).apply { mkdirsOrThrow() }

    fun stagingDirectory(generation: Generation): File =
        File(generation.directory, STAGING_DIRECTORY_NAME).apply { mkdirsOrThrow() }

    fun blobFile(generation: Generation, blobId: UUID): File =
        File(blobsDirectory(generation), "$blobId$BLOB_FILE_SUFFIX")

    fun stagedBlobFile(generation: Generation, token: UUID): File =
        File(stagingDirectory(generation), "$token$STAGED_FILE_SUFFIX")

    fun generation(id: UUID): Generation = Generation(id, File(generations, id.toString()))

    fun moveAtomically(source: File, destination: File) {
        destination.parentFile?.mkdirsOrThrow()
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirsOrThrow()
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun read(file: File): ByteArray = AtomicFile(file).openRead().use { it.readBytes() }

    fun removeGeneration(generation: Generation) {
        if (generation.directory.exists() && !generation.directory.deleteRecursively()) {
            throw VaultStorageException("Vault staging generation could not be removed.")
        }
    }

    fun cleanupInactive(activeGenerationId: UUID?) {
        if (!generations.exists()) return
        generations.listFiles().orEmpty().forEach { candidate ->
            if (candidate.name != activeGenerationId?.toString()) candidate.deleteRecursively()
        }
    }

    fun deleteAll() {
        if (root.exists() && !root.deleteRecursively()) {
            throw VaultStorageException("Local vault files could not be deleted.")
        }
    }

    data class Generation(val id: UUID, val directory: File)

    companion object {
        const val BLOB_FILE_SUFFIX = ".phvb"

        private const val ROOT_DIRECTORY_NAME = "health-vault"
        private const val GENERATIONS_DIRECTORY_NAME = "generations"
        private const val ACTIVE_GENERATION_FILE_NAME = "active-generation"
        private const val METADATA_FILE_NAME = "metadata.phv"
        private const val BLOBS_DIRECTORY_NAME = "blobs"
        private const val STAGING_DIRECTORY_NAME = ".staging"
        private const val STAGED_FILE_SUFFIX = ".part"
    }
}

private fun File.mkdirsOrThrow() {
    if (!isDirectory && !mkdirs()) throw VaultStorageException("Cannot create local vault directory.")
}
