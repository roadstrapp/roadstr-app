package app.roadstr.service.meshcore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The P-256 key this app pairs with MeshCore Open's local API. MeshCore Open remembers the public
 * key once the user allows it; the private key never leaves this app.
 */
interface LocalApiIdentity {
    /** The raw public key, x || y, 64 bytes: what the `hello` message carries. */
    fun publicKey(): ByteArray

    /** `SHA256withECDSA` over [message], DER-encoded. */
    fun sign(message: ByteArray): ByteArray

    companion object {
        /**
         * What MeshCore Open shows in its "Allow …?" dialog: the first 64 bits of SHA-256 over the raw
         * public key, as four groups of four upper-case hex digits ("AB12 CD34 5678 9ABC").
         */
        fun fingerprint(publicKey: ByteArray): String {
            val hex = MessageDigest.getInstance("SHA-256").digest(publicKey)
                .take(8).joinToString("") { "%02X".format(it) }
            return hex.chunked(4).joinToString(" ")
        }

        /** The 64-byte x || y form of an EC public key. */
        fun rawPublicKey(key: ECPublicKey): ByteArray {
            val raw = ByteArray(64)
            copyFixed(key.w.affineX.toByteArray(), raw, 0)
            copyFixed(key.w.affineY.toByteArray(), raw, 32)
            return raw
        }

        private fun copyFixed(source: ByteArray, target: ByteArray, offset: Int) {
            val start = if (source.size > 32) source.size - 32 else 0
            val length = source.size - start
            System.arraycopy(source, start, target, offset + 32 - length, length)
        }
    }
}

/**
 * The pairing key in the Android Keystore: generated on first use, never exportable, used only to
 * sign. Clearing the app's data (or uninstalling it) forgets it, and MeshCore Open asks again.
 */
class AndroidKeystoreLocalApiIdentity(private val alias: String) : LocalApiIdentity {
    private val lock = Any()
    private var cached: Pair<PrivateKey, ByteArray>? = null

    override fun publicKey(): ByteArray = key().second.copyOf()

    override fun sign(message: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(key().first)
        signer.update(message)
        return signer.sign()
    }

    private fun key(): Pair<PrivateKey, ByteArray> = synchronized(lock) {
        cached?.let { return it }
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val entry = store.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: run {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build(),
                )
            }.generateKeyPair()
            store.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        }
        val raw = LocalApiIdentity.rawPublicKey(entry.certificate.publicKey as ECPublicKey)
        return (entry.privateKey to raw).also { cached = it }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
    }
}
