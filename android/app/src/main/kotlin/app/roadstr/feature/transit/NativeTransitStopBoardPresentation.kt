package app.roadstr.feature.transit

import app.roadstr.core.network.TransitDeparture
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitServiceState
import app.roadstr.core.network.TransitStopBoard
import app.roadstr.core.network.TransitStopReference
import app.roadstr.service.transit.NativeTransitData
import app.roadstr.service.transit.NativeTransitDataFailure
import app.roadstr.service.transit.NativeTransitDataResult
import app.roadstr.service.transit.NativeTransitDataUnavailable
import app.roadstr.service.transit.NativeTransitDataUnsupported
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeTransitStopBoardStatus {
    Hidden,
    Loading,
    Ready,
    Empty,
    Failure,
}

data class NativeTransitDeparturePresentation(
    val stableId: String,
    val mode: TransitMode,
    val line: String,
    val direction: String,
    val scheduledTimeLabel: String,
    val expectedTimeLabel: String,
    val delayMinutes: Long?,
    val platform: String?,
    val scheduledPlatform: String?,
    val realtime: Boolean,
    val state: TransitServiceState,
    val alertTitles: List<String>,
)

data class NativeTransitStopBoardSnapshot(
    val revision: Long,
    val status: NativeTransitStopBoardStatus,
    val stop: TransitStopReference?,
    val updatedAtLabel: String?,
    val departures: List<NativeTransitDeparturePresentation>,
) {
    companion object {
        const val NO_REVISION = -1L

        fun hidden() = NativeTransitStopBoardSnapshot(
            revision = NO_REVISION,
            status = NativeTransitStopBoardStatus.Hidden,
            stop = null,
            updatedAtLabel = null,
            departures = emptyList(),
        )
    }
}

object NativeTransitStopBoardPresenter {
    const val MAX_VISIBLE_DEPARTURES = 40

    fun present(
        board: TransitStopBoard,
        zoneId: ZoneId,
        locale: Locale,
    ): List<NativeTransitDeparturePresentation> {
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zoneId)
        return board.departures.take(MAX_VISIBLE_DEPARTURES).map { departure ->
            departure(departure, formatter)
        }
    }

    fun updatedAt(value: Instant, zoneId: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zoneId).format(value)

    private fun departure(
        value: TransitDeparture,
        formatter: DateTimeFormatter,
    ) = NativeTransitDeparturePresentation(
        stableId = "${value.tripId.providerId}:${value.tripId.value}:${value.scheduledTime}",
        mode = value.line?.mode ?: TransitMode.Other,
        line = value.line?.shortName ?: value.line?.longName ?: "—",
        direction = value.direction ?: "—",
        scheduledTimeLabel = formatter.format(value.scheduledTime),
        expectedTimeLabel = formatter.format(value.expectedTime),
        delayMinutes = value.delaySeconds?.let { seconds -> seconds / 60 },
        platform = value.expectedPlatform,
        scheduledPlatform = value.scheduledPlatform,
        realtime = value.realtime,
        state = value.state,
        alertTitles = value.alerts.map { it.header }.distinct(),
    )
}

/** Revision-safe owner; replacing a search cancels late board responses at the UI boundary. */
class NativeTransitStopBoardSession(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeTransitStopBoardSnapshot.hidden())
    private var revision = NativeTransitStopBoardSnapshot.NO_REVISION

    val state: StateFlow<NativeTransitStopBoardSnapshot> = _state.asStateFlow()

    fun begin(revision: Long, stop: TransitStopReference): Boolean = synchronized(lock) {
        require(revision >= 0)
        if (revision <= this.revision) return false
        this.revision = revision
        _state.value = NativeTransitStopBoardSnapshot(
            revision = revision,
            status = NativeTransitStopBoardStatus.Loading,
            stop = stop,
            updatedAtLabel = null,
            departures = emptyList(),
        )
        true
    }

    fun submit(revision: Long, result: NativeTransitDataResult<TransitStopBoard>): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status == NativeTransitStopBoardStatus.Hidden) return false
        _state.value = when (result) {
            is NativeTransitData -> {
                val rows = NativeTransitStopBoardPresenter.present(result.value, zoneId, locale)
                _state.value.copy(
                    status = if (rows.isEmpty()) NativeTransitStopBoardStatus.Empty else NativeTransitStopBoardStatus.Ready,
                    stop = result.value.stop,
                    updatedAtLabel = NativeTransitStopBoardPresenter.updatedAt(result.value.updatedAt, zoneId, locale),
                    departures = rows,
                )
            }
            is NativeTransitDataFailure,
            NativeTransitDataUnavailable,
            NativeTransitDataUnsupported,
            -> _state.value.copy(status = NativeTransitStopBoardStatus.Failure, departures = emptyList())
        }
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0)
        if (revision < this.revision) return false
        this.revision = revision
        _state.value = NativeTransitStopBoardSnapshot.hidden().copy(revision = revision)
        true
    }
}
