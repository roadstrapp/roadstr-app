package app.roadstr.storage

import app.roadstr.feature.saved.NativeParkingPosition
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import app.roadstr.feature.map.NativeMapPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val NATIVE_SAVED_PLACES_FILE = "saved_places_v1.bin"
internal const val NATIVE_SAVED_PLACES_FILE_MAX_BYTES = 4 * 1024 * 1024 + 128

class NativeSavedPlacesException(message: String) : IllegalStateException(message)

class NativeSavedPlacesRecord(
    val schemaVersion: Int,
    val sequence: Long,
    favorites: List<NativeSavedPlace>,
    val parking: NativeParkingPosition?,
) {
    val favorites: List<NativeSavedPlace> = Collections.unmodifiableList(favorites.toList())

    override fun equals(other: Any?): Boolean =
        other is NativeSavedPlacesRecord &&
            schemaVersion == other.schemaVersion &&
            sequence == other.sequence &&
            favorites == other.favorites &&
            parking == other.parking

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + sequence.hashCode()
        result = 31 * result + favorites.hashCode()
        result = 31 * result + (parking?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "NativeSavedPlacesRecord(schemaVersion=$schemaVersion, sequence=$sequence, " +
            "favorites=${favorites.size}, hasParking=${parking != null})"

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Strict canonical framing used only inside the encrypted saved-places file. */
object NativeSavedPlacesRecordCodec {
    private val MAGIC = "RSTRSAV1".toByteArray(Charsets.US_ASCII)
    private const val DIGEST_BYTES = 32
    private const val MAX_LABEL_BYTES = NativeSavedPlacesProtocol.MAX_LABEL_CHARS * 3
    private const val MAX_ADDRESS_BYTES = NativeSavedPlacesProtocol.MAX_ADDRESS_CHARS * 3
    const val MAX_PLAINTEXT_BYTES = 4 * 1024 * 1024

    fun encode(record: NativeSavedPlacesRecord): ByteArray {
        validateRecord(record)
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(record.schemaVersion)
                output.writeLong(record.sequence)
                output.writeInt(record.favorites.size)
                for (favorite in record.favorites) {
                    output.writeText(favorite.label, MAX_LABEL_BYTES)
                    output.writeText(favorite.address, MAX_ADDRESS_BYTES)
                    output.writeDouble(favorite.point.latitude)
                    output.writeDouble(favorite.point.longitude)
                }
                output.writeBoolean(record.parking != null)
                record.parking?.let { parking ->
                    output.writeDouble(parking.point.latitude)
                    output.writeDouble(parking.point.longitude)
                    output.writeBoolean(parking.savedAtEpochMillis != null)
                    parking.savedAtEpochMillis?.let(output::writeLong)
                }
            }
            bytes.toByteArray()
        }
        val framed = payload + MessageDigest.getInstance("SHA-256").digest(payload)
        if (framed.size > MAX_PLAINTEXT_BYTES) invalid()
        return framed
    }

    fun decode(encoded: ByteArray): NativeSavedPlacesRecord {
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
                if (count !in 0..NativeSavedPlacesProtocol.MAX_STORED_ITEMS) invalid()
                val favorites = ArrayList<NativeSavedPlace>(count)
                repeat(count) {
                    val favorite = NativeSavedPlace(
                        label = input.readText(MAX_LABEL_BYTES),
                        address = input.readText(MAX_ADDRESS_BYTES),
                        point = NativeMapPoint(input.readDouble(), input.readDouble()),
                    )
                    if (NativeSavedPlacesProtocol.normalizeFavorite(favorite) != favorite) invalid()
                    favorites += favorite
                }
                val parking = if (input.readBoolean()) {
                    val point = NativeMapPoint(input.readDouble(), input.readDouble())
                    val savedAt = if (input.readBoolean()) input.readLong() else null
                    NativeParkingPosition(point, savedAt).also(::validateParking)
                } else {
                    null
                }
                if (input.available() != 0) invalid()
                NativeSavedPlacesRecord(schemaVersion, sequence, favorites, parking)
                    .also(::validateRecord)
            }
        } catch (failure: NativeSavedPlacesException) {
            throw failure
        } catch (_: Exception) {
            invalid()
        }
    }

    private fun validateRecord(record: NativeSavedPlacesRecord) {
        if (record.schemaVersion != NativeSavedPlacesRecord.CURRENT_SCHEMA_VERSION ||
            record.sequence < 0 ||
            record.favorites.size > NativeSavedPlacesProtocol.MAX_STORED_ITEMS
        ) {
            invalid()
        }
        for (favorite in record.favorites) {
            if (NativeSavedPlacesProtocol.normalizeFavorite(favorite) != favorite) invalid()
            utf8Bytes(favorite.label, MAX_LABEL_BYTES)
            utf8Bytes(favorite.address, MAX_ADDRESS_BYTES)
        }
        record.parking?.let(::validateParking)
    }

    private fun validateParking(parking: NativeParkingPosition) {
        val canonical = try {
            NativeSavedPlacesProtocol.decodeParking(
                NativeSavedPlacesProtocol.encodeParking(parking),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
        if (canonical != parking) invalid()
    }

    private fun DataOutputStream.writeText(value: String, limit: Int) {
        val bytes = utf8Bytes(value, limit)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun utf8Bytes(value: String, limit: Int): ByteArray = try {
        val buffer = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
        ByteArray(buffer.remaining()).also(buffer::get).also {
            if (it.size > limit) invalid()
        }
    } catch (_: CharacterCodingException) {
        invalid()
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

    private fun invalid(): Nothing =
        throw NativeSavedPlacesException("Native saved-places value is invalid")
}

/** Independent AES-GCM envelope because saved places may exceed the small secret-map bound. */
internal object NativeSavedPlacesEncryptedEnvelope {
    private val MAGIC = "RSTRSVG1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    private const val HEADER_BYTES = 20
    val associatedData = "roadstr-native-saved-places-v1".toByteArray(Charsets.US_ASCII)

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
        if (encoded.size !in MIN_FILE_BYTES..NATIVE_SAVED_PLACES_FILE_MAX_BYTES) invalid()
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
        } catch (failure: NativeSavedPlacesException) {
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
        throw NativeSavedPlacesException("Native saved-places envelope is invalid")

    private const val MIN_FILE_BYTES = HEADER_BYTES +
        NATIVE_SECRET_GCM_NONCE_BYTES + NATIVE_SECRET_GCM_TAG_BYTES
    private const val MAX_CIPHERTEXT_BYTES = NATIVE_SAVED_PLACES_FILE_MAX_BYTES -
        HEADER_BYTES - NATIVE_SECRET_GCM_NONCE_BYTES
}

internal class NativeSavedPlacesPersistenceCodec(private val aead: NativeSecretAead) {
    fun encode(record: NativeSavedPlacesRecord): ByteArray {
        val plaintext = NativeSavedPlacesRecordCodec.encode(record)
        return try {
            NativeSavedPlacesEncryptedEnvelope.encode(
                aead.seal(plaintext, NativeSavedPlacesEncryptedEnvelope.associatedData),
            )
        } catch (failure: NativeSavedPlacesException) {
            throw failure
        } catch (_: Exception) {
            throw NativeSavedPlacesException("Native saved-places encryption failed")
        } finally {
            plaintext.fill(0)
        }
    }

    fun decode(encoded: ByteArray): NativeSavedPlacesRecord {
        val sealed = NativeSavedPlacesEncryptedEnvelope.decode(encoded)
        val plaintext = try {
            aead.open(sealed, NativeSavedPlacesEncryptedEnvelope.associatedData)
        } catch (_: Exception) {
            throw NativeSavedPlacesException("Native saved-places decryption failed")
        }
        return try {
            NativeSavedPlacesRecordCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }
}

enum class NativeSavedPlacesInitializationOutcome {
    Initialized,
    AlreadyPresent,
}

/**
 * Encrypted recoverable owner for favorites and the saved parking coordinate.
 *
 * Legacy import is supplied by the transactional migration writer. Runtime
 * mutations require an initialized record, preventing a missing migration
 * from being mistaken for a valid empty user data set.
 */
class FileNativeSavedPlacesStore internal constructor(
    directory: File,
    aead: NativeSecretAead,
    private val ioDispatcher: CoroutineDispatcher,
) : NativeSavedPlacesMigrationStore {
    constructor(
        paths: NativeStoragePaths,
        keyAlias: String = DEFAULT_KEY_ALIAS,
    ) : this(paths.rootDirectory, AndroidKeystoreAead(keyAlias), Dispatchers.IO)

    private val operationLock = Any()
    private var acceptedUiRevision = -1L
    private val persistenceCodec = NativeSavedPlacesPersistenceCodec(aead)
    private val file = RecoverableAtomicFile(
        directory = directory,
        fileName = NATIVE_SAVED_PLACES_FILE,
        maxBytes = NATIVE_SAVED_PLACES_FILE_MAX_BYTES,
        validator = persistenceCodec::decode,
    )

    suspend fun load(): NativeSavedPlacesRecord? = withContext(ioDispatcher) {
        synchronized(operationLock) { readOptional() }
    }

    suspend fun initializeEmptyForNewInstall(): NativeSavedPlacesInitializationOutcome =
        withContext(ioDispatcher) {
            synchronized(operationLock) {
                if (readOptional() != null) {
                    return@synchronized NativeSavedPlacesInitializationOutcome.AlreadyPresent
                }
                persist(emptyRecord())
                NativeSavedPlacesInitializationOutcome.Initialized
            }
        }

    suspend fun upsert(revision: Long, value: NativeSavedPlace, index: Int? = null): Boolean =
        mutate(revision) { current ->
            val normalized = NativeSavedPlacesProtocol.normalizeFavorite(value)
                ?: return@mutate null
            val updated = current.favorites.toMutableList()
            if (index == null) {
                if (updated.size >= NativeSavedPlacesProtocol.MAX_STORED_ITEMS) return@mutate null
                updated += normalized
            } else {
                if (index !in updated.indices) return@mutate null
                updated[index] = normalized
            }
            current.next(favorites = updated)
        }

    suspend fun delete(revision: Long, index: Int): Boolean = mutate(revision) { current ->
        if (index !in current.favorites.indices) return@mutate null
        current.next(favorites = current.favorites.toMutableList().also { it.removeAt(index) })
    }

    suspend fun merge(revision: Long, incoming: Iterable<NativeSavedPlace>): Boolean =
        mutate(revision) { current ->
            val normalized = incoming.mapNotNull(NativeSavedPlacesProtocol::normalizeFavorite)
                .take(NativeSavedPlacesProtocol.MAX_STORED_ITEMS)
            current.next(
                favorites = NativeSavedPlacesProtocol.mergeByLabel(current.favorites, normalized),
            )
        }

    suspend fun setParking(revision: Long, parking: NativeParkingPosition): Boolean =
        mutate(revision) { current ->
            val normalized = normalizeParking(parking) ?: return@mutate null
            current.next(parking = normalized)
        }

    suspend fun clearParking(revision: Long): Boolean = mutate(revision) { current ->
        if (current.parking == null) return@mutate null
        current.next(parking = null)
    }

    override fun stageLegacy(favoritesValue: String?, parkingValue: String?): Unit =
        synchronized(operationLock) {
            guard("Native saved-places staging failed") {
                file.stage(
                    persistenceCodec.encode(
                        legacyRecord(favoritesValue, parkingValue),
                    ),
                )
                Unit
            }
        }

    override fun commitStaged() = synchronized(operationLock) {
        guard("Native saved-places commit failed") { file.commit() }
    }

    override fun verifyLegacy(favoritesValue: String?, parkingValue: String?) =
        synchronized(operationLock) {
            guard("Native saved-places verification failed") {
                if (readOptional() != legacyRecord(favoritesValue, parkingValue)) {
                    throw NativeSavedPlacesException("Native saved-places verification failed")
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
        transform: (NativeSavedPlacesRecord) -> NativeSavedPlacesRecord?,
    ): Boolean = withContext(ioDispatcher) {
        synchronized(operationLock) {
            require(revision >= 0) { "Saved-places revision must be non-negative" }
            if (revision < acceptedUiRevision) return@synchronized false
            val current = readOptional()
                ?: throw NativeSavedPlacesException("Native saved-places store is not initialized")
            val updated = transform(current) ?: return@synchronized false
            if (updated.favorites != current.favorites || updated.parking != current.parking) {
                persist(updated)
            }
            acceptedUiRevision = revision
            true
        }
    }

    private fun persist(record: NativeSavedPlacesRecord) {
        guard("Native saved-places write failed") {
            file.stage(persistenceCodec.encode(record))
            file.commit()
            if (readOptional() != record) {
                throw NativeSavedPlacesException("Native saved-places reopen verification failed")
            }
        }
    }

    private fun readOptional(): NativeSavedPlacesRecord? =
        guard("Native saved-places read failed") {
            file.read()?.let(persistenceCodec::decode)
        }

    private fun legacyRecord(favoritesValue: String?, parkingValue: String?) =
        NativeSavedPlacesRecord(
            schemaVersion = NativeSavedPlacesRecord.CURRENT_SCHEMA_VERSION,
            sequence = 0,
            favorites = NativeSavedPlacesProtocol.decodeStoredFavorites(favoritesValue),
            parking = NativeSavedPlacesProtocol.decodeParking(parkingValue),
        )

    private fun emptyRecord() = NativeSavedPlacesRecord(
        schemaVersion = NativeSavedPlacesRecord.CURRENT_SCHEMA_VERSION,
        sequence = 0,
        favorites = emptyList(),
        parking = null,
    )

    private fun NativeSavedPlacesRecord.next(
        favorites: List<NativeSavedPlace> = this.favorites,
        parking: NativeParkingPosition? = this.parking,
    ) = NativeSavedPlacesRecord(schemaVersion, sequence + 1, favorites, parking)

    private fun normalizeParking(value: NativeParkingPosition): NativeParkingPosition? = try {
        NativeSavedPlacesProtocol.decodeParking(NativeSavedPlacesProtocol.encodeParking(value))
    } catch (_: IllegalArgumentException) {
        null
    }

    private inline fun <T> guard(message: String, block: () -> T): T = try {
        block()
    } catch (failure: NativeSavedPlacesException) {
        throw failure
    } catch (_: RuntimeException) {
        throw NativeSavedPlacesException(message)
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "app.roadstr.native.saved-places.v1"
        private val CIPHERTEXT_DIGEST_DOMAIN =
            "roadstr-native-saved-places-ciphertext-v1".toByteArray(Charsets.US_ASCII)
    }
}
