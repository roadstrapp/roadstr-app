package app.roadstr.storage

import app.roadstr.migration.LegacyAsset
import app.roadstr.migration.LegacyIdentity
import app.roadstr.migration.LegacySnapshotEnvelope
import app.roadstr.migration.LegacyStorageContract
import app.roadstr.migration.LegacyStorageSnapshot
import app.roadstr.migration.NativeSnapshotWriter
import java.security.MessageDigest

/** Public/native representation of one migrated snapshot.
 *
 * Protected values are intentionally absent. The public record contains only
 * per-key SHA-256 commitments so it can be compared after reopen without
 * placing nsec, NWC or passphrases in preferences/database files.
 */
data class NativeSnapshotRecord(
    val schemaVersion: Int,
    val ordinaryValues: Map<String, String>,
    val secureValueDigests: Map<String, String>,
    val identity: LegacyIdentity,
    val assets: List<LegacyAsset>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromLegacy(snapshot: LegacyStorageSnapshot): NativeSnapshotRecord =
            NativeSnapshotRecord(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                ordinaryValues = snapshot.ordinaryValues.toSortedMap(),
                secureValueDigests = snapshot.secureValues
                    .toSortedMap()
                    .mapValues { (key, value) -> NativeSecretDigest.sha256(key, value) },
                identity = LegacyIdentity(
                    publicKeyHex = snapshot.identity.publicKeyHex,
                    flavor = snapshot.identity.flavor,
                    privateKeyHex = null,
                ),
                assets = snapshot.assets.sortedWith(
                    compareBy(LegacyAsset::relativePath, LegacyAsset::sizeBytes, LegacyAsset::sha256),
                ),
            )
    }
}

class NativeSnapshotCodecException(message: String) : IllegalArgumentException(message)

/** Canonical public-record codec; it reuses the bounded envelope framing. */
object NativeSnapshotRecordCodec {
    private val hex64 = Regex("^[0-9a-fA-F]{64}$")

    fun encode(snapshot: LegacyStorageSnapshot): ByteArray =
        encode(NativeSnapshotRecord.fromLegacy(snapshot))

    fun encode(record: NativeSnapshotRecord): ByteArray {
        validateRecord(record)
        return LegacySnapshotEnvelope.encode(
            LegacyStorageSnapshot(
                schemaVersion = record.schemaVersion,
                ordinaryValues = record.ordinaryValues,
                secureValues = record.secureValueDigests,
                identity = record.identity,
                assets = record.assets,
            ),
        )
    }

    fun decode(encoded: ByteArray): NativeSnapshotRecord {
        val decoded = try {
            LegacySnapshotEnvelope.decode(encoded)
        } catch (_: RuntimeException) {
            throw NativeSnapshotCodecException("Native snapshot cannot be decoded")
        }
        val record = NativeSnapshotRecord(
            schemaVersion = decoded.schemaVersion,
            ordinaryValues = decoded.ordinaryValues.toSortedMap(),
            secureValueDigests = decoded.secureValues.toSortedMap(),
            identity = decoded.identity,
            assets = decoded.assets.sortedWith(
                compareBy(LegacyAsset::relativePath, LegacyAsset::sizeBytes, LegacyAsset::sha256),
            ),
        )
        try {
            validateRecord(record)
        } catch (_: RuntimeException) {
            throw NativeSnapshotCodecException("Native snapshot record is invalid")
        }
        return record
    }

