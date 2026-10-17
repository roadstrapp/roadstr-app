package app.roadstr.core.journey

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

const val JOURNEY_FORMAT_VERSION = 1

enum class JourneyMode {
    CAR,
    MOTORCYCLE,
    BICYCLE,
    WALKING,
    TRANSIT,
    OTHER,
}

enum class JourneySessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    INTERRUPTED,
}

data class JourneySession(
    val id: String,
    val deviceId: String,
    val mode: JourneyMode,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val validatedMeters: Long,
    val uncertainMeters: Long,
    val status: JourneySessionStatus,
    val formatVersion: Int = JOURNEY_FORMAT_VERSION,
) {
    init {
        require(ID.matches(id)) { "Invalid journey session id" }
        require(DEVICE_ID.matches(deviceId)) { "Invalid journey device id" }
        require(startedAtEpochMillis >= 0L)
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis)
        require(validatedMeters >= 0L && uncertainMeters >= 0L)
        require(formatVersion == JOURNEY_FORMAT_VERSION)
        require(status == JourneySessionStatus.ACTIVE || status == JourneySessionStatus.PAUSED || endedAtEpochMillis != null)
    }

    companion object {
        private val ID = Regex("^[A-Za-z0-9._:-]{8,96}$")
        private val DEVICE_ID = Regex("^[A-Za-z0-9._:-]{8,96}$")
    }
}

data class JourneyAchievementUnlock(
    val achievementId: String,
    val unlockedAtEpochMillis: Long,
)

data class JourneyRecord(
    val sessions: List<JourneySession> = emptyList(),
    val unlocks: List<JourneyAchievementUnlock> = emptyList(),
    val formatVersion: Int = JOURNEY_FORMAT_VERSION,
)

data class JourneyStatistics(
    val totalMeters: Long,
    val completedTrips: Int,
    val monthMeters: Long,
    val yearMeters: Long,
    val metersByMode: Map<JourneyMode, Long>,
    val lastSession: JourneySession?,
)

object JourneyStatisticsCalculator {
    fun calculate(
        sessions: Iterable<JourneySession>,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): JourneyStatistics {
        require(nowEpochMillis >= 0L)
        val accepted = sessions.distinctBy { it.id }
            .filter { it.status != JourneySessionStatus.PAUSED || it.validatedMeters > 0L }
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        fun inMonth(session: JourneySession): Boolean {
            val time = Instant.ofEpochMilli(session.startedAtEpochMillis).atZone(zoneId)
            return time.year == now.year && time.month == now.month
        }
        fun inYear(session: JourneySession): Boolean =
            Instant.ofEpochMilli(session.startedAtEpochMillis).atZone(zoneId).year == now.year
        return JourneyStatistics(
            totalMeters = accepted.sumMeters { it.validatedMeters },
            completedTrips = accepted.count { it.status == JourneySessionStatus.COMPLETED },
            monthMeters = accepted.filter(::inMonth).sumMeters { it.validatedMeters },
            yearMeters = accepted.filter(::inYear).sumMeters { it.validatedMeters },
            metersByMode = JourneyMode.entries.associateWith { mode ->
                accepted.filter { it.mode == mode }.sumMeters { it.validatedMeters }
            },
            lastSession = accepted.maxByOrNull { it.endedAtEpochMillis ?: it.startedAtEpochMillis },
        )
    }

    private fun <T> Iterable<T>.sumMeters(selector: (T) -> Long): Long {
        var total = 0L
        for (value in this) total = Math.addExact(total, selector(value))
        return total
    }
}

fun newJourneyId(): String = UUID.randomUUID().toString()

