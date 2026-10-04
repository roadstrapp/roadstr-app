package app.roadstr.service.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/**
 * AOSP-only LocationManager source. It deliberately never asks for a fused
 * provider or a Google Play Services dependency; permission prompting remains
 * the responsibility of the lifecycle/UI owner.
 */
class AndroidLocationManagerSource(
    context: Context,
    private val looper: Looper = Looper.getMainLooper(),
    private val minTimeMillis: Long = 500L,
    private val minDistanceMeters: Float = 0f,
) : NativeLocationSource {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    override suspend fun isLocationEnabled(): Boolean = try {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        // Some location-less Android builds do not register the GPS provider.
        false
    }

    override suspend fun start(onLocation: (NativeRawLocation) -> Unit): Boolean {
        if (listener != null) return true
        if (!isLocationEnabled()) return false

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location.toNativeRawLocation())
            }

            // These three only gained default bodies in API 30. On Android
            // 7-10 the platform calls them as abstract methods, so a listener
            // that omits them can fail with AbstractMethodError.
            @Deprecated("Called only below API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            // The registration deliberately survives the provider being
            // switched off: LocationManager resumes delivery when it comes
            // back. Forgetting the listener here without removeUpdates left
            // the registration orphaned, so a watchdog restart added a second
            // one and stop() could never remove the first — GPS stayed on
            // after the host stopped.
            override fun onProviderDisabled(provider: String) = Unit
        }
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minTimeMillis,
                minDistanceMeters,
                newListener,
                looper,
            )
            listener = newListener
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override suspend fun stop() {
        val activeListener = listener ?: return
        listener = null
        try {
            locationManager.removeUpdates(activeListener)
        } catch (_: SecurityException) {
            // Permission may have been revoked while navigation was stopping.
        }
    }

    override suspend fun lastKnown(): NativeRawLocation? {
        if (!isLocationEnabled()) return null
        // Every enabled provider, not only GPS: on a cold start the satellite
        // provider has nothing cached, while the network or passive one often
        // holds a recent fix. Starting from nothing left the cursor and the
        // camera without a position until the first satellite fix.
        val providers = try {
            locationManager.getProviders(true)
        } catch (_: SecurityException) {
            return null
        }
        var best: NativeRawLocation? = null
        for (provider in providers) {
            val candidate = try {
                locationManager.getLastKnownLocation(provider)?.toNativeRawLocation()
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: continue
            if (NativeLocationPolicy.isBetterCachedFix(candidate, best)) best = candidate
        }
        return best
    }

    private fun Location.toNativeRawLocation(): NativeRawLocation = NativeRawLocation(
        latitude = latitude,
        longitude = longitude,
        speedMetersPerSecond = speed.toDouble(),
        accuracyMeters = accuracy.toDouble(),
        // hasBearing/hasAltitude exist since API 1; only the *Accuracy variants
        // need API 26. Gating these on O dropped the provider course on the
        // Android 7 devices this app still supports.
        bearingDegrees = if (hasBearing()) bearing.toDouble() else -1.0,
        altitudeMeters = if (hasAltitude()) altitude else 0.0,
        timestampMillis = time,
        provider = provider,
    )
}
