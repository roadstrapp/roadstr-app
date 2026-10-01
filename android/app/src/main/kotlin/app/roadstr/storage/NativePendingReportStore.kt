package app.roadstr.storage

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrPendingReportQueue
import app.roadstr.core.protocol.nostr.PendingReportFlushResult
import app.roadstr.core.protocol.nostr.PendingRoadReport
import app.roadstr.migration.LegacySnapshotLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val NATIVE_PENDING_REPORT_FILE = "pending_reports_v1.bin"
internal const val NATIVE_PENDING_REPORT_FILE_MAX_BYTES =
    LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES + 100_000 * Int.SIZE_BYTES + 256

class NativePendingReportStoreException(message: String) : IllegalStateException(message)

class NativePendingReportRecord(
    val schemaVersion: Int,
    val sequence: Long,
    storageRows: List<String>,
) {
    val storageRows: List<String> = Collections.unmodifiableList(storageRows.toList())

    override fun equals(other: Any?): Boolean =
        other is NativePendingReportRecord &&
            schemaVersion == other.schemaVersion &&
            sequence == other.sequence &&
            storageRows == other.storageRows

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + sequence.hashCode()
        result = 31 * result + storageRows.hashCode()
        return result
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_ROWS = 100_000
    }
}

/** Canonical integrity framing for the encrypted FIFO queue. */
object NativePendingReportRecordCodec {
    private val MAGIC = "RSTRPND1".toByteArray(Charsets.US_ASCII)
    private const val DIGEST_BYTES = 32
    const val MAX_PLAINTEXT_BYTES = NATIVE_PENDING_REPORT_FILE_MAX_BYTES - 128

