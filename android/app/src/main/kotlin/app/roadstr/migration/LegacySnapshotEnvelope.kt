package app.roadstr.migration

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/**
 * Binary boundary between a legacy reader and the native migration core.
 *
 * The envelope is deterministic and bounded. Its trailing SHA-256 detects
 * transport corruption; it is deliberately not an authenticity primitive.
 * Authenticity still comes from reading private app storage and validating
 * identity bindings before any native write.
 */
object LegacySnapshotEnvelope {
    private val magic = "RSTRMIG1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    private const val DIGEST_BYTES = 32

    fun encode(snapshot: LegacyStorageSnapshot): ByteArray {
        ensure(snapshot.ordinaryValues.size <= LegacySnapshotLimits.MAX_ENTRIES_PER_STORE) {
            "Too many ordinary entries"
        }
        ensure(snapshot.secureValues.size <= LegacySnapshotLimits.MAX_ENTRIES_PER_STORE) {
            "Too many secure entries"
        }
        ensure(snapshot.assets.size <= LegacySnapshotLimits.MAX_ASSETS) {
            "Too many assets"
        }
        ensure(snapshot.assets.map(LegacyAsset::relativePath).toSet().size == snapshot.assets.size) {
            "Duplicate asset path"
        }

        val bodyBytes = encodedBodySize(snapshot)
        ensure(bodyBytes <= LegacySnapshotLimits.MAX_ENVELOPE_BYTES - DIGEST_BYTES) {
            "Envelope is too large"
        }
        val bodyBuffer = ByteArrayOutputStream(bodyBytes.toInt())
        DataOutputStream(bodyBuffer).use { output ->
            output.write(magic)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(snapshot.schemaVersion)
            output.writeMap(snapshot.ordinaryValues, LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES)
            output.writeMap(snapshot.secureValues, LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES)
            output.writeNullableString(
                snapshot.identity.publicKeyHex,
                LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES,
            )
            output.writeNullableString(
                snapshot.identity.flavor,
                LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES,
            )
            output.writeNullableString(
                snapshot.identity.privateKeyHex,
                LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES,
            )

            val assets = snapshot.assets.sortedWith(
                compareBy(LegacyAsset::relativePath, LegacyAsset::sizeBytes, LegacyAsset::sha256),
            )
            output.writeInt(assets.size)
            for (asset in assets) {
                output.writeString(asset.relativePath, LegacySnapshotLimits.MAX_ASSET_PATH_BYTES)
                ensure(asset.sizeBytes in 0..LegacySnapshotLimits.MAX_ASSET_SIZE_BYTES) {
                    "Invalid asset size"
                }
                output.writeLong(asset.sizeBytes)
                output.writeString(asset.sha256, 64)
            }
        }

        val body = bodyBuffer.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(body)
        return body + digest
    }

    fun decode(envelope: ByteArray): LegacyStorageSnapshot {
        ensure(envelope.size >= magic.size + 12 + DIGEST_BYTES) { "Envelope is truncated" }
        ensure(envelope.size <= LegacySnapshotLimits.MAX_ENVELOPE_BYTES) { "Envelope is too large" }

        val bodySize = envelope.size - DIGEST_BYTES
        val expectedDigest = envelope.copyOfRange(bodySize, envelope.size)
        val actualDigest = MessageDigest.getInstance("SHA-256")
            .apply { update(envelope, 0, bodySize) }
            .digest()
        ensure(MessageDigest.isEqual(expectedDigest, actualDigest)) { "Envelope integrity check failed" }

        val cursor = Cursor(envelope, bodySize)
        ensure(cursor.readBytes(magic.size).contentEquals(magic)) { "Invalid envelope magic" }
        ensure(cursor.readInt() == FORMAT_VERSION) { "Unsupported envelope format" }
        val schemaVersion = cursor.readInt()
        val ordinaryValues = cursor.readMap(LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES)
        val secureValues = cursor.readMap(LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES)
        val identity = LegacyIdentity(
            publicKeyHex = cursor.readNullableString(LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES),
            flavor = cursor.readNullableString(LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES),
            privateKeyHex = cursor.readNullableString(LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES),
        )

        val assetCount = cursor.readCount(LegacySnapshotLimits.MAX_ASSETS, "asset")
        val assetPaths = mutableSetOf<String>()
        val assets = ArrayList<LegacyAsset>(assetCount)
        repeat(assetCount) {
            val relativePath = cursor.readString(LegacySnapshotLimits.MAX_ASSET_PATH_BYTES)
            ensure(assetPaths.add(relativePath)) { "Duplicate asset path" }
            val sizeBytes = cursor.readLong()
            ensure(sizeBytes in 0..LegacySnapshotLimits.MAX_ASSET_SIZE_BYTES) {
                "Invalid asset size"
            }
            val sha256 = cursor.readString(64)
            assets += LegacyAsset(relativePath, sizeBytes, sha256)
        }
        ensure(cursor.isAtEnd()) { "Envelope contains trailing data" }

        return LegacyStorageSnapshot(
            schemaVersion = schemaVersion,
            ordinaryValues = ordinaryValues,
            secureValues = secureValues,
            identity = identity,
            assets = assets,
        )
    }

