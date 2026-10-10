package app.roadstr.roadtest

import android.content.Context
import android.util.Base64
import app.roadstr.core.protocol.nostr.NostrBunkerUri
import app.roadstr.core.protocol.nostr.NostrConnectUri
import app.roadstr.core.protocol.nostr.NostrNip19
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.feature.profile.NativeBunkerLoginResult
import app.roadstr.feature.profile.NativeIdentityGateway
import app.roadstr.feature.profile.NativeIdentitySnapshot
import app.roadstr.feature.profile.NativeProfileIdentityFlavor
import app.roadstr.feature.profile.NativeProfileMetadata
import app.roadstr.service.nostr.NativeBunkerClient
import app.roadstr.service.nostr.NativeNostrConnectReceiver
import app.roadstr.service.nostr.NativeRelayConnector
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.OkHttpRelayConnector
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What it takes to talk to the remote signer of a bunker login; the key is this app's own, not the account's. */
class NativeBunkerSession(
    val clientKeyHex: String,
    val remotePubkeyHex: String,
    val relays: List<String>,
)

/**
 * Who is logged in: through the Amber signer app or through a remote signer (a "bunker"). The account's
 * private key never reaches this phone; the only key kept is the one that identifies this app to a bunker,
 * encrypted at rest. A private-key login of an earlier build is dropped on the first start.
 */
