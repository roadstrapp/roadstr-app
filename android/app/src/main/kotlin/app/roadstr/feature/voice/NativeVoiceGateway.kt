package app.roadstr.feature.voice

import java.io.Closeable
import kotlinx.coroutines.flow.StateFlow

enum class NativeVoiceRuntimeStatus { MissingAssets, Downloading, Ready, Speaking, Failed }

data class NativeVoiceRuntimeSnapshot(
    val status: NativeVoiceRuntimeStatus,
    val downloadFraction: Double = 0.0,
) {
    init {
        require(downloadFraction.isFinite() && downloadFraction in 0.0..1.0)
    }
}

/** Value-only boundary between the shared Compose shell and an Android voice owner. */
interface NativeVoiceGateway : Closeable {
    val state: StateFlow<NativeVoiceRuntimeSnapshot>

    fun configure(
        languageCode: String,
        gender: NativeVoiceGender,
        speed: Double,
        volume: Double,
    )

    fun downloadAssets()
    fun announceStart()
    fun announceManeuver(instruction: String, distanceMeters: Int, nowMillis: Long)
    fun announceArrival()
    fun setMuted(muted: Boolean)
    fun stop()
}