    private fun DataOutputStream.writeMap(values: Map<String, String>, maxValueBytes: Int) {
        writeInt(values.size)
        for ((key, value) in values.toSortedMap()) {
            writeString(key, LegacySnapshotLimits.MAX_KEY_BYTES)
            writeString(value, maxValueBytes)
        }
    }

    private fun DataOutputStream.writeNullableString(value: String?, maxBytes: Int) {
        if (value == null) {
            writeInt(-1)
        } else {
            writeString(value, maxBytes)
        }
    }

    private fun DataOutputStream.writeString(value: String, maxBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        ensure(bytes.size <= maxBytes) { "Envelope field is too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun encodedBodySize(snapshot: LegacyStorageSnapshot): Long {
        var size = magic.size.toLong() + Int.SIZE_BYTES + Int.SIZE_BYTES
        size += encodedMapSize(snapshot.ordinaryValues, LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES)
        size += encodedMapSize(snapshot.secureValues, LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES)
        size += encodedNullableStringSize(
            snapshot.identity.publicKeyHex,
            LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES,
        )
        size += encodedNullableStringSize(
            snapshot.identity.flavor,
            LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES,
        )
        size += encodedNullableStringSize(
            snapshot.identity.privateKeyHex,
            LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES,
        )
        size += Int.SIZE_BYTES
        for (asset in snapshot.assets) {
            size += encodedStringSize(asset.relativePath, LegacySnapshotLimits.MAX_ASSET_PATH_BYTES)
            ensure(asset.sizeBytes in 0..LegacySnapshotLimits.MAX_ASSET_SIZE_BYTES) {
                "Invalid asset size"
            }
            size += Long.SIZE_BYTES
            size += encodedStringSize(asset.sha256, 64)
        }
        return size
    }

    private fun encodedMapSize(values: Map<String, String>, maxValueBytes: Int): Long {
        var size = Int.SIZE_BYTES.toLong()
        for ((key, value) in values) {
            size += encodedStringSize(key, LegacySnapshotLimits.MAX_KEY_BYTES)
            size += encodedStringSize(value, maxValueBytes)
        }
        return size
    }

    private fun encodedNullableStringSize(value: String?, maxBytes: Int): Long =
        if (value == null) Int.SIZE_BYTES.toLong() else encodedStringSize(value, maxBytes)

    private fun encodedStringSize(value: String, maxBytes: Int): Long {
        val size = value.toByteArray(Charsets.UTF_8).size
        ensure(size <= maxBytes) { "Envelope field is too large" }
        return Int.SIZE_BYTES.toLong() + size
    }

    private class Cursor(
        private val bytes: ByteArray,
        private val limit: Int,
    ) {
        private var offset = 0

        fun readMap(maxValueBytes: Int): Map<String, String> {
            val count = readCount(LegacySnapshotLimits.MAX_ENTRIES_PER_STORE, "entry")
            val values = LinkedHashMap<String, String>(count)
            repeat(count) {
                val key = readString(LegacySnapshotLimits.MAX_KEY_BYTES)
                val value = readString(maxValueBytes)
                ensure(values.put(key, value) == null) { "Duplicate map key" }
            }
            return values
        }

        fun readCount(max: Int, kind: String): Int {
            val count = readInt()
            ensure(count in 0..max) { "Invalid $kind count" }
            return count
        }

        fun readNullableString(maxBytes: Int): String? {
            val length = readInt()
            if (length == -1) return null
            return readStringBody(length, maxBytes)
        }

        fun readString(maxBytes: Int): String = readStringBody(readInt(), maxBytes)

        private fun readStringBody(length: Int, maxBytes: Int): String {
            ensure(length in 0..maxBytes) { "Invalid envelope field length" }
            val encoded = readBytes(length)
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString()
            } catch (_: CharacterCodingException) {
                throw LegacyEnvelopeException("Envelope contains invalid UTF-8")
            }
        }

        fun readInt(): Int = ByteBuffer.wrap(readBytes(Int.SIZE_BYTES)).int

        fun readLong(): Long = ByteBuffer.wrap(readBytes(Long.SIZE_BYTES)).long

        fun readBytes(count: Int): ByteArray {
            ensure(count >= 0 && count <= limit - offset) { "Envelope is truncated" }
            val result = bytes.copyOfRange(offset, offset + count)
            offset += count
            return result
        }

        fun isAtEnd(): Boolean = offset == limit
    }

    private inline fun ensure(condition: Boolean, message: () -> String) {
        if (!condition) throw LegacyEnvelopeException(message())
    }
}

class LegacyEnvelopeException(message: String) : IllegalArgumentException(message)

/** Adapts a byte source without modifying the source or any legacy storage. */
class LegacyEnvelopeSnapshotReader(
    private val source: () -> ByteArray?,
) : LegacySnapshotReader {
    override fun read(): LegacyStorageSnapshot? = source()?.let(LegacySnapshotEnvelope::decode)
}
