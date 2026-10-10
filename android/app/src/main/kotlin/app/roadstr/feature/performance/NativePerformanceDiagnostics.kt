package app.roadstr.feature.performance

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Debug-only-in-practice counters. The host enables them only in a debuggable APK. */
class NativePerformanceDiagnostics {
    private val gpsFixes = AtomicLong()
    private val trackingTicks = AtomicLong()
    private val cameraUpdates = AtomicLong()
    private val layerUpdates = AtomicLong()
    private val sampleStartedNanos = AtomicLong(System.nanoTime())

    fun recordGpsFix() { gpsFixes.incrementAndGet() }
    fun recordTrackingTick() { trackingTicks.incrementAndGet() }
    fun recordCameraUpdate() { cameraUpdates.incrementAndGet() }
    fun recordLayerUpdate() { layerUpdates.incrementAndGet() }

    fun reset() {
        gpsFixes.set(0L)
        trackingTicks.set(0L)
        cameraUpdates.set(0L)
        layerUpdates.set(0L)
        sampleStartedNanos.set(System.nanoTime())
    }

    fun sample(profile: NativePerformanceProfile): NativePerformanceSample {
        val now = System.nanoTime()
        val elapsed = (now - sampleStartedNanos.getAndSet(now)).coerceAtLeast(1L) / 1_000_000_000.0
        return NativePerformanceSample(
            profile = profile.storageValue,
            elapsedSeconds = elapsed,
            gpsHertz = gpsFixes.getAndSet(0L) / elapsed,
            trackingHertz = trackingTicks.getAndSet(0L) / elapsed,
            cameraHertz = cameraUpdates.getAndSet(0L) / elapsed,
            layerHertz = layerUpdates.getAndSet(0L) / elapsed,
        )
    }
}

data class NativePerformanceSample(
    val profile: String,
    val elapsedSeconds: Double,
    val gpsHertz: Double,
    val trackingHertz: Double,
    val cameraHertz: Double,
    val layerHertz: Double,
) {
    fun asLogLine(): String = buildString {
        append("profile=").append(profile)
        append(" window_s=").append(String.format(Locale.ROOT, "%.1f", elapsedSeconds))
        append(" gps_hz=").append(String.format(Locale.ROOT, "%.2f", gpsHertz))
        append(" tracking_hz=").append(String.format(Locale.ROOT, "%.2f", trackingHertz))
        append(" camera_hz=").append(String.format(Locale.ROOT, "%.2f", cameraHertz))
        append(" layer_hz=").append(String.format(Locale.ROOT, "%.2f", layerHertz))
    }
}
