package net.mamby.health.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PortableBackupContainer @Inject constructor(
    private val json: Json,
    private val cryptography: PortableBackupCryptography,
) {
    suspend fun write(
        target: File,
        header: BackupHeader,
        manifest: BackupManifest,
        backupKey: ByteArray,
        copyDocument: suspend (BackupDocumentEntry, OutputStream) -> Long,
    ) {
        require(manifest.documents.size <= PortableBackupFormat.MAX_DOCUMENT_COUNT) {
            "Backup contains too many documents"
        }
        target.outputStream().buffered().use { fileOutput ->
            ZipOutputStream(fileOutput).use { zip ->
                writePlaintextEntry(
                    zip = zip,
                    name = PortableBackupFormat.HEADER_ENTRY_NAME,
                    bytes = json.encodeToString(header).encodeToByteArray(),
                )
                writeEncryptedEntry(
                    zip = zip,
                    name = PortableBackupFormat.MANIFEST_ENTRY_NAME,
                    backupKey = backupKey,
                    associatedData = manifestAssociatedData(),
                ) { output ->
                    output.write(json.encodeToString(manifest).encodeToByteArray())
                }
                manifest.documents.forEach { document ->
                    require(document.sizeBytes in 0..PortableBackupFormat.MAX_DOCUMENT_BYTES) {
                        "Document exceeds the portable backup limit"
                    }
                    writeEncryptedEntry(
                        zip = zip,
                        name = document.entryName,
                        backupKey = backupKey,
                        associatedData = documentAssociatedData(document),
                    ) { output ->
                        val copied = copyDocument(document, output)
                        require(copied == document.sizeBytes) {
                            "Document changed while the backup was being created"
                        }
                    }
                }
            }
        }
    }

    fun readHeader(container: File): BackupHeader = ZipFile(container).use { zip ->
        val entry = zip.getEntry(PortableBackupFormat.HEADER_ENTRY_NAME)
            ?: throw BackupCorruptionException("Backup header is missing")
        if (entry.size !in 1..PortableBackupFormat.MAX_HEADER_BYTES.toLong()) {
            throw BackupCorruptionException("Backup header has an invalid size")
        }
        val bytes = zip.getInputStream(entry).use { input ->
            input.readBounded(PortableBackupFormat.MAX_HEADER_BYTES)
        }
        runCatching { json.decodeFromString<BackupHeader>(bytes.decodeToString()) }
            .getOrElse { throw BackupCorruptionException("Backup header is invalid", it) }
    }

    fun readManifest(
        container: File,
        backupKey: ByteArray,
    ): BackupManifest = ZipFile(container).use { zip ->
        val entry = zip.getEntry(PortableBackupFormat.MANIFEST_ENTRY_NAME)
            ?: throw BackupCorruptionException("Encrypted backup manifest is missing")
        val maximumEncryptedSize = PortableBackupFormat.MAX_MANIFEST_BYTES.toLong() +
            PortableBackupCryptography.NONCE_SIZE_BYTES +
            PortableBackupCryptography.TAG_SIZE_BYTES
        if (entry.size !in MINIMUM_ENCRYPTED_ENTRY_SIZE.toLong()..maximumEncryptedSize) {
            throw BackupCorruptionException("Encrypted backup manifest has an invalid size")
        }
        val output = BoundedByteArrayOutputStream(PortableBackupFormat.MAX_MANIFEST_BYTES)
        try {
            decryptEntry(
                input = zip.getInputStream(entry),
                output = output,
                backupKey = backupKey,
                associatedData = manifestAssociatedData(),
            )
        } catch (error: Exception) {
            output.wipe()
            throw error
        }
        val plaintext = output.toByteArray()
        output.wipe()
        try {
            runCatching { json.decodeFromString<BackupManifest>(plaintext.decodeToString()) }
                .getOrElse {
                    throw BackupCorruptionException("Encrypted backup manifest is invalid", it)
                }
        } finally {
            plaintext.fill(0)
        }
    }

    fun validateStructure(container: File, manifest: BackupManifest) {
        if (manifest.formatVersion != PortableBackupFormat.VERSION) {
            throw UnsupportedBackupException("Unsupported backup manifest version")
        }
        if (manifest.documents.size > PortableBackupFormat.MAX_DOCUMENT_COUNT) {
            throw BackupCorruptionException("Backup contains too many documents")
        }
        val indices = manifest.documents.map(BackupDocumentEntry::index)
        if (indices != indices.indices.toList() || indices.distinct().size != indices.size) {
            throw BackupCorruptionException("Backup document indices are invalid")
        }
        if (manifest.documents.map(BackupDocumentEntry::blobId).distinct().size !=
            manifest.documents.size
        ) {
            throw BackupCorruptionException("Backup contains duplicate document identifiers")
        }

        ZipFile(container).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val expectedNames = buildSet {
                add(PortableBackupFormat.HEADER_ENTRY_NAME)
                add(PortableBackupFormat.MANIFEST_ENTRY_NAME)
                manifest.documents.mapTo(this) { it.entryName }
            }
            val actualNames = entries.map(ZipEntry::getName)
            if (actualNames.size != expectedNames.size || actualNames.toSet() != expectedNames) {
                throw BackupCorruptionException("Backup has missing, duplicate, or unexpected entries")
            }
            if (entries.any(ZipEntry::isDirectory)) {
                throw BackupCorruptionException("Backup contains unexpected directories")
            }
            manifest.documents.forEach { document ->
                if (document.sizeBytes !in 0..PortableBackupFormat.MAX_DOCUMENT_BYTES) {
                    throw BackupCorruptionException("Backup document has an invalid size")
                }
                val encryptedEntry = zip.getEntry(document.entryName)
                    ?: throw BackupCorruptionException("Encrypted backup document is missing")
                val expectedEncryptedSize = document.sizeBytes + MINIMUM_ENCRYPTED_ENTRY_SIZE
                if (encryptedEntry.size != expectedEncryptedSize) {
                    throw BackupCorruptionException("Encrypted backup document has an invalid size")
                }
            }
        }
    }

    fun verifyDocuments(container: File, manifest: BackupManifest, backupKey: ByteArray) {
        ZipFile(container).use { zip ->
            manifest.documents.forEach { document ->
                val entry = zip.getEntry(document.entryName)
                    ?: throw BackupCorruptionException("Encrypted backup document is missing")
                val counter = CountingDiscardOutputStream()
                decryptEntry(
                    input = zip.getInputStream(entry),
                    output = counter,
                    backupKey = backupKey,
                    associatedData = documentAssociatedData(document),
                )
                if (counter.count != document.sizeBytes) {
                    throw BackupCorruptionException("Decrypted backup document has an invalid size")
                }
            }
        }
    }

    fun openVerifiedDocument(
        container: File,
        document: BackupDocumentEntry,
        backupKey: ByteArray,
    ): InputStream {
        val plaintext = ZipFile(container).use { zip ->
            val entry = zip.getEntry(document.entryName)
                ?: throw BackupCorruptionException("Encrypted backup document is missing")
            val output = BoundedByteArrayOutputStream(
                document.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
            try {
                decryptEntry(
                    input = zip.getInputStream(entry),
                    output = output,
                    backupKey = backupKey,
                    associatedData = documentAssociatedData(document),
                )
            } catch (error: Exception) {
                output.wipe()
                throw error
            }
            output.toByteArray().also { bytes ->
                output.wipe()
                if (bytes.size.toLong() != document.sizeBytes) {
                    bytes.fill(0)
                    throw BackupCorruptionException("Decrypted backup document has an invalid size")
                }
            }
        }
        return WipingByteArrayInputStream(plaintext)
    }

    private fun writePlaintextEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(newEntry(name))
        try {
            zip.write(bytes)
        } finally {
            bytes.fill(0)
            zip.closeEntry()
        }
    }

    private suspend fun writeEncryptedEntry(
        zip: ZipOutputStream,
        name: String,
        backupKey: ByteArray,
        associatedData: ByteArray,
        writePlaintext: suspend (OutputStream) -> Unit,
    ) {
        zip.putNextEntry(newEntry(name))
        try {
            val (nonce, cipher) = cryptography.newEncryptingCipher(backupKey, associatedData)
            zip.write(nonce)
            val encryptingOutput = FinishingCipherOutputStream(zip, cipher)
            writePlaintext(encryptingOutput)
            encryptingOutput.finish()
        } finally {
            zip.closeEntry()
        }
    }

    private fun decryptEntry(
        input: InputStream,
        output: OutputStream,
        backupKey: ByteArray,
        associatedData: ByteArray,
    ) {
        input.use {
            val nonce = it.readExact(PortableBackupCryptography.NONCE_SIZE_BYTES)
            val cipher = cryptography.newDecryptingCipher(backupKey, nonce, associatedData)
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            try {
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    cipher.update(buffer, 0, read)?.takeIf { bytes -> bytes.isNotEmpty() }
                        ?.let(output::write)
                }
                cipher.doFinal()?.takeIf(ByteArray::isNotEmpty)?.let(output::write)
            } catch (error: AEADBadTagException) {
                throw BackupCorruptionException("Encrypted backup entry failed authentication", error)
            } finally {
                buffer.fill(0)
            }
        }
    }

    private fun newEntry(name: String) = ZipEntry(name).apply { time = ZIP_EPOCH_MILLIS }

    private fun manifestAssociatedData(): ByteArray =
        "phvbackup:${PortableBackupFormat.VERSION}:manifest".encodeToByteArray()

    private fun documentAssociatedData(document: BackupDocumentEntry): ByteArray =
        "phvbackup:${PortableBackupFormat.VERSION}:blob:${document.index}:${document.blobId}"
            .encodeToByteArray()

    private companion object {
        const val STREAM_BUFFER_BYTES = 64 * 1024
        const val ZIP_EPOCH_MILLIS = 0L
        const val MINIMUM_ENCRYPTED_ENTRY_SIZE =
            PortableBackupCryptography.NONCE_SIZE_BYTES + PortableBackupCryptography.TAG_SIZE_BYTES
    }
}