    private fun validateRecord(record: NativeSnapshotRecord) {
        require(record.schemaVersion == NativeSnapshotRecord.CURRENT_SCHEMA_VERSION) {
            "Unsupported native snapshot schema"
        }
        require(record.ordinaryValues.keys.all { key ->
            key in LegacyStorageContract.hiveKeys || LegacyStorageContract.isDynamicKey(key)
        }) { "Native snapshot contains unsupported ordinary keys" }
        require(record.secureValueDigests.keys.all { it in LegacyStorageContract.secureKeys }) {
            "Native snapshot contains unsupported protected keys"
        }
        require(record.secureValueDigests.values.all { hex64.matches(it) }) {
            "Native snapshot contains invalid protected commitments"
        }
        require(record.identity.privateKeyHex == null) {
            "Native snapshot contains private identity material"
        }
        require(record.identity.publicKeyHex == null || hex64.matches(record.identity.publicKeyHex)) {
            "Native snapshot contains an invalid public identity"
        }
        require(record.identity.flavor == null || record.identity.flavor in setOf("amber", "nsec")) {
            "Native snapshot contains an invalid identity mode"
        }
        if (record.identity.flavor != null) {
            require(record.identity.publicKeyHex != null) {
                "Native snapshot identity is incomplete"
            }
        }
    }
}

/** Domain-separated commitment so a public record cannot be mistaken for raw data. */
object NativeSecretDigest {
    private val domain = "roadstr-native-secret-v1".toByteArray(Charsets.UTF_8)

    fun sha256(key: String, value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(domain)
        digest.updateString(key)
        digest.updateString(value)
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun MessageDigest.updateString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        updateInt(bytes.size)
        update(bytes)
    }

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr 24).toByte())
        update((value ushr 16).toByte())
        update((value ushr 8).toByte())
        update(value.toByte())
    }
}

/** Durable adapter boundary for non-secret native data. */
interface NativePublicSnapshotStore {
    fun stage(encodedRecord: ByteArray)
    fun commit()
    fun read(): ByteArray?
}

/** Keystore-backed adapter boundary for values that must never enter the public record. */
interface NativeSecretStore {
    fun stage(values: Map<String, String>)
    fun commit()
    fun verify(values: Map<String, String>)
}

/** Verifies protected state without returning or exporting its raw values. */
fun interface NativeSecretCommitmentVerifier {
    fun matches(expectedDigests: Map<String, String>): Boolean
}

/**
 * Coordinates the public record and protected store under the migration
 * protocol. A failure after either commit leaves the completion marker false;
 * the next run stages a complete replacement and verifies both stores again.
 */
class CompositeNativeSnapshotWriter(
    private val publicStore: NativePublicSnapshotStore,
    private val secretStore: NativeSecretStore,
) : NativeSnapshotWriter {
    private var stagedRecord: NativeSnapshotRecord? = null
    private var stagedSecrets: Map<String, String>? = null

    override fun stage(snapshot: LegacyStorageSnapshot) {
        val record = NativeSnapshotRecord.fromLegacy(snapshot)
        val encoded = NativeSnapshotRecordCodec.encode(record)
        try {
            publicStore.stage(encoded)
        } catch (_: RuntimeException) {
            throw IllegalStateException("Native public snapshot staging failed")
        }
        try {
            secretStore.stage(snapshot.secureValues.toMap())
        } catch (_: RuntimeException) {
            throw IllegalStateException("Native protected store staging failed")
        }
        stagedRecord = record
        stagedSecrets = snapshot.secureValues.toMap()
    }

    override fun commit() {
        check(stagedRecord != null && stagedSecrets != null) {
            "Native snapshot was not staged"
        }
        try {
            publicStore.commit()
        } catch (_: RuntimeException) {
            throw IllegalStateException("Native public snapshot commit failed")
        }
        try {
            secretStore.commit()
        } catch (_: RuntimeException) {
            throw IllegalStateException("Native protected store commit failed")
        }
    }

    override fun verify(snapshot: LegacyStorageSnapshot) {
        val expected = NativeSnapshotRecord.fromLegacy(snapshot)
        val actual = try {
            publicStore.read()?.let(NativeSnapshotRecordCodec::decode)
        } catch (_: RuntimeException) {
            null
        }
        check(actual == expected) { "Native public snapshot verification failed" }
        try {
            secretStore.verify(snapshot.secureValues)
        } catch (_: RuntimeException) {
            throw IllegalStateException("Native protected store verification failed")
        }
    }
}
