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

/** Small platform-neutral boundary for NIP-55/nsec adapters. */
interface NativeIdentityGateway {
    val state: StateFlow<NativeIdentitySnapshot>

    suspend fun loginNsec(encodedNsec: String): Boolean

    fun loginAmberPublicKey(encodedOrHex: String): Boolean = false

    suspend fun fetchProfileMetadata(pubkeyHex: String): NativeProfileMetadata? = null

    fun logout()
}