    fun encode(record: NativePendingReportRecord): ByteArray {
        validate(record)
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(record.schemaVersion)
                output.writeLong(record.sequence)
                output.writeInt(record.storageRows.size)
                for (row in record.storageRows) output.writeText(row)
            }
            bytes.toByteArray()
        }
        val framed = payload + MessageDigest.getInstance("SHA-256").digest(payload)
        if (framed.size > MAX_PLAINTEXT_BYTES) invalid()
        return framed
    }

    fun decode(encoded: ByteArray): NativePendingReportRecord {
        if (encoded.size <= MAGIC.size + DIGEST_BYTES || encoded.size > MAX_PLAINTEXT_BYTES) {
            invalid()
        }
        val payload = encoded.copyOfRange(0, encoded.size - DIGEST_BYTES)
        val expected = encoded.copyOfRange(encoded.size - DIGEST_BYTES, encoded.size)
        if (!MessageDigest.isEqual(
                expected,
                MessageDigest.getInstance("SHA-256").digest(payload),
            )
        ) {
            invalid()
        }
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                if (!MessageDigest.isEqual(MAGIC, magic)) invalid()
                val schemaVersion = input.readInt()
                val sequence = input.readLong()
                val count = input.readInt()
                if (count !in 0..NativePendingReportRecord.MAX_ROWS) invalid()
                val rows = ArrayList<String>(count)
                repeat(count) { rows += input.readText() }
                if (input.available() != 0) invalid()
                NativePendingReportRecord(schemaVersion, sequence, rows).also(::validate)
            }
        } catch (failure: NativePendingReportStoreException) {
            throw failure
        } catch (_: Exception) {
            invalid()
        }
    }

    private fun validate(record: NativePendingReportRecord) {
        if (record.schemaVersion != NativePendingReportRecord.CURRENT_SCHEMA_VERSION ||
            record.sequence < 0 ||
            record.storageRows.size > NativePendingReportRecord.MAX_ROWS
        ) {
            invalid()
        }
        var bytes = MAGIC.size + Int.SIZE_BYTES * 2 + Long.SIZE_BYTES + DIGEST_BYTES
        for (row in record.storageRows) {
            val rowBytes = row.toByteArray(Charsets.UTF_8)
            if (rowBytes.isEmpty() ||
                rowBytes.size > LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES ||
                NativePendingReportRows.decodeRow(row) == null
            ) {
                invalid()
            }
            bytes += Int.SIZE_BYTES + rowBytes.size
            if (bytes > MAX_PLAINTEXT_BYTES) invalid()
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String {
        val size = readInt()
        if (size !in 1..LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES || size > available()) {
            invalid()
        }
        val bytes = ByteArray(size).also(::readFully)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun invalid(): Nothing =
        throw NativePendingReportStoreException("Native pending-report value is invalid")
}

internal object NativePendingReportRows {
    fun fromLegacy(raw: String?): List<String> {
        if (raw == null) return emptyList()
        if (raw.toByteArray(Charsets.UTF_8).size > LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES) {
            invalid()
        }
        val values = try {
            BoundedJsonParser(raw).parse() as? List<*>
        } catch (_: RuntimeException) {
            null
        } ?: invalid()
        if (values.size > NativePendingReportRecord.MAX_ROWS) invalid()
        return Collections.unmodifiableList(
            values.asSequence()
                .filterIsInstance<String>()
                .filter { decodeRow(it) != null }
                .toList(),
        )
    }

    fun decode(rows: List<String>): List<PendingRoadReport> =
        Collections.unmodifiableList(rows.mapNotNull(::decodeRow))

    fun decodeRow(raw: String): PendingRoadReport? {
        val value = try {
            BoundedJsonParser(raw).parse() as? Map<*, *>
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val event = (value["event"] as? Map<*, *>)?.entries?.associate { (key, nested) ->
            (key as? String ?: return null) to nested
        }
        val expiresAt = value["expiresAt"] as? Long
        return try {
            PendingRoadReport(event, expiresAt)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun invalid(): Nothing =
        throw NativePendingReportStoreException("Legacy pending-report value is invalid")
}

internal object NativePendingReportEncryptedEnvelope {
    private val MAGIC = "RSTRPQG1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    private const val HEADER_BYTES = 20
    val associatedData = "roadstr-native-pending-reports-v1".toByteArray(Charsets.US_ASCII)

    fun encode(sealed: NativeSecretSealedData): ByteArray {
        if (sealed.nonce.size != NATIVE_SECRET_GCM_NONCE_BYTES ||
            sealed.ciphertext.size !in NATIVE_SECRET_GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES
        ) {
            invalid()
        }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeInt(sealed.nonce.size)
                output.writeInt(sealed.ciphertext.size)
                output.write(sealed.nonce)
                output.write(sealed.ciphertext)
            }
            bytes.toByteArray()
        }
    }

    fun decode(encoded: ByteArray): NativeSecretSealedData {
        if (encoded.size !in MIN_FILE_BYTES..NATIVE_PENDING_REPORT_FILE_MAX_BYTES) invalid()
        return try {
            val buffer = ByteBuffer.wrap(encoded)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            if (!MessageDigest.isEqual(MAGIC, magic) || buffer.int != FORMAT_VERSION) invalid()
            val nonceSize = buffer.int
            val ciphertextSize = buffer.int
            if (nonceSize != NATIVE_SECRET_GCM_NONCE_BYTES ||
                ciphertextSize !in NATIVE_SECRET_GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES ||
                nonceSize + ciphertextSize != buffer.remaining()
            ) {
                invalid()
            }
            NativeSecretSealedData(
                ByteArray(nonceSize).also(buffer::get),
                ByteArray(ciphertextSize).also(buffer::get),
            )
        } catch (failure: NativePendingReportStoreException) {
            throw failure
        } catch (_: RuntimeException) {
            invalid()
        }
    }

    private fun invalid(): Nothing =
        throw NativePendingReportStoreException("Native pending-report envelope is invalid")

    private const val MIN_FILE_BYTES = HEADER_BYTES +
        NATIVE_SECRET_GCM_NONCE_BYTES + NATIVE_SECRET_GCM_TAG_BYTES
    private const val MAX_CIPHERTEXT_BYTES = NATIVE_PENDING_REPORT_FILE_MAX_BYTES -
        HEADER_BYTES - NATIVE_SECRET_GCM_NONCE_BYTES
}

internal class NativePendingReportPersistenceCodec(private val aead: NativeSecretAead) {
    fun encode(record: NativePendingReportRecord): ByteArray {
        val plaintext = NativePendingReportRecordCodec.encode(record)
        return try {
            NativePendingReportEncryptedEnvelope.encode(
                aead.seal(plaintext, NativePendingReportEncryptedEnvelope.associatedData),
            )
        } catch (failure: NativePendingReportStoreException) {
            throw failure
        } catch (_: Exception) {
            throw NativePendingReportStoreException("Native pending-report encryption failed")
        } finally {
            plaintext.fill(0)
        }
    }

    fun decode(encoded: ByteArray): NativePendingReportRecord {
        val sealed = NativePendingReportEncryptedEnvelope.decode(encoded)
        val plaintext = try {
            aead.open(sealed, NativePendingReportEncryptedEnvelope.associatedData)
        } catch (_: Exception) {
            throw NativePendingReportStoreException("Native pending-report decryption failed")
        }
        return try {
            NativePendingReportRecordCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }
}

enum class NativePendingReportInitializationOutcome { Initialized, AlreadyPresent }

/** Encrypted single-flight owner for signed reports awaiting relay publication. */
class FileNativePendingReportStore internal constructor(
    directory: File,
    aead: NativeSecretAead,
    private val ioDispatcher: CoroutineDispatcher,
) : NativePendingReportMigrationStore {
    constructor(
        paths: NativeStoragePaths,
        keyAlias: String = DEFAULT_KEY_ALIAS,
    ) : this(paths.rootDirectory, AndroidKeystoreAead(keyAlias), Dispatchers.IO)

    private val operationLock = Any()
    private var acceptedUiRevision = -1L
    private val codec = NativePendingReportPersistenceCodec(aead)
    private val file = RecoverableAtomicFile(
        directory,
        NATIVE_PENDING_REPORT_FILE,
        NATIVE_PENDING_REPORT_FILE_MAX_BYTES,
        codec::decode,
    )

    suspend fun load(): List<PendingRoadReport> = withContext(ioDispatcher) {
        synchronized(operationLock) {
            NativePendingReportRows.decode(readRequired().storageRows)
        }
    }

    suspend fun initializeEmptyForNewInstall(): NativePendingReportInitializationOutcome =
        withContext(ioDispatcher) {
            synchronized(operationLock) {
                if (readOptional() != null) {
                    return@synchronized NativePendingReportInitializationOutcome.AlreadyPresent
                }
                persist(emptyRecord())
                NativePendingReportInitializationOutcome.Initialized
            }
        }

    suspend fun enqueue(
        revision: Long,
        event: Map<String, Any?>,
        expiresAt: Long,
    ): Boolean = mutate(revision) { current ->
        val row = PendingRoadReport(event, expiresAt).storageJson()
        if (current.storageRows.size >= NativePendingReportRecord.MAX_ROWS ||
            current.storageRows.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } +
            row.toByteArray(Charsets.UTF_8).size > LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES
        ) {
            throw NativePendingReportStoreException("Native pending-report queue is full")
        }
        current.next(current.storageRows + row)
    }

    suspend fun flush(
        revision: Long,
        now: Long,
        verify: (Map<String, Any?>) -> Boolean,
        publish: (Map<String, Any?>) -> Boolean,
    ): PendingReportFlushResult = withContext(ioDispatcher) {
        synchronized(operationLock) {
            require(revision >= 0) { "Pending-report revision must be non-negative" }
            if (revision < acceptedUiRevision) {
                throw NativePendingReportStoreException("Pending-report revision is stale")
            }
            val current = readRequired()
            val result = NostrPendingReportQueue.flush(
                NativePendingReportRows.decode(current.storageRows),
                now,
                verify,
                publish,
            )
            val remaining = result.remaining.map(PendingRoadReport::storageJson)
            if (remaining != current.storageRows) persist(current.next(remaining))
            acceptedUiRevision = revision
            result
        }
    }

    override fun stageLegacy(normalizedLegacyValue: String?): Unit = synchronized(operationLock) {
        guard("Native pending-report staging failed") {
            file.stage(
                codec.encode(
                    NativePendingReportRecord(
                        NativePendingReportRecord.CURRENT_SCHEMA_VERSION,
                        0,
                        NativePendingReportRows.fromLegacy(normalizedLegacyValue),
                    ),
                ),
            )
            Unit
        }
    }

    override fun commitStaged() = synchronized(operationLock) {
        guard("Native pending-report commit failed") { file.commit() }
    }

    override fun verifyLegacy(normalizedLegacyValue: String?) = synchronized(operationLock) {
        val expected = NativePendingReportRecord(
            NativePendingReportRecord.CURRENT_SCHEMA_VERSION,
            0,
            NativePendingReportRows.fromLegacy(normalizedLegacyValue),
        )
        if (readOptional() != expected) {
            throw NativePendingReportStoreException("Native pending-report verification failed")
        }
    }

    override fun committedCiphertextDigest(): ByteArray? = synchronized(operationLock) {
        try {
            val encrypted = file.read() ?: return@synchronized null
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(CIPHERTEXT_DIGEST_DOMAIN)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(encrypted.size).array())
            digest.update(encrypted)
            digest.digest()
        } catch (_: RuntimeException) {
            null
        }
    }

    private suspend fun mutate(
        revision: Long,
        transform: (NativePendingReportRecord) -> NativePendingReportRecord,
    ): Boolean = withContext(ioDispatcher) {
        synchronized(operationLock) {
            require(revision >= 0) { "Pending-report revision must be non-negative" }
            if (revision < acceptedUiRevision) return@synchronized false
            val current = readRequired()
            persist(transform(current))
            acceptedUiRevision = revision
            true
        }
    }

    private fun persist(record: NativePendingReportRecord) {
        guard("Native pending-report write failed") {
            file.stage(codec.encode(record))
            file.commit()
            if (readOptional() != record) {
                throw NativePendingReportStoreException("Native pending-report reopen verification failed")
            }
        }
    }

    private fun readRequired(): NativePendingReportRecord = readOptional()
        ?: throw NativePendingReportStoreException("Native pending-report store is not initialized")

    private fun readOptional(): NativePendingReportRecord? =
        guard("Native pending-report read failed") { file.read()?.let(codec::decode) }

    private fun emptyRecord() = NativePendingReportRecord(
        NativePendingReportRecord.CURRENT_SCHEMA_VERSION,
        0,
        emptyList(),
    )

    private fun NativePendingReportRecord.next(rows: List<String>) =
        NativePendingReportRecord(schemaVersion, sequence + 1, rows)

    private inline fun <T> guard(message: String, block: () -> T): T = try {
        block()
    } catch (failure: NativePendingReportStoreException) {
        throw failure
    } catch (_: RuntimeException) {
        throw NativePendingReportStoreException(message)
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "app.roadstr.native.pending-reports.v1"
        private val CIPHERTEXT_DIGEST_DOMAIN =
            "roadstr-native-pending-reports-ciphertext-v1".toByteArray(Charsets.US_ASCII)
    }
}