private class FinishingCipherOutputStream(
    private val destination: OutputStream,
    private val cipher: Cipher,
) : OutputStream() {
    private var finished = false

    override fun write(value: Int) {
        write(byteArrayOf(value.toByte()))
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        check(!finished) { "Encrypted backup entry is already complete" }
        cipher.update(buffer, offset, length)?.takeIf(ByteArray::isNotEmpty)?.let(destination::write)
    }

    override fun flush() = destination.flush()

    override fun close() = finish()

    fun finish() {
        if (finished) return
        cipher.doFinal()?.takeIf(ByteArray::isNotEmpty)?.let(destination::write)
        finished = true
    }
}

private class BoundedByteArrayOutputStream(private val maximumBytes: Int) :
    ByteArrayOutputStream() {
    override fun write(value: Int) {
        ensureCapacityFor(1)
        super.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensureCapacityFor(length)
        super.write(buffer, offset, length)
    }

    private fun ensureCapacityFor(additionalBytes: Int) {
        if (count.toLong() + additionalBytes > maximumBytes) {
            throw BackupCorruptionException("Decrypted backup entry exceeds its limit")
        }
    }

    fun wipe() {
        buf.fill(0)
        reset()
    }
}

private class CountingDiscardOutputStream : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        count++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        count = Math.addExact(count, length.toLong())
    }
}

private class WipingByteArrayInputStream(private val bytes: ByteArray) : InputStream() {
    private var position = 0

    override fun read(): Int = if (position >= bytes.size) {
        -1
    } else {
        bytes[position++].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length, bytes.size - position)
        bytes.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override fun close() {
        bytes.fill(0)
        position = bytes.size
    }
}

private fun InputStream.readBounded(maximumBytes: Int): ByteArray {
    val output = BoundedByteArrayOutputStream(maximumBytes)
    val buffer = ByteArray(8 * 1024)
    try {
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    } finally {
        buffer.fill(0)
    }
}

private fun InputStream.readExact(size: Int): ByteArray {
    val bytes = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(bytes, offset, size - offset)
        if (read < 0) {
            bytes.fill(0)
            throw BackupCorruptionException("Encrypted backup entry is truncated")
        }
        offset += read
    }
    return bytes
}
