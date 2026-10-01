package app.roadstr.storage

import app.roadstr.feature.activity.NativeActivityCursorKind
import app.roadstr.feature.activity.NativeActivityCursorProtocol
import app.roadstr.feature.activity.NativeActivityInboxProtocol
import app.roadstr.feature.activity.NativeActivityNotification
import app.roadstr.migration.LegacySnapshotLimits
import app.roadstr.migration.LegacyStorageContract
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.TreeMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val NATIVE_ACTIVITY_FILE = "activity_state_v1.bin"
internal const val NATIVE_ACTIVITY_FILE_MAX_BYTES = LegacySnapshotLimits.MAX_ENVELOPE_BYTES + 128

class NativeActivityStoreException(message: String) : IllegalStateException(message)

class NativeActivityIdentityState(
    val pubkey: String,
    items: List<NativeActivityNotification>,
    val zapCursorSeconds: Long?,
    val confirmationCursorSeconds: Long?,
) {
    val items: List<NativeActivityNotification> = Collections.unmodifiableList(items.toList())

    override fun equals(other: Any?): Boolean =
        other is NativeActivityIdentityState &&
            pubkey == other.pubkey &&
            items == other.items &&
            zapCursorSeconds == other.zapCursorSeconds &&
            confirmationCursorSeconds == other.confirmationCursorSeconds

    override fun hashCode(): Int {
        var result = pubkey.hashCode()
        result = 31 * result + items.hashCode()
        result = 31 * result + (zapCursorSeconds?.hashCode() ?: 0)
        result = 31 * result + (confirmationCursorSeconds?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "NativeActivityIdentityState(pubkey=$pubkey, items=${items.size}, " +
            "hasZapCursor=${zapCursorSeconds != null}, " +
            "hasConfirmationCursor=${confirmationCursorSeconds != null})"
}

class NativeActivityRecord(
    val schemaVersion: Int,
    val sequence: Long,
    identities: Map<String, NativeActivityIdentityState>,
) {
    val identities: Map<String, NativeActivityIdentityState> =
        Collections.unmodifiableMap(TreeMap(identities))

    override fun equals(other: Any?): Boolean =
        other is NativeActivityRecord &&
            schemaVersion == other.schemaVersion &&
            sequence == other.sequence &&
            identities == other.identities

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + sequence.hashCode()
        result = 31 * result + identities.hashCode()
        return result
    }

    override fun toString(): String =
        "NativeActivityRecord(schemaVersion=$schemaVersion, sequence=$sequence, " +
            "identities=${identities.size})"

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Canonical integrity framing inside the encrypted multi-identity activity file. */
object NativeActivityRecordCodec {
    private val MAGIC = "RSTRACT1".toByteArray(Charsets.US_ASCII)
    private const val DIGEST_BYTES = 32
    private const val PUBKEY_BYTES = 64
    const val MAX_IDENTITIES = LegacySnapshotLimits.MAX_ENTRIES_PER_STORE
    const val MAX_PLAINTEXT_BYTES = LegacySnapshotLimits.MAX_ENVELOPE_BYTES

    fun encode(record: NativeActivityRecord): ByteArray {
        validateRecord(record)
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(record.schemaVersion)
                output.writeLong(record.sequence)
                output.writeInt(record.identities.size)
                for ((pubkey, state) in record.identities) {
                    output.write(pubkey.toByteArray(Charsets.US_ASCII))
                    output.writeText(canonicalInbox(state.items))
                    output.writeOptionalCursor(state.zapCursorSeconds)
                    output.writeOptionalCursor(state.confirmationCursorSeconds)
                }
            }
            bytes.toByteArray()
        }
        val framed = payload + MessageDigest.getInstance("SHA-256").digest(payload)
        if (framed.size > MAX_PLAINTEXT_BYTES) invalid()
        return framed
    }

    fun decode(encoded: ByteArray): NativeActivityRecord {
        if (encoded.size <= MAGIC.size + DIGEST_BYTES || encoded.size > MAX_PLAINTEXT_BYTES) {
            invalid()
        }
        val payload = encoded.copyOfRange(0, encoded.size - DIGEST_BYTES)
        val expected = encoded.copyOfRange(encoded.size - DIGEST_BYTES, encoded.size)
        val actual = MessageDigest.getInstance("SHA-256").digest(payload)
        if (!MessageDigest.isEqual(expected, actual)) invalid()
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                if (!MessageDigest.isEqual(MAGIC, magic)) invalid()
                val schemaVersion = input.readInt()
                val sequence = input.readLong()
                val count = input.readInt()
                if (count !in 0..MAX_IDENTITIES) invalid()
                val identities = TreeMap<String, NativeActivityIdentityState>()
                repeat(count) {
                    val pubkeyBytes = ByteArray(PUBKEY_BYTES).also(input::readFully)
                    val pubkey = pubkeyBytes.toString(Charsets.US_ASCII)
                    val inbox = input.readText(NativeActivityInboxProtocol.MAX_NORMALIZED_BYTES)
                    val items = NativeActivityInboxProtocol.decodeNormalized(inbox)
                    if (canonicalInbox(items) != inbox) invalid()
                    val state = NativeActivityIdentityState(
                        pubkey = pubkey,
                        items = items,
                        zapCursorSeconds = input.readOptionalCursor(),
                        confirmationCursorSeconds = input.readOptionalCursor(),
                    )
                    validateState(pubkey, state)
                    if (identities.put(pubkey, state) != null) invalid()
                }
                if (input.available() != 0) invalid()
                NativeActivityRecord(schemaVersion, sequence, identities)
                    .also(::validateRecord)
            }
        } catch (failure: NativeActivityStoreException) {
            throw failure
        } catch (_: Exception) {
            invalid()
        }
    }

    private fun validateRecord(record: NativeActivityRecord) {
        if (record.schemaVersion != NativeActivityRecord.CURRENT_SCHEMA_VERSION ||
            record.sequence < 0 ||
            record.identities.size > MAX_IDENTITIES
        ) {
            invalid()
        }
        var estimatedBytes = MAGIC.size + Int.SIZE_BYTES * 2 + Long.SIZE_BYTES + DIGEST_BYTES
        for ((pubkey, state) in record.identities) {
            validateState(pubkey, state)
            estimatedBytes += PUBKEY_BYTES + Int.SIZE_BYTES +
                canonicalInbox(state.items).toByteArray(Charsets.UTF_8).size +
                2 * (1 + Long.SIZE_BYTES)
            if (estimatedBytes > MAX_PLAINTEXT_BYTES) invalid()
        }
    }

    private fun validateState(pubkey: String, state: NativeActivityIdentityState) {
        try {
            if (state.pubkey != pubkey || pubkey.length != PUBKEY_BYTES) invalid()
            NativeActivityInboxProtocol.storageKey(pubkey)
        } catch (_: IllegalArgumentException) {
            invalid()
        }
        val inbox = canonicalInbox(state.items)
        if (NativeActivityInboxProtocol.decodeNormalized(inbox) != state.items) invalid()
        validateCursor(state.zapCursorSeconds)
        validateCursor(state.confirmationCursorSeconds)
    }

    private fun validateCursor(value: Long?) {
        if (value != null && value !in 0..NativeActivityInboxProtocol.MAX_TIMESTAMP_SECONDS) {
            invalid()
        }
    }

    private fun canonicalInbox(items: List<NativeActivityNotification>): String {
        if (items.size > NativeActivityInboxProtocol.MAX_ENTRIES) invalid()
        val encoded = NativeActivityInboxProtocol.encodeNormalized(items)
        if (encoded.toByteArray(Charsets.UTF_8).size > NativeActivityInboxProtocol.MAX_NORMALIZED_BYTES) {
            invalid()
        }
        return encoded
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(limit: Int): String {
        val size = readInt()
        if (size !in 0..limit || size > available()) invalid()
        val bytes = ByteArray(size).also(::readFully)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun DataOutputStream.writeOptionalCursor(value: Long?) {
        writeByte(if (value == null) 0 else 1)
        value?.let(::writeLong)
    }

    private fun DataInputStream.readOptionalCursor(): Long? = when (readUnsignedByte()) {
        0 -> null
        1 -> readLong().also(::validateCursor)
        else -> invalid()
    }

    private fun invalid(): Nothing =
        throw NativeActivityStoreException("Native activity value is invalid")
}

/** Large AES-GCM envelope dedicated to private activity rows and cursors. */
internal object NativeActivityEncryptedEnvelope {
    private val MAGIC = "RSTRACG1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    private const val HEADER_BYTES = 20
    val associatedData = "roadstr-native-activity-v1".toByteArray(Charsets.US_ASCII)

    fun encode(sealed: NativeSecretSealedData): ByteArray {
        validate(sealed)
        return ByteArrayOutputStream(
            HEADER_BYTES + sealed.nonce.size + sealed.ciphertext.size,
        ).use { bytes ->
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
        if (encoded.size !in MIN_FILE_BYTES..NATIVE_ACTIVITY_FILE_MAX_BYTES) invalid()
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
                nonce = ByteArray(nonceSize).also(buffer::get),
                ciphertext = ByteArray(ciphertextSize).also(buffer::get),
            ).also(::validate)
        } catch (failure: NativeActivityStoreException) {
            throw failure
        } catch (_: RuntimeException) {
            invalid()
        }
    }

    private fun validate(sealed: NativeSecretSealedData) {
        if (sealed.nonce.size != NATIVE_SECRET_GCM_NONCE_BYTES ||
            sealed.ciphertext.size !in NATIVE_SECRET_GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES
        ) {
            invalid()
        }
    }

    private fun invalid(): Nothing =
        throw NativeActivityStoreException("Native activity envelope is invalid")

    private const val MIN_FILE_BYTES = HEADER_BYTES +
        NATIVE_SECRET_GCM_NONCE_BYTES + NATIVE_SECRET_GCM_TAG_BYTES
    private const val MAX_CIPHERTEXT_BYTES = NATIVE_ACTIVITY_FILE_MAX_BYTES -
        HEADER_BYTES - NATIVE_SECRET_GCM_NONCE_BYTES
}

