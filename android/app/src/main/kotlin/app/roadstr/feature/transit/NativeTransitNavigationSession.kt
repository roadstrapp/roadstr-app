package app.roadstr.feature.transit

import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitLeg
import app.roadstr.core.network.TransitResponsePoint
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeTransitNavigationStage {
    ApproachStop,
    WaitingToBoard,
    OnBoard,
    ApproachingAlightingStop,
    TransferWalk,
    WaitingForConnection,
    FinalWalk,
    Arrived,
    Suspended,
    DataUnavailable,
    MissedTrip,
    Ended,
}

enum class NativeTransitVoiceCueKind {
    ApproachStop,
    PrepareToBoard,
    BoardLine,
    PrepareToAlight,
    PrepareConnection,
    ContinueOnFoot,
    Arrived,
}

data class NativeTransitVoiceCue(
    val sequence: Long,
    val kind: NativeTransitVoiceCueKind,
    val line: String? = null,
    val stopName: String? = null,
)

data class NativeTransitNavigationCheckpoint(
    val itineraryId: String?,
    val revision: Long,
    val stage: NativeTransitNavigationStage,
    val legIndex: Int,
    val suspendedFrom: NativeTransitNavigationStage?,
    val verifySequence: Long,
    val replanSequence: Long,
    val intermediateStopIndex: Int = 0,
)

data class NativeTransitReplanContext(
    val providerId: String,
    val origin: TransitResponsePoint,
    val destination: TransitResponsePoint,
    val earliestDeparture: Instant,
)

data class NativeTransitNavigationSnapshot(
    val revision: Long,
    val active: Boolean,
    val stage: NativeTransitNavigationStage,
    val legIndex: Int,
    val line: String?,
    val direction: String?,
    val nextStop: String?,
    val alightingStop: String?,
    val stopsRemaining: Int?,
    val expectedTime: Instant?,
    val platform: String?,
    val nextLine: String?,
    val connectionStatus: NativeTransitConnectionStatus,
    val voiceCue: NativeTransitVoiceCue?,
    val verifySequence: Long,
    val replanSequence: Long,
) {
    companion object {
        fun idle() = NativeTransitNavigationSnapshot(
            revision = -1,
            active = false,
            stage = NativeTransitNavigationStage.Ended,
            legIndex = -1,
            line = null,
            direction = null,
            nextStop = null,
            alightingStop = null,
            stopsRemaining = null,
            expectedTime = null,
            platform = null,
            nextLine = null,
            connectionStatus = NativeTransitConnectionStatus.None,
            voiceCue = null,
            verifySequence = 0,
            replanSequence = 0,
        )
    }
}

/**
 * Public-transport guidance is deliberately separate from continuous road guidance.
 * GPS may advance walking approaches and suggest an upcoming alight, but boarding and
 * alighting only happen after explicit user confirmation.
 */
