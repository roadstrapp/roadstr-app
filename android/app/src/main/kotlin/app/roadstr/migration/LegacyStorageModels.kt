package app.roadstr.migration

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
    private const val supportedSchemaVersion = 1
    private val hex64 = Regex("^[0-9a-fA-F]{64}$")
    private val sha256 = Regex("^[0-9a-fA-F]{64}$")

    fun validate(
        snapshot: LegacyStorageSnapshot,
        identityVerifier: IdentityVerifier,
    ): SnapshotValidation {
        if (snapshot.schemaVersion != supportedSchemaVersion) {
            return SnapshotValidation.failure(
                "Unsupported legacy snapshot schema: ${snapshot.schemaVersion}",
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

        val identity = snapshot.identity
        if (identity.flavor != null && identity.flavor !in setOf("amber", "nsec")) {
            return SnapshotValidation.failure("Unknown identity flavor: ${identity.flavor}")
        }
        if (identity.publicKeyHex != null && !hex64.matches(identity.publicKeyHex)) {
            return SnapshotValidation.failure("Invalid stored public key")
        }
        if (identity.privateKeyHex != null && !hex64.matches(identity.privateKeyHex)) {
            return SnapshotValidation.failure("Invalid stored private key")
        }
        if (identity.flavor == "nsec" && identity.privateKeyHex == null) {
            return SnapshotValidation.failure("nsec identity has no private key")
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

        for (asset in snapshot.assets) {
            if (!isSafeRelativePath(asset.relativePath)) {
                return SnapshotValidation.failure("Unsafe asset path: ${asset.relativePath}")
            }
            if (asset.sizeBytes < 0L) {
                return SnapshotValidation.failure("Negative asset size: ${asset.relativePath}")
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
