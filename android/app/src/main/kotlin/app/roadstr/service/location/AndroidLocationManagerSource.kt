package app.roadstr.service.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
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
    }

    override suspend fun start(onLocation: (NativeRawLocation) -> Unit): Boolean {
        if (listener != null) return true
        if (!isLocationEnabled()) return false

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location.toNativeRawLocation())
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) listener = null
            }
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
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.toNativeRawLocation()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun Location.toNativeRawLocation(): NativeRawLocation = NativeRawLocation(
        latitude = latitude,
        longitude = longitude,
        speedMetersPerSecond = speed.toDouble(),
        accuracyMeters = accuracy.toDouble(),
        bearingDegrees = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasBearing()) {
            bearing.toDouble()
        } else {
            -1.0
        },
        altitudeMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAltitude()) {
            altitude
        } else {
            0.0
        },
        timestampMillis = time,
    )
}