class NativeTransitNavigationSession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeTransitNavigationSnapshot.idle())
    private var itinerary: TransitItinerary? = null
    private var suspendedFrom: NativeTransitNavigationStage? = null
    private var cueSequence = 0L
    /** Number of intermediate calls already passed on the current transit leg. */
    private var intermediateStopIndex = 0

    val state: StateFlow<NativeTransitNavigationSnapshot> = _state.asStateFlow()

    fun start(revision: Long, itinerary: TransitItinerary): Boolean = synchronized(lock) {
        require(revision >= 0)
        if (_state.value.active || itinerary.transitLegs.isEmpty() || itinerary.legs.isEmpty()) return false
        this.itinerary = itinerary
        suspendedFrom = null
        intermediateStopIndex = 0
        val first = itinerary.legs.first()
        publish(
            revision = revision,
            stage = initialStage(first, itinerary),
            legIndex = 0,
            active = true,
            cue = if (first.mode.isTransit) NativeTransitVoiceCueKind.PrepareToBoard else NativeTransitVoiceCueKind.ApproachStop,
        )
        true
    }

    fun restore(
        checkpoint: NativeTransitNavigationCheckpoint,
        itinerary: TransitItinerary,
    ): Boolean = synchronized(lock) {
        if (
            checkpoint.revision < 0 || checkpoint.legIndex !in itinerary.legs.indices ||
            checkpoint.itineraryId != itinerary.id?.value || checkpoint.stage in TERMINAL_STAGES
        ) return false
        this.itinerary = itinerary
        suspendedFrom = checkpoint.suspendedFrom
        intermediateStopIndex = checkpoint.intermediateStopIndex.coerceIn(
            0,
            itinerary.legs[checkpoint.legIndex].intermediateStops.size,
        )
        _state.value = snapshot(
            revision = checkpoint.revision,
            stage = checkpoint.stage,
            legIndex = checkpoint.legIndex,
            active = true,
            voiceCue = null,
            verifySequence = checkpoint.verifySequence,
            replanSequence = checkpoint.replanSequence,
        )
        true
    }

    fun checkpoint(): NativeTransitNavigationCheckpoint? = synchronized(lock) {
        val current = _state.value
        if (!current.active) return null
        NativeTransitNavigationCheckpoint(
            itineraryId = itinerary?.id?.value,
            revision = current.revision,
            stage = current.stage,
            legIndex = current.legIndex,
            suspendedFrom = suspendedFrom,
            verifySequence = current.verifySequence,
            replanSequence = current.replanSequence,
            intermediateStopIndex = intermediateStopIndex,
        )
    }

    fun itineraryReference(): app.roadstr.core.network.TransitScopedId? = synchronized(lock) {
        itinerary?.id
    }

    fun replanContext(
        currentPosition: TransitResponsePoint?,
        now: Instant,
    ): NativeTransitReplanContext? = synchronized(lock) {
        val value = itinerary ?: return null
        val current = _state.value
        if (!current.active) return null
        val currentLeg = value.legs.getOrNull(current.legIndex) ?: return null
        val origin = currentPosition ?: currentLeg.to?.position ?: currentLeg.geometry.lastOrNull() ?: return null
        val destination = value.legs.lastOrNull()?.to?.position
            ?: value.legs.lastOrNull()?.geometry?.lastOrNull()
            ?: return null
        val providerId = value.id?.providerId
            ?: value.transitLegs.firstNotNullOfOrNull { it.tripId?.providerId ?: it.routeId?.providerId }
            ?: return null
        NativeTransitReplanContext(providerId, origin, destination, now)
    }

    /** Applies new realtime values only when they still describe the same scoped itinerary. */
    fun refreshSameItinerary(updated: TransitItinerary): Boolean = synchronized(lock) {
        val current = _state.value
        val existing = itinerary ?: return false
        if (!current.active || existing.id == null || updated.id != existing.id) return false
        itinerary = updated
        intermediateStopIndex = intermediateStopIndex.coerceAtMost(
            updated.legs.getOrNull(current.legIndex)?.intermediateStops?.size ?: 0,
        )
        _state.value = snapshot(
            revision = current.revision,
            stage = current.stage,
            legIndex = current.legIndex.coerceAtMost(updated.legs.lastIndex),
            active = true,
            voiceCue = null,
            verifySequence = current.verifySequence,
            replanSequence = current.replanSequence,
        )
        true
    }

    /** The caller must have shown alternatives and received an explicit user selection first. */
    fun replaceAfterConfirmation(revision: Long, replacement: TransitItinerary): Boolean = synchronized(lock) {
        val current = _state.value
        val replaceable = current.stage == NativeTransitNavigationStage.MissedTrip ||
            current.connectionStatus in setOf(
                NativeTransitConnectionStatus.AtRisk,
                NativeTransitConnectionStatus.Missed,
            )
        if (!current.active || !replaceable || replacement.transitLegs.isEmpty() || replacement.legs.isEmpty()) {
            return false
        }
        itinerary = replacement
        suspendedFrom = null
        intermediateStopIndex = 0
        publish(
            revision = revision,
            stage = initialStage(replacement.legs.first(), replacement),
            legIndex = 0,
            active = true,
            cue = if (replacement.legs.first().mode.isTransit) {
                NativeTransitVoiceCueKind.PrepareToBoard
            } else {
                NativeTransitVoiceCueKind.ApproachStop
            },
        )
        true
    }

    fun confirmBoarded(): Boolean = synchronized(lock) {
        val current = _state.value
        if (current.stage !in setOf(NativeTransitNavigationStage.WaitingToBoard, NativeTransitNavigationStage.WaitingForConnection)) {
            return false
        }
        val leg = itinerary?.legs?.getOrNull(current.legIndex) ?: return false
        if (!leg.mode.isTransit) return false
        publishCurrent(NativeTransitNavigationStage.OnBoard, cue = NativeTransitVoiceCueKind.BoardLine)
        true
    }

    fun confirmAlighted(): Boolean = synchronized(lock) {
        val current = _state.value
        if (current.stage !in setOf(NativeTransitNavigationStage.OnBoard, NativeTransitNavigationStage.ApproachingAlightingStop)) {
            return false
        }
        advanceAfterLeg(current.legIndex)
    }

    fun confirmWalkComplete(): Boolean = synchronized(lock) {
        val current = _state.value
        if (current.stage !in WALKING_STAGES) return false
        advanceAfterLeg(current.legIndex)
    }

    fun submitLocation(point: TransitResponsePoint, now: Instant): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.active || current.stage in TERMINAL_STAGES || current.stage == NativeTransitNavigationStage.Suspended) {
            return false
        }
        val leg = itinerary?.legs?.getOrNull(current.legIndex) ?: return false
        when (current.stage) {
            in WALKING_STAGES -> {
                val target = leg.to?.position ?: leg.geometry.lastOrNull() ?: return false
                if (distanceMeters(point, target) <= WALK_COMPLETION_METERS) {
                    return advanceAfterLeg(current.legIndex)
                }
            }
            NativeTransitNavigationStage.OnBoard -> {
                val stopProgressed = advanceIntermediateStops(leg, now, point)
                val target = leg.to?.position
                val closeByPosition = target != null && distanceMeters(point, target) <= ALIGHT_WARNING_METERS
                val closeByTime = Duration.between(now, leg.endTime).seconds in 0..ALIGHT_WARNING_SECONDS
                if (closeByPosition || closeByTime) {
                    publishCurrent(
                        NativeTransitNavigationStage.ApproachingAlightingStop,
                        cue = NativeTransitVoiceCueKind.PrepareToAlight,
                    )
                    return true
                }
                if (stopProgressed) return true
            }
            else -> Unit
        }
        false
    }

    fun tick(now: Instant): Boolean = synchronized(lock) {
        val current = _state.value
        if (current.stage != NativeTransitNavigationStage.OnBoard) return false
        val leg = itinerary?.legs?.getOrNull(current.legIndex) ?: return false
        val stopProgressed = advanceIntermediateStops(leg, now, null)
        val seconds = Duration.between(now, leg.endTime).seconds
        if (seconds !in 0..ALIGHT_WARNING_SECONDS) return stopProgressed
        publishCurrent(
            NativeTransitNavigationStage.ApproachingAlightingStop,
            cue = NativeTransitVoiceCueKind.PrepareToAlight,
        )
        true
    }

    fun markMissed(): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.active || current.stage in TERMINAL_STAGES) return false
        _state.value = snapshot(
            revision = current.revision,
            stage = NativeTransitNavigationStage.MissedTrip,
            legIndex = current.legIndex,
            active = true,
            voiceCue = null,
            verifySequence = current.verifySequence,
            replanSequence = current.replanSequence + 1,
        )
        true
    }

    fun requestVerification(): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.active || current.stage in TERMINAL_STAGES) return false
        _state.value = current.copy(verifySequence = current.verifySequence + 1)
        true
    }

    fun assessUpcomingConnection(now: Instant): NativeTransitConnectionAssessment = synchronized(lock) {
        val current = _state.value
        val value = itinerary ?: return NativeTransitConnectionAssessment(
            NativeTransitConnectionStatus.Unknown,
            null,
            null,
            false,
        )
        val completedIndex = when {
            value.legs.getOrNull(current.legIndex)?.mode?.isTransit == true -> current.legIndex
            else -> value.legs.indices.lastOrNull { index ->
                index < current.legIndex && value.legs[index].mode.isTransit
            }
        } ?: return NativeTransitConnectionAssessment(
            NativeTransitConnectionStatus.None,
            null,
            null,
            false,
        )
        val assessment = NativeTransitConnectionEvaluator.evaluateJourney(value, completedIndex, now = now)
        _state.value = current.copy(connectionStatus = assessment.status)
        assessment
    }

    fun suspend(): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.active || current.stage in TERMINAL_STAGES || current.stage == NativeTransitNavigationStage.Suspended) {
            return false
        }
        suspendedFrom = current.stage
        _state.value = current.copy(stage = NativeTransitNavigationStage.Suspended, voiceCue = null)
        true
    }

    fun resume(): Boolean = synchronized(lock) {
        val current = _state.value
        val restored = suspendedFrom ?: return false
        if (current.stage != NativeTransitNavigationStage.Suspended) return false
        suspendedFrom = null
        _state.value = current.copy(stage = restored)
        true
    }

    fun dataUnavailable(): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.active || current.stage in TERMINAL_STAGES) return false
        suspendedFrom = current.stage
        _state.value = current.copy(stage = NativeTransitNavigationStage.DataUnavailable, voiceCue = null)
        true
    }

    fun dataRestored(): Boolean = synchronized(lock) {
        val current = _state.value
        val restored = suspendedFrom ?: return false
        if (current.stage != NativeTransitNavigationStage.DataUnavailable) return false
        suspendedFrom = null
        _state.value = current.copy(stage = restored)
        true
    }

    fun stop(): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.active && current.stage != NativeTransitNavigationStage.Arrived) return false
        itinerary = null
        suspendedFrom = null
        intermediateStopIndex = 0
        _state.value = NativeTransitNavigationSnapshot.idle().copy(
            revision = current.revision,
            stage = NativeTransitNavigationStage.Ended,
        )
        true
    }

    private fun advanceAfterLeg(completedIndex: Int): Boolean {
        val value = itinerary ?: return false
        val nextIndex = completedIndex + 1
        if (nextIndex !in value.legs.indices) {
            publish(
                revision = _state.value.revision,
                stage = NativeTransitNavigationStage.Arrived,
                legIndex = completedIndex,
                active = false,
                cue = NativeTransitVoiceCueKind.Arrived,
            )
            return true
        }
        val next = value.legs[nextIndex]
        intermediateStopIndex = 0
        val hasPreviousTransit = value.legs.take(nextIndex).any { it.mode.isTransit }
        val laterTransit = value.legs.drop(nextIndex + 1).any { it.mode.isTransit }
        val stage = when {
            next.mode.isTransit && hasPreviousTransit -> NativeTransitNavigationStage.WaitingForConnection
            next.mode.isTransit -> NativeTransitNavigationStage.WaitingToBoard
            laterTransit -> NativeTransitNavigationStage.TransferWalk
            else -> NativeTransitNavigationStage.FinalWalk
        }
        val cue = when (stage) {
            NativeTransitNavigationStage.WaitingForConnection -> NativeTransitVoiceCueKind.PrepareConnection
            NativeTransitNavigationStage.TransferWalk -> NativeTransitVoiceCueKind.PrepareConnection
            NativeTransitNavigationStage.FinalWalk -> NativeTransitVoiceCueKind.ContinueOnFoot
            else -> null
        }
        publish(_state.value.revision, stage, nextIndex, active = true, cue = cue)
        return true
    }

    private fun publishCurrent(stage: NativeTransitNavigationStage, cue: NativeTransitVoiceCueKind?) {
        val current = _state.value
        publish(current.revision, stage, current.legIndex, active = true, cue = cue)
    }

    private fun publish(
        revision: Long,
        stage: NativeTransitNavigationStage,
        legIndex: Int,
        active: Boolean,
        cue: NativeTransitVoiceCueKind?,
    ) {
        val leg = itinerary?.legs?.getOrNull(legIndex)
        _state.value = snapshot(
            revision = revision,
            stage = stage,
            legIndex = legIndex,
            active = active,
            voiceCue = cue?.let {
                NativeTransitVoiceCue(
                    sequence = ++cueSequence,
                    kind = it,
                    line = leg?.displayLine,
                    stopName = leg?.to?.name ?: leg?.toName,
                )
            },
            verifySequence = _state.value.verifySequence,
            replanSequence = _state.value.replanSequence,
        )
    }

    private fun snapshot(
        revision: Long,
        stage: NativeTransitNavigationStage,
        legIndex: Int,
        active: Boolean,
        voiceCue: NativeTransitVoiceCue?,
        verifySequence: Long,
        replanSequence: Long,
    ): NativeTransitNavigationSnapshot {
        val value = itinerary
        val leg = value?.legs?.getOrNull(legIndex)
        val remaining = leg?.takeIf { it.mode.isTransit }?.let {
            (it.intermediateStops.size - intermediateStopIndex).coerceAtLeast(0) + 1
        }
        val nextCall = leg?.takeIf { it.mode.isTransit }?.intermediateStops?.getOrNull(intermediateStopIndex)
        val nextTransit = value?.legs?.drop(legIndex + 1)?.firstOrNull { it.mode.isTransit }
        val previousTransitIndex = value?.legs?.indices?.lastOrNull { index ->
            index < legIndex && value.legs[index].mode.isTransit
        }
        val connectionStatus = if (
            stage in setOf(
                NativeTransitNavigationStage.TransferWalk,
                NativeTransitNavigationStage.WaitingForConnection,
            ) && value != null && previousTransitIndex != null
        ) {
            NativeTransitConnectionEvaluator.evaluateJourney(value, previousTransitIndex).status
        } else {
            NativeTransitConnectionStatus.None
        }
        return NativeTransitNavigationSnapshot(
            revision = revision,
            active = active,
            stage = stage,
            legIndex = legIndex,
            line = leg?.displayLine,
            direction = leg?.headsign,
            nextStop = nextCall?.name ?: leg?.to?.name ?: leg?.toName,
            alightingStop = leg?.to?.name ?: leg?.toName,
            stopsRemaining = remaining,
            expectedTime = leg?.endTime,
            platform = leg?.to?.expectedPlatform ?: leg?.to?.scheduledPlatform,
            nextLine = nextTransit?.displayLine,
            connectionStatus = connectionStatus,
            voiceCue = voiceCue,
            verifySequence = verifySequence,
            replanSequence = replanSequence,
        )
    }

    private fun initialStage(first: TransitLeg, itinerary: TransitItinerary): NativeTransitNavigationStage = when {
        first.mode.isTransit -> NativeTransitNavigationStage.WaitingToBoard
        itinerary.legs.drop(1).any { it.mode.isTransit } -> NativeTransitNavigationStage.ApproachStop
        else -> NativeTransitNavigationStage.FinalWalk
    }

    /** Advances only the informational stop cursor; it never confirms alighting. */
    private fun advanceIntermediateStops(
        leg: TransitLeg,
        now: Instant,
        position: TransitResponsePoint?,
    ): Boolean {
        if (!leg.mode.isTransit || intermediateStopIndex >= leg.intermediateStops.size) return false
        var nextIndex = intermediateStopIndex
        while (nextIndex < leg.intermediateStops.size) {
            val stop = leg.intermediateStops[nextIndex]
            val stopTime = stop.expectedArrival ?: stop.scheduledArrival ?: stop.expectedDeparture ?: stop.scheduledDeparture
            val passedByTime = stopTime != null && now >= stopTime.plusSeconds(INTERMEDIATE_TIME_GRACE_SECONDS)
            val passedByPosition = position != null && stop.position?.let {
                distanceMeters(position, it) <= INTERMEDIATE_STOP_METERS
            } == true
            if (!passedByTime && !passedByPosition) break
            nextIndex++
        }
        if (nextIndex == intermediateStopIndex) return false
        intermediateStopIndex = nextIndex
        val current = _state.value
        _state.value = snapshot(
            revision = current.revision,
            stage = current.stage,
            legIndex = current.legIndex,
            active = current.active,
            voiceCue = current.voiceCue,
            verifySequence = current.verifySequence,
            replanSequence = current.replanSequence,
        )
        return true
    }

    private fun distanceMeters(left: TransitResponsePoint, right: TransitResponsePoint): Double {
        val lat1 = Math.toRadians(left.latitude)
        val lat2 = Math.toRadians(right.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(right.longitude - left.longitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 6_371_000.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private companion object {
        const val WALK_COMPLETION_METERS = 35.0
        const val ALIGHT_WARNING_METERS = 400.0
        const val ALIGHT_WARNING_SECONDS = 120L
        const val INTERMEDIATE_STOP_METERS = 80.0
        const val INTERMEDIATE_TIME_GRACE_SECONDS = 30L
        val WALKING_STAGES = setOf(
            NativeTransitNavigationStage.ApproachStop,
            NativeTransitNavigationStage.TransferWalk,
            NativeTransitNavigationStage.FinalWalk,
        )
        val TERMINAL_STAGES = setOf(
            NativeTransitNavigationStage.Arrived,
            NativeTransitNavigationStage.Ended,
        )
    }
}
