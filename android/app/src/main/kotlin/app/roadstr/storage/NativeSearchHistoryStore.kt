package app.roadstr.storage

import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val NATIVE_SEARCH_HISTORY_FILE = "search_history_v1.bin"
internal const val NATIVE_SEARCH_HISTORY_FILE_MAX_BYTES = 512 * 1024 + 64

class NativeSearchHistoryException(message: String) : IllegalStateException(message)

/** Integrity-framed native representation of the fixture-locked history rows. */
object NativeSearchHistoryCodec {
    private val magic = "RSTRHST1".toByteArray(Charsets.US_ASCII)
    private const val DIGEST_BYTES = 32
    private const val MAX_ENTRY_BYTES = 4 * 1024
    const val MAX_FILE_BYTES = 512 * 1024

    fun encode(entries: List<SearchHistoryEntry>): ByteArray {
        if (entries.size > SearchHistoryProtocol.MAX_LOADED_ITEMS) {
            invalid()
        }
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(magic)
                output.writeInt(entries.size)
                for (entry in entries) {
                    val encoded = canonicalEntry(entry).toByteArray(Charsets.UTF_8)
                    if (encoded.isEmpty() || encoded.size > MAX_ENTRY_BYTES) invalid()
                    output.writeInt(encoded.size)
                    output.write(encoded)
                }
                output.flush()
                bytes.toByteArray()
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val framed = payload + digest
        if (framed.size > MAX_FILE_BYTES) invalid()
        return framed
    }

    fun decode(encoded: ByteArray): List<SearchHistoryEntry> {
        try {
            if (encoded.size < magic.size + Int.SIZE_BYTES + DIGEST_BYTES) invalid()
            if (encoded.size > MAX_FILE_BYTES) invalid()
            val payloadLength = encoded.size - DIGEST_BYTES
            val payload = encoded.copyOfRange(0, payloadLength)
            val expectedDigest = encoded.copyOfRange(payloadLength, encoded.size)
            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload)
            if (!MessageDigest.isEqual(expectedDigest, actualDigest)) invalid()

            val rows = DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val actualMagic = ByteArray(magic.size)
                input.readFully(actualMagic)
                if (!MessageDigest.isEqual(magic, actualMagic)) invalid()
                val count = input.readInt()
                if (count !in 0..SearchHistoryProtocol.MAX_LOADED_ITEMS) invalid()
                buildList(count) {
                    repeat(count) {
                        val length = input.readInt()
                        if (length !in 1..MAX_ENTRY_BYTES || length > input.available()) invalid()
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        val row = bytes.toString(Charsets.UTF_8)
                        if (!bytes.contentEquals(row.toByteArray(Charsets.UTF_8))) invalid()
                        add(row)
                    }
                }.also {
                    if (input.available() != 0) invalid()
                }
            }
            val entries = SearchHistoryProtocol.decodeStored(rows)
            if (entries.size != rows.size) invalid()
            for (index in rows.indices) {
                if (SearchHistoryProtocol.encodeEntry(entries[index]) != rows[index]) invalid()
            }
            return Collections.unmodifiableList(entries.toList())
        } catch (failure: NativeSearchHistoryException) {
            throw failure
        } catch (_: RuntimeException) {
            invalid()
        }
    }

    private fun canonicalEntry(entry: SearchHistoryEntry): String {
        val encoded = try {
            SearchHistoryProtocol.encodeEntry(entry)
        } catch (_: RuntimeException) {
            invalid()
        }
        val decoded = SearchHistoryProtocol.decodeStored(listOf(encoded))
        if (decoded.size != 1 || decoded.single() != entry) invalid()
        return encoded
    }

    private fun invalid(): Nothing =
        throw NativeSearchHistoryException("Native search history value is invalid")
}

