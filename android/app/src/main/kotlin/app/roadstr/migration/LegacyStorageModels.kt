package app.roadstr.migration

import java.security.MessageDigest

internal object LegacySnapshotLimits {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_ENTRIES_PER_STORE = 512
    const val MAX_KEY_BYTES = 256
    const val MAX_ORDINARY_VALUE_BYTES = 4 * 1024 * 1024
    const val MAX_SECURE_VALUE_BYTES = 64 * 1024
    const val MAX_ASSETS = 256
    const val MAX_ASSET_PATH_BYTES = 1024
    const val MAX_ASSET_SIZE_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_ENVELOPE_BYTES = 64 * 1024 * 1024
}

/**
 * Normalized representation produced by the legacy reader.
 *
 * Values are canonical strings at this boundary. The reader owns decoding
 * Hive's dynamic Dart values; the native writer owns mapping them to the
 * versioned native schema. Keeping this boundary typed and small prevents a
 * half-migrated Dart object graph from leaking into the new runtime.
 */
data class LegacyIdentity(
    val publicKeyHex: String?,
    val flavor: String?,
    val privateKeyHex: String?,
)

data class LegacyAsset(
    /** Relative path below the app's documents directory. */
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class LegacyStorageSnapshot(
    val schemaVersion: Int,
    val ordinaryValues: Map<String, String>,
    val secureValues: Map<String, String>,
    val identity: LegacyIdentity,
    val assets: List<LegacyAsset>,
)

data class SnapshotValidation(
    val valid: Boolean,
    val reason: String? = null,
) {
    companion object {
        fun ok() = SnapshotValidation(valid = true)
        fun failure(reason: String) = SnapshotValidation(valid = false, reason = reason)
    }
}

fun interface IdentityVerifier {
    /** Returns the canonical lowercase public key derived from the private key. */
    fun derivePublicKeyHex(privateKeyHex: String): String
}

object LegacyStorageValidator {
    private val hex64 = Regex("^[0-9a-fA-F]{64}$")
    private val sha256 = Regex("^[0-9a-fA-F]{64}$")

    fun validate(
        snapshot: LegacyStorageSnapshot,
        identityVerifier: IdentityVerifier,
    ): SnapshotValidation {
        if (snapshot.schemaVersion != LegacySnapshotLimits.SUPPORTED_SCHEMA_VERSION) {
            return SnapshotValidation.failure(
                "Unsupported legacy snapshot schema: ${snapshot.schemaVersion}",
            )
        }

        if (snapshot.ordinaryValues.size > LegacySnapshotLimits.MAX_ENTRIES_PER_STORE ||
            snapshot.secureValues.size > LegacySnapshotLimits.MAX_ENTRIES_PER_STORE
        ) {
            return SnapshotValidation.failure("Legacy snapshot contains too many entries")
        }

        val unknownOrdinary = snapshot.ordinaryValues.keys.filterNot { key ->
            key in LegacyStorageContract.hiveKeys || LegacyStorageContract.isDynamicKey(key)
        }
        if (unknownOrdinary.isNotEmpty()) {
            return SnapshotValidation.failure(
                "Unknown Hive keys present: ${unknownOrdinary.sorted()}",
            )
        }

        val unknownSecure = snapshot.secureValues.keys - LegacyStorageContract.secureKeys
        if (unknownSecure.isNotEmpty()) {
            return SnapshotValidation.failure(
                "Unknown secure-storage keys present: ${unknownSecure.sorted()}",
            )
        }

        val leakedSecure = snapshot.ordinaryValues.keys
            .filter { it in LegacyStorageContract.secureKeys }
        if (leakedSecure.isNotEmpty()) {
            return SnapshotValidation.failure(
                "Sensitive keys were supplied as ordinary values: ${leakedSecure.sorted()}",
            )
        }


        for ((key, value) in snapshot.ordinaryValues) {
            if (key.toByteArray().size > LegacySnapshotLimits.MAX_KEY_BYTES ||
                value.toByteArray().size > LegacySnapshotLimits.MAX_ORDINARY_VALUE_BYTES
            ) {
                return SnapshotValidation.failure("Oversized ordinary value: $key")
            }
        }
        for ((key, value) in snapshot.secureValues) {
            if (key.toByteArray().size > LegacySnapshotLimits.MAX_KEY_BYTES ||
                value.toByteArray().size > LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES
            ) {
                return SnapshotValidation.failure("Oversized secure value: $key")
            }
        }

        val protectedState = LegacyProtectedStatePolicy.validateSecureShapes(snapshot.secureValues)
        if (!protectedState.valid) return protectedState

        val identity = snapshot.identity
        val identityBindings = LegacyProtectedStatePolicy.validateIdentityBindings(
            snapshot.secureValues,
            identity,
        )
        if (!identityBindings.valid) return identityBindings

        if (identity.publicKeyHex != null && !hex64.matches(identity.publicKeyHex)) {
            return SnapshotValidation.failure("Invalid stored public key")
        }
        if (identity.privateKeyHex != null && !hex64.matches(identity.privateKeyHex)) {
            return SnapshotValidation.failure("Invalid stored private key")
        }
        if (identity.flavor == "nsec" &&
            (identity.privateKeyHex == null || identity.publicKeyHex == null)
        ) {
            return SnapshotValidation.failure("nsec identity is incomplete")
        }
        if (identity.flavor == "amber" &&
            (identity.publicKeyHex == null || identity.privateKeyHex != null)
        ) {
            return SnapshotValidation.failure("Amber identity is incomplete")
        }
        if (identity.privateKeyHex != null) {
            val expected = identity.publicKeyHex
                ?: return SnapshotValidation.failure("Private key has no public-key binding")
            val derived = try {
                identityVerifier.derivePublicKeyHex(identity.privateKeyHex).lowercase()
            } catch (_: RuntimeException) {
                return SnapshotValidation.failure("Private key could not be validated")
            }
            if (derived != expected.lowercase()) {
                return SnapshotValidation.failure("Private/public key mismatch")
            }
        }

        if (snapshot.assets.size > LegacySnapshotLimits.MAX_ASSETS) {
            return SnapshotValidation.failure("Legacy snapshot contains too many assets")
        }
        val assetPaths = mutableSetOf<String>()
        for (asset in snapshot.assets) {
            if (!isSafeRelativePath(asset.relativePath)) {
                return SnapshotValidation.failure("Unsafe asset path: ${asset.relativePath}")
            }
            if (asset.relativePath.toByteArray().size > LegacySnapshotLimits.MAX_ASSET_PATH_BYTES) {
                return SnapshotValidation.failure("Asset path is too long")
            }
            if (!assetPaths.add(asset.relativePath)) {
                return SnapshotValidation.failure("Duplicate asset path: ${asset.relativePath}")
            }
            if (asset.sizeBytes !in 0..LegacySnapshotLimits.MAX_ASSET_SIZE_BYTES) {
                return SnapshotValidation.failure("Invalid asset size: ${asset.relativePath}")
            }
            if (!sha256.matches(asset.sha256)) {
                return SnapshotValidation.failure("Invalid asset checksum: ${asset.relativePath}")
            }
        }

        return SnapshotValidation.ok()
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
        if (path.contains('\\')) return false
        return path.split('/').none { it.isBlank() || it == "." || it == ".." }
    }
}

/**
 * Stable, order-independent digest for staged/native comparison.
 *
 * It is never a loggable identity and never replaces field-by-field
 * validation. Its purpose is to detect omissions or mutation between read,
 * stage and reopen without serializing secrets into diagnostics.
 */
object LegacySnapshotFingerprint {
    fun sha256(snapshot: LegacyStorageSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateInt(snapshot.schemaVersion)
        digest.updateMap(snapshot.ordinaryValues)
        digest.updateMap(snapshot.secureValues)
        digest.updateNullableString(snapshot.identity.publicKeyHex)
        digest.updateNullableString(snapshot.identity.flavor)
        digest.updateNullableString(snapshot.identity.privateKeyHex)
        val assets = snapshot.assets.sortedWith(
            compareBy(LegacyAsset::relativePath, LegacyAsset::sizeBytes, LegacyAsset::sha256),
        )
        digest.updateInt(assets.size)
        for (asset in assets) {
            digest.updateString(asset.relativePath)
            digest.updateLong(asset.sizeBytes)
            digest.updateString(asset.sha256.lowercase())
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun MessageDigest.updateMap(values: Map<String, String>) {
        updateInt(values.size)
        for ((key, value) in values.toSortedMap()) {
            updateString(key)
            updateString(value)
        }
    }

    private fun MessageDigest.updateNullableString(value: String?) {
        update(if (value == null) 0 else 1)
        if (value != null) updateString(value)
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

    private fun MessageDigest.updateLong(value: Long) {
        for (shift in 56 downTo 0 step 8) update((value ushr shift).toByte())
    }
}