class NativeRoadTestIdentityGateway(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
    private val connector: NativeRelayConnector = OkHttpRelayConnector(),
) : NativeIdentityGateway {
    private val keyAlias = names.identityAlias
    private val preferences = context.getSharedPreferences(names.prefs("identity"), Context.MODE_PRIVATE)
    private val profileService = NativeRoadTestNostrProfileService()

    /** The signer wants the person to approve in a browser; the Activity opens the address. */
    @Volatile
    var openUrl: (String) -> Unit = {}

    init {
        dropPrivateKeyLogin()
    }

    private val _state = MutableStateFlow(
        NativeIdentitySnapshot(
            pubkeyHex = preferences.getString(PUBLIC_KEY, null),
            flavor = NativeProfileIdentityFlavor.fromWire(preferences.getString(FLAVOR, null)),
        ),
    )

    override val state: StateFlow<NativeIdentitySnapshot> = _state.asStateFlow()

    override suspend fun loginBunker(link: String): NativeBunkerLoginResult {
        val uri = NostrBunkerUri.parse(link) ?: return NativeBunkerLoginResult.InvalidLink
        val clientKey = newClientKey()
        val client = NativeBunkerClient(
            connector = connector,
            clientKeyHex = clientKey,
            remotePubkeyHex = uri.remotePubkeyHex,
            relays = uri.relays,
            onAuthUrl = { url -> openUrl(url) },
        )
        val params = if (uri.secret == null) {
            listOf(uri.remotePubkeyHex)
        } else {
            listOf(uri.remotePubkeyHex, uri.secret, REQUESTED_PERMISSIONS)
        }
        val connect = client.call("connect", params, NativeBunkerClient.CONNECT_TIMEOUT_MILLIS)
        if (!connect.ok) return failure(connect.error)
        if (connect.result != "ack" && connect.result != uri.secret) return NativeBunkerLoginResult.Refused
        val account = client.call("get_public_key", emptyList())
        val publicHex = account.result?.trim()?.lowercase()?.takeIf { account.ok && HEX_64.matches(it) }
            ?: return failure(account.error)
        return saveBunker(publicHex, clientKey, uri.remotePubkeyHex, uri.relays)
    }

    override suspend fun loginNostrConnect(openOffer: (String) -> Unit): NativeBunkerLoginResult {
        val clientKey = newClientKey()
        val clientPubkey = NostrSchnorr.publicKey(clientKey)
        val secret = NativeBunkerClient.randomRequestId()
        val relays = NativeRoadEventService.DEFAULT_RELAYS.take(NostrBunkerUri.MAX_RELAYS)
        val offer = NostrConnectUri.build(
            clientPubkeyHex = clientPubkey,
            relays = relays,
            secret = secret,
            permissions = REQUESTED_PERMISSIONS,
            name = "Roadstr",
            url = "https://github.com/roadstrapp/roadstr-app",
        )
        val remote = NativeNostrConnectReceiver(connector, clientKey, relays).awaitSigner(
            secret = secret,
            onOfferReady = { openOffer(offer) },
        ) ?: return NativeBunkerLoginResult.NoAnswer
        val client = NativeBunkerClient(
            connector = connector,
            clientKeyHex = clientKey,
            remotePubkeyHex = remote,
            relays = relays,
            onAuthUrl = { url -> openUrl(url) },
        )
        val account = client.call("get_public_key", emptyList())
        val publicHex = account.result?.trim()?.lowercase()?.takeIf { account.ok && HEX_64.matches(it) }
            ?: return failure(account.error)
        return saveBunker(publicHex, clientKey, remote, relays)
    }

    private fun saveBunker(
        publicHex: String,
        clientKey: String,
        remotePubkeyHex: String,
        relays: List<String>,
    ): NativeBunkerLoginResult {
        val saved = runCatching {
            check(preferences.edit()
                .putString(PUBLIC_KEY, publicHex)
                .putString(FLAVOR, NativeProfileIdentityFlavor.Bunker.wireValue)
                .putString(BUNKER_CLIENT_KEY, encrypt(clientKey))
                .putString(BUNKER_REMOTE, remotePubkeyHex)
                .putString(BUNKER_RELAYS, relays.joinToString("\n"))
                .remove(PRIVATE_KEY)
                .commit()) { "Unable to persist the identity" }
        }.isSuccess
        if (!saved) return NativeBunkerLoginResult.NoAnswer
        _state.value = NativeIdentitySnapshot(publicHex, NativeProfileIdentityFlavor.Bunker)
        return NativeBunkerLoginResult.Connected
    }

    private fun failure(error: String?) =
        if (error != null) NativeBunkerLoginResult.Refused else NativeBunkerLoginResult.NoAnswer

    /** The pairing with the remote signer, or null when the login is not a bunker one. */
    fun bunkerSession(): NativeBunkerSession? = runCatching {
        if (preferences.getString(FLAVOR, null) != NativeProfileIdentityFlavor.Bunker.wireValue) return null
        NativeBunkerSession(
            clientKeyHex = decrypt(preferences.getString(BUNKER_CLIENT_KEY, null) ?: return null),
            remotePubkeyHex = preferences.getString(BUNKER_REMOTE, null)?.takeIf(HEX_64::matches) ?: return null,
            relays = (preferences.getString(BUNKER_RELAYS, null) ?: return null).split('\n')
                .filter(NostrBunkerUri::isRelay),
        ).takeIf { it.relays.isNotEmpty() }
    }.getOrNull()

    private fun newClientKey(): String {
        val random = SecureRandom()
        while (true) {
            val candidate = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            if (runCatching { NostrSchnorr.publicKey(candidate) }.isSuccess) return candidate
        }
    }

    /**
     * An earlier build could hold the account's own private key. This one never does: the key is erased, the
     * person is logged out, and a notice is left so the next start says why.
     */
    private fun dropPrivateKeyLogin() {
        val legacy = preferences.contains(PRIVATE_KEY) ||
            preferences.getString(FLAVOR, null) == LEGACY_NSEC_FLAVOR
        if (!legacy) return
        preferences.edit()
            .remove(PUBLIC_KEY).remove(FLAVOR).remove(PRIVATE_KEY)
            .remove(PROFILE_PUBKEY).remove(PROFILE_NAME).remove(PROFILE_DISPLAY_NAME).remove(PROFILE_PICTURE)
            .putBoolean(LOGIN_NOTICE, true)
            .commit()
    }

    override fun consumeLoginNotice(): Boolean {
        if (!preferences.getBoolean(LOGIN_NOTICE, false)) return false
        preferences.edit().remove(LOGIN_NOTICE).apply()
        return true
    }

    /** Leaves a notice that a private-key login was not carried over from the old app. */
    fun noteLoginReset() {
        preferences.edit().putBoolean(LOGIN_NOTICE, true).commit()
    }

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
            .remove(PRIVATE_KEY).remove(BUNKER_CLIENT_KEY).remove(BUNKER_REMOTE).remove(BUNKER_RELAYS)
            .commit()) { "Unable to persist the identity" }
        _state.value = NativeIdentitySnapshot(publicHex, NativeProfileIdentityFlavor.Amber)
    }.isSuccess

    /**
     * Brings the identity the old app kept over, for an Amber login: there is no key to carry, only who it is.
     * The in-memory state is not touched: the activity creates this gateway after the import, and reads the
     * store then.
     */
    fun importIdentity(
        publicKeyHex: String,
        flavor: NativeProfileIdentityFlavor,
        name: String?,
        pictureUrl: String?,
    ): Boolean = runCatching {
        require(publicKeyHex.matches(HEX_64) && flavor == NativeProfileIdentityFlavor.Amber)
        val edit = preferences.edit()
            .putString(PUBLIC_KEY, publicKeyHex)
            .putString(FLAVOR, flavor.wireValue)
            .remove(PRIVATE_KEY)
        if (name != null || pictureUrl != null) {
            edit.putString(PROFILE_PUBKEY, publicKeyHex)
                .putString(PROFILE_NAME, name)
                .remove(PROFILE_DISPLAY_NAME)
                .putString(PROFILE_PICTURE, pictureUrl)
        }
        check(edit.commit()) { "Unable to persist the imported identity" }
    }.isSuccess

    /** Whether the stored identity is exactly this one. */
    fun holds(publicKeyHex: String, flavor: NativeProfileIdentityFlavor): Boolean =
        preferences.getString(PUBLIC_KEY, null) == publicKeyHex &&
            preferences.getString(FLAVOR, null) == flavor.wireValue

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
            .remove(BUNKER_CLIENT_KEY).remove(BUNKER_REMOTE).remove(BUNKER_RELAYS)
            .remove(PROFILE_PUBKEY)
            .remove(PROFILE_NAME)
            .remove(PROFILE_DISPLAY_NAME)
            .remove(PROFILE_PICTURE)
            .commit()
        if (!cleared) return
        _state.value = NativeIdentitySnapshot()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            keyAlias,
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
        const val PUBLIC_KEY = "pubkey_hex"
        const val FLAVOR = "flavor"
        const val PRIVATE_KEY = "private_key_gcm"
        const val BUNKER_CLIENT_KEY = "bunker_client_key_gcm"
        const val BUNKER_REMOTE = "bunker_remote_pubkey"
        const val BUNKER_RELAYS = "bunker_relays"
        const val LOGIN_NOTICE = "login_notice"
        const val LEGACY_NSEC_FLAVOR = "nsec"
        const val REQUESTED_PERMISSIONS = "get_public_key,sign_event,nip44_encrypt,nip44_decrypt"
        const val PROFILE_PUBKEY = "profile_pubkey"
        const val PROFILE_NAME = "profile_name"
        const val PROFILE_DISPLAY_NAME = "profile_display_name"
        const val PROFILE_PICTURE = "profile_picture"
        val HEX_64 = Regex("[0-9a-f]{64}")
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