/** AES-GCM boundary keeping labels and coordinates out of the on-disk file. */
internal class NativeSearchHistoryPersistenceCodec(
    private val aead: NativeSecretAead,
) {
    fun encode(entries: List<SearchHistoryEntry>): ByteArray {
        val plaintext = NativeSearchHistoryCodec.encode(entries)
        return try {
            val sealed = aead.seal(plaintext, ASSOCIATED_DATA)
            NativeSecretEncryptedEnvelope.encode(sealed).also { encoded ->
                if (encoded.size > NATIVE_SEARCH_HISTORY_FILE_MAX_BYTES) invalid()
            }
        } catch (failure: NativeSearchHistoryException) {
            throw failure
        } catch (_: Exception) {
            invalid()
        } finally {
            plaintext.fill(0)
        }
    }

    fun decode(encoded: ByteArray): List<SearchHistoryEntry> {
        if (encoded.size > NATIVE_SEARCH_HISTORY_FILE_MAX_BYTES) invalid()
        val sealed = try {
            NativeSecretEncryptedEnvelope.decode(encoded)
        } catch (_: RuntimeException) {
            invalid()
        }
        val plaintext = try {
            aead.open(sealed, ASSOCIATED_DATA)
        } catch (_: Exception) {
            invalid()
        }
        return try {
            NativeSearchHistoryCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun invalid(): Nothing =
        throw NativeSearchHistoryException("Native search history encryption failed")

    private companion object {
        val ASSOCIATED_DATA =
            "roadstr-native-search-history-v1".toByteArray(Charsets.US_ASCII)
    }
}

/**
 * Recoverable app-private search-history store.
 *
 * Import receives an already collected legacy value and never opens Hive. All
 * file work is serialized and moved to the supplied I/O dispatcher. Corrupt
 * unbacked history is non-critical and loads as empty, but normal mutations
 * fail closed so recoverable ciphertext is never silently overwritten.
 */
class FileNativeSearchHistoryStore internal constructor(
    directory: File,
    aead: NativeSecretAead,
    private val ioDispatcher: CoroutineDispatcher,
) : NativeSearchHistoryMigrationStore {
    constructor(
        paths: NativeStoragePaths,
        keyAlias: String = DEFAULT_KEY_ALIAS,
    ) : this(paths.rootDirectory, AndroidKeystoreAead(keyAlias), Dispatchers.IO)

    private val operationLock = Any()
    private val persistenceCodec = NativeSearchHistoryPersistenceCodec(aead)
    private val file = RecoverableAtomicFile(
        directory = directory,
        fileName = NATIVE_SEARCH_HISTORY_FILE,
        maxBytes = NATIVE_SEARCH_HISTORY_FILE_MAX_BYTES,
        validator = persistenceCodec::decode,
    )

    suspend fun load(): List<SearchHistoryEntry> = withContext(ioDispatcher) {
        synchronized(operationLock) { readOrEmpty() }
    }

    suspend fun importLegacy(raw: Any?): List<SearchHistoryEntry> = withContext(ioDispatcher) {
        synchronized(operationLock) {
            val imported = SearchHistoryProtocol.decodeStored(raw)
                .take(SearchHistoryProtocol.MAX_LOADED_ITEMS)
            persist(imported)
            immutableCopy(imported)
        }
    }

    suspend fun prepend(entry: SearchHistoryEntry): List<SearchHistoryEntry> =
        withContext(ioDispatcher) {
            synchronized(operationLock) {
                validateEntry(entry)
                val updated = SearchHistoryProtocol.prepend(entry, readForMutation())
                persist(updated)
                updated
            }
        }

    suspend fun clear() = withContext(ioDispatcher) {
        synchronized(operationLock) { persist(emptyList()) }
    }

    override fun stageLegacy(normalizedLegacyValue: String?) = synchronized(operationLock) {
        try {
            stageEntries(decodeNormalizedLegacy(normalizedLegacyValue))
        } catch (failure: NativeSearchHistoryException) {
            throw failure
        } catch (_: RuntimeException) {
            throw NativeSearchHistoryException("Native search history staging failed")
        }
    }

    override fun commitStaged() = synchronized(operationLock) {
        try {
            file.commit()
        } catch (_: RuntimeException) {
            throw NativeSearchHistoryException("Native search history commit failed")
        }
    }

    override fun verifyLegacy(normalizedLegacyValue: String?) = synchronized(operationLock) {
        val expected = decodeNormalizedLegacy(normalizedLegacyValue)
        try {
            val actual = file.read()?.let(persistenceCodec::decode)
            if (actual != expected) {
                throw NativeSearchHistoryException("Native search history verification failed")
            }
        } catch (failure: NativeSearchHistoryException) {
            throw failure
        } catch (_: RuntimeException) {
            throw NativeSearchHistoryException("Native search history verification failed")
        }
    }

    override fun committedCiphertextDigest(): ByteArray? = synchronized(operationLock) {
        try {
            val encrypted = file.read() ?: return@synchronized null
            ciphertextDigest(encrypted)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun readOrEmpty(): List<SearchHistoryEntry> = try {
        file.read()?.let(persistenceCodec::decode) ?: emptyList()
    } catch (_: NativePersistenceException) {
        emptyList()
    }

    private fun readForMutation(): List<SearchHistoryEntry> = try {
        file.read()?.let(persistenceCodec::decode) ?: emptyList()
    } catch (_: RuntimeException) {
        throw NativeSearchHistoryException("Native search history read failed")
    }

    private fun persist(entries: List<SearchHistoryEntry>) {
        try {
            stageEntries(entries)
            file.commit()
            val reopened = file.read()?.let(persistenceCodec::decode)
            if (reopened != entries) {
                throw NativePersistenceException("Native search history verification failed")
            }
        } catch (failure: NativeSearchHistoryException) {
            throw failure
        } catch (_: RuntimeException) {
            throw NativeSearchHistoryException("Native search history write failed")
        }
    }

    private fun stageEntries(entries: List<SearchHistoryEntry>) {
        file.stage(persistenceCodec.encode(entries))
    }

    private fun decodeNormalizedLegacy(raw: String?): List<SearchHistoryEntry> {
        if (raw == null) return emptyList()
        val parsed = try {
            BoundedJsonParser(raw).parse()
        } catch (_: RuntimeException) {
            null
        }
        return immutableCopy(
            SearchHistoryProtocol.decodeStored(parsed)
                .take(SearchHistoryProtocol.MAX_LOADED_ITEMS),
        )
    }

    private fun ciphertextDigest(encrypted: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(CIPHERTEXT_DIGEST_DOMAIN)
        digest.update((encrypted.size ushr 24).toByte())
        digest.update((encrypted.size ushr 16).toByte())
        digest.update((encrypted.size ushr 8).toByte())
        digest.update(encrypted.size.toByte())
        digest.update(encrypted)
        return digest.digest()
    }

    private fun validateEntry(entry: SearchHistoryEntry) {
        NativeSearchHistoryCodec.encode(listOf(entry))
    }

    private fun immutableCopy(entries: List<SearchHistoryEntry>): List<SearchHistoryEntry> =
        Collections.unmodifiableList(entries.toList())

    companion object {
        const val DEFAULT_KEY_ALIAS = "app.roadstr.native.search-history.v1"
        private val CIPHERTEXT_DIGEST_DOMAIN =
            "roadstr-native-search-history-ciphertext-v1".toByteArray(Charsets.US_ASCII)
    }
}
