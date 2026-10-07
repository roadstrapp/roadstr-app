package app.roadstr.feature.profile

import kotlinx.coroutines.flow.StateFlow

data class NativeIdentitySnapshot(
    val pubkeyHex: String? = null,
    val flavor: NativeProfileIdentityFlavor? = null,
) {
    val loggedIn: Boolean
        get() = pubkeyHex != null && flavor != null
}

data class NativeProfileMetadata(
    val name: String? = null,
    val displayName: String? = null,
    val pictureUrl: String? = null,
)

/** How a login through a remote signer ended. */
enum class NativeBunkerLoginResult {
    Connected,

    /** What was pasted is not a `bunker://` link with a secure relay. */
    InvalidLink,

    /** The signer refused, or answered with an error. */
    Refused,

    /** Nobody answered in time: the signer is offline or the request was not approved. */
    NoAnswer,
}

/** Small platform-neutral boundary for the two ways to log in: the Amber app (NIP-55) and a remote signer (NIP-46). */
interface NativeIdentityGateway {
    val state: StateFlow<NativeIdentitySnapshot>

    suspend fun loginBunker(link: String): NativeBunkerLoginResult = NativeBunkerLoginResult.InvalidLink

    /** True once after an update that dropped a private-key login; reading it clears it. */
    fun consumeLoginNotice(): Boolean = false

    fun loginAmberPublicKey(encodedOrHex: String): Boolean = false

    suspend fun fetchProfileMetadata(pubkeyHex: String): NativeProfileMetadata? = null

    fun logout()
}
