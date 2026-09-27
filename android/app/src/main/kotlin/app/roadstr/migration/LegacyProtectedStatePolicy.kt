package app.roadstr.migration

import java.util.Base64

/**
 * Shape and binding rules for protected legacy state.
 *
 * The policy never includes a protected value in a failure reason. Public
 * key names and mode labels are safe diagnostics; private keys, Hive bytes and
 * NWC material are not.
 */
internal object LegacyProtectedStatePolicy {
    private val hex64 = Regex("^[0-9a-fA-F]{64}$")
    private val canonicalHiveKey = Regex("^[A-Za-z0-9+/]{43}=$")

    fun validateSecureShapes(values: Map<String, String>): SnapshotValidation {
        val hiveKey = values[LegacyStorageContract.settingsEncryptionKey]
        if (hiveKey != null && !isCanonicalHiveKey(hiveKey)) {
            return SnapshotValidation.failure("Invalid protected Hive key")
        }

        val publicKey = values["nostr_pub_hex"]
        val privateKey = values["nostr_priv_hex"]
        val flavor = values["nostr_flavor"]
        if (publicKey != null && !hex64.matches(publicKey)) {
            return SnapshotValidation.failure("Invalid stored public key shape")
        }
        if (privateKey != null && !hex64.matches(privateKey)) {
            return SnapshotValidation.failure("Invalid stored private key shape")
        }
        if (flavor != null && flavor !in setOf("amber", "nsec")) {
            return SnapshotValidation.failure("Unsupported stored identity flavor")
        }

        val hasIdentity = publicKey != null || privateKey != null || flavor != null
        if (!hasIdentity) return SnapshotValidation.ok()
        if (flavor == null) {
            return SnapshotValidation.failure("Stored identity flavor is missing")
        }
        if (flavor == "nsec" && (publicKey == null || privateKey == null)) {
            return SnapshotValidation.failure("nsec identity is incomplete")
        }
        if (flavor == "amber" && (publicKey == null || privateKey != null)) {
            return SnapshotValidation.failure("Amber identity is incomplete")
        }
        return SnapshotValidation.ok()
    }

    fun validateIdentityBindings(
        secureValues: Map<String, String>,
        identity: LegacyIdentity,
    ): SnapshotValidation {
        val bindings = listOf(
            "nostr_pub_hex" to identity.publicKeyHex,
            "nostr_priv_hex" to identity.privateKeyHex,
            "nostr_flavor" to identity.flavor,
        )
        for ((key, identityValue) in bindings) {
            val secureValue = secureValues[key]
            if ((identityValue == null) != (secureValue == null)) {
                return SnapshotValidation.failure("Identity binding is incomplete")
            }
            if (identityValue != null && secureValue != null) {
                val equal = if (key == "nostr_flavor") {
                    identityValue == secureValue
                } else {
                    identityValue.equals(secureValue, ignoreCase = true)
                }
                if (!equal) return SnapshotValidation.failure("Identity binding mismatch")
            }
        }
        return SnapshotValidation.ok()
    }

    fun isCanonicalHiveKey(encoded: String): Boolean {
        if (!canonicalHiveKey.matches(encoded)) return false
        return try {
            val decoded = Base64.getDecoder().decode(encoded)
            decoded.size == 32 && Base64.getEncoder().encodeToString(decoded) == encoded
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
