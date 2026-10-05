package app.roadstr.feature.map

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Tilt-compensated compass azimuth from the accelerometer and the
 * magnetometer, ported from the Flutter map screen.
 *
 * It is only started while the map should follow the phone's own orientation
 * at a standstill outside navigation. In motion the heading comes from the GPS
 * course and the route: in a cradle or a pocket the magnetometer says where
 * the phone points, not where the car does. Everything stays on the device.
 */
class NativeCompassSensor(
    context: Context,
    private val onAzimuth: (Double) -> Unit,
) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var gravity: FloatArray? = null
    private var heading = 0.0
    private var lastEmitMillis = 0L
    private var started = false

    /** False on a phone without both sensors. */
    fun start(): Boolean {
        val sensors = manager ?: return false
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        val magnetometer = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) ?: return false
        if (started) return true
        started = sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI) &&
            sensors.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        return started
    }

    fun stop() {
        if (!started) return
        manager?.unregisterListener(this)
        started = false
        // A stale gravity vector paired with a fresh magnetometer sample would
        // tilt-compensate to the wrong azimuth for the first readings.
        gravity = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val acceleration = gravity ?: return
                val azimuth = azimuth(acceleration, event.values)
                if (!azimuth.isFinite()) return
                var difference = azimuth - heading
                while (difference > 180.0) difference -= 360.0
                while (difference < -180.0) difference += 360.0
                // Exponential low-pass with wrap-around: slow enough to hide
                // sensor noise, fast enough to feel responsive.
                heading = ((heading + difference * LOW_PASS_ALPHA) % 360.0 + 360.0) % 360.0
                // About 10 Hz: the raw stream would swamp the camera with more
                // moves than it can settle.
                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitMillis >= EMIT_INTERVAL_MILLIS) {
                    lastEmitMillis = now
                    onAzimuth(heading)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Degrees clockwise from north; keeps the previous heading when the vectors degenerate. */
    private fun azimuth(acceleration: FloatArray, magnetic: FloatArray): Double {
        var gx = -acceleration[0].toDouble()
        var gy = -acceleration[1].toDouble()
        var gz = -acceleration[2].toDouble()
        val gravityNorm = sqrt(gx * gx + gy * gy + gz * gz)
        if (gravityNorm < MIN_NORM) return heading
        gx /= gravityNorm
        gy /= gravityNorm
        gz /= gravityNorm

        val mx = magnetic[0].toDouble()
        val my = magnetic[1].toDouble()
        val mz = magnetic[2].toDouble()
        var ex = gy * mz - gz * my
        var ey = gz * mx - gx * mz
        var ez = gx * my - gy * mx
        val eastNorm = sqrt(ex * ex + ey * ey + ez * ez)
        if (eastNorm < MIN_NORM) return heading
        ex /= eastNorm
        ey /= eastNorm
        ez /= eastNorm

        val nz = ex * gy - ey * gx
        var degrees = Math.toDegrees(atan2(-ez, -nz))
        if (degrees < 0) degrees += 360.0
        return degrees
    }

    private companion object {
        const val LOW_PASS_ALPHA = 0.15
        const val EMIT_INTERVAL_MILLIS = 100L
        const val MIN_NORM = 0.1
    }
}
