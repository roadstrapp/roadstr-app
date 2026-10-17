package app.roadstr.core.journey

import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JourneySnapshot(
    val enabled: Boolean,
    val record: JourneyRecord,
    val statistics: JourneyStatistics,
    val achievements: List<JourneyAchievementState>,
    val progress: JourneyProgress,
    val activeSession: JourneySession?,
    val newlyUnlocked: List<JourneyAchievement> = emptyList(),
)

/** Thread-safe owner used by the process navigation runtime and the Compose dashboard. */
class JourneyController(
    initialRecord: JourneyRecord,
    private val deviceId: String,
    enabled: Boolean,
    private val persist: (JourneyRecord) -> Unit,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = ::newJourneyId,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val lock = Any()
    private var enabled = enabled
    private var record = recover(initialRecord)
    private var validator: JourneyDistanceValidator? = null
    private var lastCheckpointMeters = 0L
    private var lastCheckpointAt = 0L
    private val _state = MutableStateFlow(snapshot())

    val state: StateFlow<JourneySnapshot> = _state.asStateFlow()

    init {
        if (record != initialRecord) persist(record)
    }

    fun setEnabled(value: Boolean): Boolean = synchronized(lock) {
        if (enabled == value) return false
        if (!value) endActive(JourneySessionStatus.INTERRUPTED, nowEpochMillis())
        enabled = value
        publish(persistNow = false)
        true
    }

    fun start(mode: JourneyMode, startedAtEpochMillis: Long = nowEpochMillis()): JourneySession? = synchronized(lock) {
        if (!enabled || active() != null || startedAtEpochMillis < 0L) return null
        val session = JourneySession(
            id = newId(),
            deviceId = deviceId,
            mode = mode,
            startedAtEpochMillis = startedAtEpochMillis,
            endedAtEpochMillis = null,
            validatedMeters = 0L,
            uncertainMeters = 0L,
            status = JourneySessionStatus.ACTIVE,
        )
        record = record.copy(sessions = record.sessions + session)
        validator = JourneyDistanceValidator(mode)
        lastCheckpointMeters = 0L
        lastCheckpointAt = startedAtEpochMillis
        publish(persistNow = true)
        session
    }

    fun pause(): Boolean = synchronized(lock) {
        val current = active()?.takeIf { it.status == JourneySessionStatus.ACTIVE } ?: return false
        replace(current.copy(status = JourneySessionStatus.PAUSED))
        validator = null
        publish(persistNow = true)
        true
    }

    fun resume(): Boolean = synchronized(lock) {
        val current = active()?.takeIf { it.status == JourneySessionStatus.PAUSED } ?: return false
        replace(current.copy(status = JourneySessionStatus.ACTIVE))
        validator = JourneyDistanceValidator(current.mode)
        publish(persistNow = true)
        true
    }

    fun submit(sample: JourneyLocationSample): JourneyDistanceUpdate = synchronized(lock) {
        val current = active()?.takeIf { it.status == JourneySessionStatus.ACTIVE }
            ?: return JourneyDistanceUpdate(JourneyFixDecision.INVALID)
        val update = (validator ?: JourneyDistanceValidator(current.mode).also { validator = it }).accept(sample)
        if (update.validatedDeltaMeters == 0L && update.uncertainDeltaMeters == 0L) return update
        val updated = current.copy(
            validatedMeters = Math.addExact(current.validatedMeters, update.validatedDeltaMeters),
            uncertainMeters = Math.addExact(current.uncertainMeters, update.uncertainDeltaMeters),
        )
        replace(updated)
        val now = nowEpochMillis()
        val checkpoint = updated.validatedMeters - lastCheckpointMeters >= CHECKPOINT_METERS ||
            now - lastCheckpointAt >= CHECKPOINT_MILLIS
        if (checkpoint) {
            lastCheckpointMeters = updated.validatedMeters
            lastCheckpointAt = now
        }
        publish(persistNow = checkpoint)
        update
    }

    fun complete(endedAtEpochMillis: Long = nowEpochMillis()): JourneySession? = synchronized(lock) {
        endActive(JourneySessionStatus.COMPLETED, endedAtEpochMillis)
    }

    fun interrupt(endedAtEpochMillis: Long = nowEpochMillis()): JourneySession? = synchronized(lock) {
        endActive(JourneySessionStatus.INTERRUPTED, endedAtEpochMillis)
    }

    fun acknowledgeUnlocks(): Boolean = synchronized(lock) {
        if (_state.value.newlyUnlocked.isEmpty()) return false
        _state.value = _state.value.copy(newlyUnlocked = emptyList())
        true
    }

    /** Imports by session id; conflicting copies keep the most complete counters. */
    fun merge(imported: JourneyRecord): Boolean = synchronized(lock) {
        val byId = LinkedHashMap<String, JourneySession>()
        (record.sessions + imported.sessions).forEach { candidate ->
            val old = byId[candidate.id]
            if (old == null || quality(candidate) > quality(old)) byId[candidate.id] = candidate
        }
        val merged = record.copy(
            sessions = byId.values.sortedBy { it.startedAtEpochMillis },
            unlocks = (record.unlocks + imported.unlocks)
                .groupBy { it.achievementId }
                .map { (_, values) -> values.minBy { it.unlockedAtEpochMillis } },
        )
        if (merged == record) return false
        record = reconcile(merged, nowEpochMillis())
        publish(persistNow = true)
        true
    }

    /** Replaces only complete remote device shards; this device's unsynced history always wins locally. */
    fun reconcileSyncedDevices(imported: JourneyRecord, completeDeviceIds: Set<String>): Boolean = synchronized(lock) {
        val remoteDevices = completeDeviceIds - deviceId
        if (remoteDevices.isEmpty()) return false
        val replacements = imported.sessions.filter { it.deviceId in remoteDevices }
        val nextSessions = record.sessions.filterNot { it.deviceId in remoteDevices } + replacements
        val next = reconcile(record.copy(sessions = nextSessions.distinctBy { it.id }), nowEpochMillis())
        if (next == record) return false
        record = next
        publish(persistNow = true)
        true
    }

    fun replaceAfterCorrection(sessions: List<JourneySession>): Boolean = synchronized(lock) {
        if (sessions.any { it.status == JourneySessionStatus.ACTIVE || it.status == JourneySessionStatus.PAUSED }) {
            return false
        }
        record = reconcile(JourneyRecord(sessions.distinctBy { it.id }, record.unlocks), nowEpochMillis())
        validator = null
        publish(persistNow = true)
        true
    }

    fun clear(): Boolean = synchronized(lock) {
        if (active() != null) return false
        if (record.sessions.isEmpty() && record.unlocks.isEmpty()) return false
        record = JourneyRecord()
        publish(persistNow = true)
        true
    }

    private fun endActive(status: JourneySessionStatus, endedAt: Long): JourneySession? {
        val current = active() ?: return null
        val safeEnd = endedAt.coerceAtLeast(current.startedAtEpochMillis)
        val ended = current.copy(status = status, endedAtEpochMillis = safeEnd)
        replace(ended)
        validator = null
        val before = record.unlocks.mapTo(hashSetOf()) { it.achievementId }
        record = reconcile(record, safeEnd)
        val unlocked = AchievementCatalog.achievements.filter { it.id !in before &&
            record.unlocks.any { unlock -> unlock.achievementId == it.id }
        }
        publish(persistNow = true, newlyUnlocked = unlocked)
        return ended
    }

    private fun active(): JourneySession? = record.sessions.singleOrNull {
        it.status == JourneySessionStatus.ACTIVE || it.status == JourneySessionStatus.PAUSED
    }

    private fun replace(session: JourneySession) {
        record = record.copy(sessions = record.sessions.map { if (it.id == session.id) session else it })
    }

    private fun recover(value: JourneyRecord): JourneyRecord {
        val now = nowEpochMillis()
        val sessions = value.sessions.distinctBy { it.id }.map { session ->
            if (session.status == JourneySessionStatus.ACTIVE || session.status == JourneySessionStatus.PAUSED) {
                session.copy(
                    status = JourneySessionStatus.INTERRUPTED,
                    endedAtEpochMillis = now.coerceAtLeast(session.startedAtEpochMillis),
                )
            } else session
        }
        return reconcile(value.copy(sessions = sessions), now)
    }

    private fun reconcile(value: JourneyRecord, at: Long): JourneyRecord {
        val total = JourneyStatisticsCalculator.calculate(value.sessions, at, zoneId).totalMeters
        return value.copy(unlocks = AchievementCatalog.reconcile(total, value.unlocks, at))
    }

    private fun snapshot(newlyUnlocked: List<JourneyAchievement> = emptyList()): JourneySnapshot {
        val now = nowEpochMillis()
        val statistics = JourneyStatisticsCalculator.calculate(record.sessions, now, zoneId)
        return JourneySnapshot(
            enabled = enabled,
            record = record,
            statistics = statistics,
            achievements = AchievementCatalog.states(statistics.totalMeters, record.unlocks),
            progress = AchievementCatalog.progress(statistics.totalMeters),
            activeSession = active(),
            newlyUnlocked = newlyUnlocked,
        )
    }

    private fun publish(persistNow: Boolean, newlyUnlocked: List<JourneyAchievement> = _state.value.newlyUnlocked) {
        if (persistNow) persist(record)
        _state.value = snapshot(newlyUnlocked)
    }

    private fun quality(session: JourneySession): Long =
        Math.addExact(session.validatedMeters, session.uncertainMeters) * 10L +
            if (session.endedAtEpochMillis != null) 1L else 0L

    companion object {
        const val CHECKPOINT_METERS = 250L
        const val CHECKPOINT_MILLIS = 60_000L
    }
}
