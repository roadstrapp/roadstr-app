package app.roadstr.roadtest

import android.content.Context
import android.util.Base64
import app.roadstr.core.protocol.nostr.NostrNip19
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.feature.profile.NativeIdentityGateway
import app.roadstr.feature.profile.NativeIdentitySnapshot
import app.roadstr.feature.profile.NativeProfileIdentityFlavor
import app.roadstr.feature.profile.NativeProfileMetadata
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Nsec login for road-test: the private key is always encrypted at rest. */
class NativeRoadTestIdentityGateway(context: Context) : NativeIdentityGateway {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val profileService = NativeRoadTestNostrProfileService()
    private val _state = MutableStateFlow(
        NativeIdentitySnapshot(
            pubkeyHex = preferences.getString(PUBLIC_KEY, null),
            flavor = NativeProfileIdentityFlavor.fromWire(preferences.getString(FLAVOR, null)),
        ),
    )

    override val state: StateFlow<NativeIdentitySnapshot> = _state.asStateFlow()

    override suspend fun loginNsec(encodedNsec: String): Boolean = runCatching {
        val privateHex = NostrNip19.decodePrivateKey(encodedNsec.trim())
        val publicHex = NostrSchnorr.publicKey(privateHex)
        val encrypted = encrypt(privateHex)
        check(preferences.edit()
            .putString(PUBLIC_KEY, publicHex)
            .putString(FLAVOR, NativeProfileIdentityFlavor.Nsec.wireValue)
            .putString(PRIVATE_KEY, encrypted)
            .commit()) { "Unable to persist the protected identity" }
        _state.value = NativeIdentitySnapshot(publicHex, NativeProfileIdentityFlavor.Nsec)
    }.isSuccess

    override fun loginAmberPublicKey(encodedOrHex: String): Boolean = runCatching {
        val candidate = encodedOrHex.trim()
        val publicHex = if (candidate.startsWith("npub1")) {
            NostrNip19.decodePublicKey(candidate)
        } else {
            require(candidate.matches(Regex("[0-9a-fA-F]{64}")))
            candidate.lowercase()
        }
        check(preferences.edit()
            .putString(PUBLIC_KEY, publicHex)
            .putString(FLAVOR, NativeProfileIdentityFlavor.Amber.wireValue)
            .remove(PRIVATE_KEY)
            .commit()) { "Unable to persist the identity" }
        _state.value = NativeIdentitySnapshot(publicHex, NativeProfileIdentityFlavor.Amber)
    }.isSuccess

    override suspend fun fetchProfileMetadata(pubkeyHex: String): NativeProfileMetadata? {
        val normalized = pubkeyHex.trim().lowercase()
        if (!normalized.matches(HEX_64)) return null
        val remote = profileService.fetch(normalized)
        if (remote != null) {
            preferences.edit()
                .putString(PROFILE_PUBKEY, normalized)
                .putString(PROFILE_NAME, remote.name)
                .putString(PROFILE_DISPLAY_NAME, remote.displayName)
                .putString(PROFILE_PICTURE, remote.pictureUrl)
                .apply()
            return remote
        }
        if (preferences.getString(PROFILE_PUBKEY, null) != normalized) return null
        return NativeProfileMetadata(
            name = preferences.getString(PROFILE_NAME, null),
            displayName = preferences.getString(PROFILE_DISPLAY_NAME, null),
            pictureUrl = preferences.getString(PROFILE_PICTURE, null),
        )
    }

    override fun logout() {
        val cleared = preferences.edit()
            .remove(PUBLIC_KEY)
            .remove(FLAVOR)
            .remove(PRIVATE_KEY)
            .remove(PROFILE_PUBKEY)
            .remove(PROFILE_NAME)
            .remove(PROFILE_DISPLAY_NAME)
            .remove(PROFILE_PICTURE)
            .commit()
        if (!cleared) return
        _state.value = NativeIdentitySnapshot()
    }

    /** Reserved for the signer adapter; it never exposes plaintext to preferences. */
    fun privateKeyHex(): String? = runCatching {
        val encoded = preferences.getString(PRIVATE_KEY, null) ?: return null
        decrypt(encoded)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size))
            .toString(StandardCharsets.UTF_8)
    }

    private companion object {
        const val PREFERENCES = "roadtest_identity"
        const val PUBLIC_KEY = "pubkey_hex"
        const val FLAVOR = "flavor"
        const val PRIVATE_KEY = "private_key_gcm"
        const val PROFILE_PUBKEY = "profile_pubkey"
        const val PROFILE_NAME = "profile_name"
        const val PROFILE_DISPLAY_NAME = "profile_display_name"
        const val PROFILE_PICTURE = "profile_picture"
        val HEX_64 = Regex("[0-9a-f]{64}")
        const val KEY_ALIAS = "roadtest_nostr_nsec"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
