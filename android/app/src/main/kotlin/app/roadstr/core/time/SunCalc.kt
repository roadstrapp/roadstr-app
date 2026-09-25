package app.roadstr.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

data class SolarTimes(val rise: Instant?, val set: Instant?)

/** NOAA simplified sunrise/sunset algorithm used by the Flutter app. */
object SunCalc {
    fun sunTimes(latitude: Double, longitude: Double, date: LocalDate): SolarTimes {
        val dayOfYear = date.dayOfYear
        val b = 2 * PI * (dayOfYear - 1) / 365.0
        val declination = 0.006918 -
            0.399912 * cos(b) +
            0.070257 * sin(b) -
            0.006758 * cos(2 * b) +
            0.000907 * sin(2 * b) -
            0.002697 * cos(3 * b) +
            0.001480 * sin(3 * b)
        val equationOfTime = 229.18 * (
            0.000075 +
                0.001868 * cos(b) -
                0.032077 * sin(b) -
                0.014615 * cos(2 * b) -
                0.040890 * sin(2 * b)
            )
        val latitudeRadians = latitude * PI / 180.0
        val cosineZenith = cos(90.833 * PI / 180.0)
        val cosineHourAngle = (cosineZenith - sin(latitudeRadians) * sin(declination)) /
            (cos(latitudeRadians) * cos(declination))
        if (cosineHourAngle < -1 || cosineHourAngle > 1) return SolarTimes(null, null)

        val hourAngle = acos(cosineHourAngle) * 180.0 / PI
        val solarNoonMinutes = 720.0 - 4.0 * longitude - equationOfTime
        val riseSeconds = ((solarNoonMinutes - 4.0 * hourAngle) * 60).roundToLong()
        val setSeconds = ((solarNoonMinutes + 4.0 * hourAngle) * 60).roundToLong()
        val midnight = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        return SolarTimes(midnight.plusSeconds(riseSeconds), midnight.plusSeconds(setSeconds))
    }
}
