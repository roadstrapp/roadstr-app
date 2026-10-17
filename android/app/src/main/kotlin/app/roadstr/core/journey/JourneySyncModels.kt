package app.roadstr.core.journey

enum class JourneySyncPhase { IDLE, SYNCING, SYNCED, ERROR, SIGNER_REQUIRED, CRYPTO_UNAVAILABLE }

data class JourneySyncSnapshot(
    val phase: JourneySyncPhase = JourneySyncPhase.IDLE,
    val lastSuccessfulSyncEpochMillis: Long? = null,
    val localChangesPending: Boolean = false,
    val potentialOverlaps: Int = 0,
    val detail: String? = null,
)

sealed interface JourneySyncResult {
    data class Ok(
        val record: JourneyRecord,
        val completeDeviceIds: Set<String>,
        val potentialOverlaps: Int,
    ) : JourneySyncResult

    data object SignerRequired : JourneySyncResult
    data object CryptoUnavailable : JourneySyncResult
    data object RelayUnavailable : JourneySyncResult
    data object InvalidRemoteData : JourneySyncResult
}