internal class NativeActivityPersistenceCodec(private val aead: NativeSecretAead) {
    fun encode(record: NativeActivityRecord): ByteArray {
        val plaintext = NativeActivityRecordCodec.encode(record)
        return try {
            NativeActivityEncryptedEnvelope.encode(
                aead.seal(plaintext, NativeActivityEncryptedEnvelope.associatedData),
            )
        } catch (failure: NativeActivityStoreException) {
            throw failure
        } catch (_: Exception) {
            throw NativeActivityStoreException("Native activity encryption failed")
        } finally {
            plaintext.fill(0)
        }
    }

    fun decode(encoded: ByteArray): NativeActivityRecord {
        val sealed = NativeActivityEncryptedEnvelope.decode(encoded)
        val plaintext = try {
            aead.open(sealed, NativeActivityEncryptedEnvelope.associatedData)
        } catch (_: Exception) {
            throw NativeActivityStoreException("Native activity decryption failed")
        }
        return try {
            NativeActivityRecordCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }
}

enum class NativeActivityInitializationOutcome {
    Initialized,
    AlreadyPresent,
}

/** Encrypted recoverable owner for every per-identity inbox and relay cursor. */
class FileNativeActivityStore internal constructor(
    directory: File,
    aead: NativeSecretAead,
    private val ioDispatcher: CoroutineDispatcher,
) : NativeActivityMigrationStore {
    constructor(
        paths: NativeStoragePaths,
        keyAlias: String = DEFAULT_KEY_ALIAS,
    ) : this(paths.rootDirectory, AndroidKeystoreAead(keyAlias), Dispatchers.IO)

    private val operationLock = Any()
    private var acceptedUiRevision = -1L
    private val persistenceCodec = NativeActivityPersistenceCodec(aead)
    private val file = RecoverableAtomicFile(
        directory = directory,
        fileName = NATIVE_ACTIVITY_FILE,
        maxBytes = NATIVE_ACTIVITY_FILE_MAX_BYTES,
        validator = persistenceCodec::decode,
    )

    suspend fun read(): NativeActivityRecord? = withContext(ioDispatcher) {
        synchronized(operationLock) { readOptional() }
    }

    suspend fun loadIdentity(pubkey: String): NativeActivityIdentityState? =
        withContext(ioDispatcher) {
            synchronized(operationLock) {
                validatePubkey(pubkey)
                readOptional()?.identities?.get(pubkey)
            }
        }

    suspend fun initializeEmptyForNewInstall(): NativeActivityInitializationOutcome =
        withContext(ioDispatcher) {
            synchronized(operationLock) {
                if (readOptional() != null) {
                    return@synchronized NativeActivityInitializationOutcome.AlreadyPresent
                }
                persist(emptyRecord())
                NativeActivityInitializationOutcome.Initialized
            }
        }

    suspend fun record(
        revision: Long,
        pubkey: String,
        notification: NativeActivityNotification,
    ): Boolean = mutate(revision) { current ->
        val state = current.identities[pubkey] ?: emptyIdentity(pubkey)
        val write = NativeActivityInboxProtocol.record(pubkey, state.items, notification)
            ?: return@mutate null
        current.next(state.copyWith(items = write.items))
    }

    suspend fun markAllRead(revision: Long, pubkey: String): Boolean =
        mutate(revision) { current ->
            val state = current.identities[pubkey] ?: return@mutate null
            val write = NativeActivityInboxProtocol.markAllRead(pubkey, state.items)
                ?: return@mutate null
            current.next(state.copyWith(items = write.items))
        }

    suspend fun seedCursor(
        revision: Long,
        kind: NativeActivityCursorKind,
        pubkey: String,
        nowSeconds: Long,
    ): Boolean = mutate(revision) { current ->
        val state = current.identities[pubkey] ?: emptyIdentity(pubkey)
        val existing = state.cursor(kind)
        if (existing != null) return@mutate null
        val value = NativeActivityCursorProtocol.seed(null, nowSeconds)
        current.next(state.withCursor(kind, value))
    }

    suspend fun advanceCursor(
        revision: Long,
        kind: NativeActivityCursorKind,
        pubkey: String,
        eventSeconds: Long,
    ): Boolean = mutate(revision) { current ->
        val state = current.identities[pubkey] ?: return@mutate null
        val cursor = state.cursor(kind) ?: return@mutate null
        val write = NativeActivityCursorProtocol.advance(kind, pubkey, cursor, eventSeconds)
            ?: return@mutate null
        current.next(state.withCursor(kind, write.value))
    }

    override fun stageLegacy(values: Map<String, String>): Unit = synchronized(operationLock) {
        guard("Native activity staging failed") {
            file.stage(persistenceCodec.encode(legacyRecord(values)))
            Unit
        }
    }

    override fun commitStaged() = synchronized(operationLock) {
        guard("Native activity commit failed") { file.commit() }
    }

    override fun verifyLegacy(values: Map<String, String>) = synchronized(operationLock) {
        guard("Native activity verification failed") {
            if (readOptional() != legacyRecord(values)) {
                throw NativeActivityStoreException("Native activity verification failed")
            }
        }
    }

    override fun committedCiphertextDigest(): ByteArray? = synchronized(operationLock) {
        try {
            val encrypted = file.read() ?: return@synchronized null
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(CIPHERTEXT_DIGEST_DOMAIN)
            digest.update((encrypted.size ushr 24).toByte())
            digest.update((encrypted.size ushr 16).toByte())
            digest.update((encrypted.size ushr 8).toByte())
            digest.update(encrypted.size.toByte())
            digest.update(encrypted)
            digest.digest()
        } catch (_: RuntimeException) {
            null
        }
    }

    private suspend fun mutate(
        revision: Long,
        transform: (NativeActivityRecord) -> NativeActivityRecord?,
    ): Boolean = withContext(ioDispatcher) {
        synchronized(operationLock) {
            require(revision >= 0) { "Activity revision must be non-negative" }
            if (revision < acceptedUiRevision) return@synchronized false
            val current = readOptional()
                ?: throw NativeActivityStoreException("Native activity store is not initialized")
            val updated = transform(current) ?: return@synchronized false
            if (updated.identities != current.identities) persist(updated)
            acceptedUiRevision = revision
            true
        }
    }

    private fun persist(record: NativeActivityRecord) {
        guard("Native activity write failed") {
            file.stage(persistenceCodec.encode(record))
            file.commit()
            if (readOptional() != record) {
                throw NativeActivityStoreException("Native activity reopen verification failed")
            }
        }
    }

    private fun readOptional(): NativeActivityRecord? = guard("Native activity read failed") {
        file.read()?.let(persistenceCodec::decode)
    }

    private fun legacyRecord(values: Map<String, String>): NativeActivityRecord {
        if (values.size > LegacySnapshotLimits.MAX_ENTRIES_PER_STORE ||
            values.keys.any { !LegacyStorageContract.isDynamicKey(it) }
        ) {
            throw NativeActivityStoreException("Legacy activity keys are invalid")
        }
        val builders = TreeMap<String, LegacyIdentityBuilder>()
        val seen = mutableSetOf<String>()
        for ((key, value) in values.toSortedMap()) {
            val prefix = LegacyStorageContract.dynamicKeyPrefixes.singleOrNull(key::startsWith)
                ?: throw NativeActivityStoreException("Legacy activity key is invalid")
            val pubkey = key.removePrefix(prefix).lowercase(Locale.ROOT)
            validatePubkey(pubkey)
            val slot = "$prefix$pubkey"
            if (!seen.add(slot)) {
                throw NativeActivityStoreException("Legacy activity keys collide")
            }
            val builder = builders.getOrPut(pubkey) { LegacyIdentityBuilder() }
            when (prefix) {
                INBOX_PREFIX -> builder.items = NativeActivityInboxProtocol.decodeNormalized(value)
                ZAP_CURSOR_PREFIX -> builder.zapCursor = parseCursor(value)
                CONFIRMATION_CURSOR_PREFIX -> builder.confirmationCursor = parseCursor(value)
                else -> throw NativeActivityStoreException("Legacy activity key is invalid")
            }
        }
        val identities = builders.mapValuesTo(TreeMap()) { (pubkey, builder) ->
            NativeActivityIdentityState(
                pubkey,
                builder.items,
                builder.zapCursor,
                builder.confirmationCursor,
            )
        }
        return NativeActivityRecord(NativeActivityRecord.CURRENT_SCHEMA_VERSION, 0, identities)
    }

    private fun parseCursor(raw: String): Long = raw.toLongOrNull()
        ?.takeIf { it in 0..NativeActivityInboxProtocol.MAX_TIMESTAMP_SECONDS }
        ?: throw NativeActivityStoreException("Legacy activity cursor is invalid")

    private fun validatePubkey(pubkey: String) {
        try {
            NativeActivityInboxProtocol.storageKey(pubkey)
        } catch (_: IllegalArgumentException) {
            throw NativeActivityStoreException("Native activity pubkey is invalid")
        }
    }

    private fun emptyRecord() = NativeActivityRecord(
        NativeActivityRecord.CURRENT_SCHEMA_VERSION,
        0,
        emptyMap(),
    )

    private fun emptyIdentity(pubkey: String): NativeActivityIdentityState {
        validatePubkey(pubkey)
        return NativeActivityIdentityState(pubkey, emptyList(), null, null)
    }

    private fun NativeActivityRecord.next(state: NativeActivityIdentityState): NativeActivityRecord {
        val updated = TreeMap(identities)
        updated[state.pubkey] = state
        return NativeActivityRecord(schemaVersion, sequence + 1, updated)
    }

    private fun NativeActivityIdentityState.copyWith(
        items: List<NativeActivityNotification> = this.items,
        zapCursorSeconds: Long? = this.zapCursorSeconds,
        confirmationCursorSeconds: Long? = this.confirmationCursorSeconds,
    ) = NativeActivityIdentityState(
        pubkey,
        items,
        zapCursorSeconds,
        confirmationCursorSeconds,
    )

    private fun NativeActivityIdentityState.cursor(kind: NativeActivityCursorKind): Long? =
        when (kind) {
            NativeActivityCursorKind.Zap -> zapCursorSeconds
            NativeActivityCursorKind.Confirmation -> confirmationCursorSeconds
        }

    private fun NativeActivityIdentityState.withCursor(
        kind: NativeActivityCursorKind,
        value: Long,
    ): NativeActivityIdentityState = when (kind) {
        NativeActivityCursorKind.Zap -> copyWith(zapCursorSeconds = value)
        NativeActivityCursorKind.Confirmation -> copyWith(confirmationCursorSeconds = value)
    }

    private inline fun <T> guard(message: String, block: () -> T): T = try {
        block()
    } catch (failure: NativeActivityStoreException) {
        throw failure
    } catch (_: RuntimeException) {
        throw NativeActivityStoreException(message)
    }

    private class LegacyIdentityBuilder {
        var items: List<NativeActivityNotification> = emptyList()
        var zapCursor: Long? = null
        var confirmationCursor: Long? = null
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "app.roadstr.native.activity.v1"
        private const val INBOX_PREFIX = "activity_inbox_"
        private const val ZAP_CURSOR_PREFIX = "activity_zap_cursor_"
        private const val CONFIRMATION_CURSOR_PREFIX = "activity_confirmation_cursor_"
        private val CIPHERTEXT_DIGEST_DOMAIN =
            "roadstr-native-activity-ciphertext-v1".toByteArray(Charsets.US_ASCII)
    }
}
