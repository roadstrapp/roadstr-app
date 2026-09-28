package app.roadstr.service.navigation

enum class NativeNavigationEffect {
    StartGps,
    StopGps,
    EnableWakelock,
    DisableWakelock,
}

data class NativeNavigationRuntimeState(
    val navigationActive: Boolean,
    val appVisible: Boolean,
    val gpsRequested: Boolean,
    val gpsActive: Boolean,
    val wakelockRequired: Boolean,
    val lifecycleGeneration: Long,
    val idleStopAtMillis: Long?,
) {
    /** A foreground location service is needed for every active GPS stream. */
    val foregroundLocationRequired: Boolean
        get() = gpsActive
}

data class NativeNavigationTransition(
    val state: NativeNavigationRuntimeState,
    val effects: List<NativeNavigationEffect>,
)

/**
 * Engine-independent lifecycle policy for the GPS/navigation owner.
 *
 * The eventual Android service/Activity adapter applies the returned effects;
 * this class only preserves the Dart behavior: a 30-second background grace
 * period, GPS retention during navigation, generation-safe resume, and
 * foreground-only screen policy.
 */
class NativeNavigationLifecycle(
    private val backgroundGraceMillis: Long = BACKGROUND_GRACE_MILLIS,
    keepScreenOn: Boolean = true,
    keepScreenOnAlways: Boolean = false,
) {
    init {
        require(backgroundGraceMillis >= 0) { "Background grace must not be negative" }
    }

    private var navigationActive = false
    private var appVisible = true
    private var gpsRequested = false
    private var gpsActive = false
    private var lifecycleGeneration = 0L
    private var idleStopAtMillis: Long? = null
    private var keepScreenOn = keepScreenOn
    private var keepScreenOnAlways = keepScreenOnAlways
    private var wakelockEnabled = false

    val state: NativeNavigationRuntimeState
        get() = snapshot()

    fun requestGps(): NativeNavigationTransition {
        gpsRequested = true
        val effects = mutableListOf<NativeNavigationEffect>()
        if (!gpsActive && (appVisible || navigationActive)) {
            effects += NativeNavigationEffect.StartGps
        }
        syncWakelock(effects)
        return transition(effects)
    }

    fun onGpsStarted(): NativeNavigationTransition {
        gpsRequested = true
        gpsActive = true
        return transition()
    }

    fun onGpsStopped(): NativeNavigationTransition {
        gpsActive = false
        return transition()
    }

    fun startNavigation(): NativeNavigationTransition {
        navigationActive = true
        gpsRequested = true
        idleStopAtMillis = null
        val effects = mutableListOf<NativeNavigationEffect>()
        if (!gpsActive) effects += NativeNavigationEffect.StartGps
        syncWakelock(effects)
        return transition(effects)
    }

    fun stopNavigation(nowMillis: Long): NativeNavigationTransition {
        navigationActive = false
        if (!appVisible) idleStopAtMillis = nowMillis + backgroundGraceMillis
        val effects = mutableListOf<NativeNavigationEffect>()
        syncWakelock(effects)
        return transition(effects)
    }

    fun onAppPaused(nowMillis: Long): NativeNavigationTransition {
        appVisible = false
        lifecycleGeneration++
        if (!navigationActive) idleStopAtMillis = nowMillis + backgroundGraceMillis
        val effects = mutableListOf<NativeNavigationEffect>()
        syncWakelock(effects)
        return transition(effects)
    }

    fun onAppResumed(): NativeNavigationTransition {
        appVisible = true
        lifecycleGeneration++
        idleStopAtMillis = null
        val effects = mutableListOf<NativeNavigationEffect>()
        if (gpsRequested && !gpsActive) effects += NativeNavigationEffect.StartGps
        syncWakelock(effects)
        return transition(effects)
    }

    /** Applies a timer tick only when the caller's generation is still current. */
    fun onGracePeriodElapsed(nowMillis: Long, generation: Long = lifecycleGeneration): NativeNavigationTransition {
        if (generation != lifecycleGeneration || appVisible || navigationActive) {
            return transition()
        }
        val deadline = idleStopAtMillis ?: return transition()
        if (nowMillis < deadline) return transition()

        idleStopAtMillis = null
        gpsRequested = false
        val effects = mutableListOf<NativeNavigationEffect>()
        if (gpsActive) {
            gpsActive = false
            effects += NativeNavigationEffect.StopGps
        }
        syncWakelock(effects)
        return transition(effects)
    }

    fun onDetached(): NativeNavigationTransition {
        appVisible = false
        navigationActive = false
        gpsRequested = false
        idleStopAtMillis = null
        lifecycleGeneration++
        val effects = mutableListOf<NativeNavigationEffect>()
        if (gpsActive) {
            gpsActive = false
            effects += NativeNavigationEffect.StopGps
        }
        syncWakelock(effects)
        return transition(effects)
    }

    fun updateScreenPolicy(
        keepScreenOn: Boolean,
        keepScreenOnAlways: Boolean,
    ): NativeNavigationTransition {
        this.keepScreenOn = keepScreenOn
        this.keepScreenOnAlways = keepScreenOnAlways
        val effects = mutableListOf<NativeNavigationEffect>()
        syncWakelock(effects)
        return transition(effects)
    }

    private fun desiredWakelock(): Boolean =
        (navigationActive && keepScreenOn) || (appVisible && keepScreenOnAlways)

    private fun syncWakelock(effects: MutableList<NativeNavigationEffect>) {
        val desired = desiredWakelock()
        if (desired == wakelockEnabled) return
        wakelockEnabled = desired
        effects += if (desired) {
            NativeNavigationEffect.EnableWakelock
        } else {
            NativeNavigationEffect.DisableWakelock
        }
    }

    private fun transition(effects: List<NativeNavigationEffect> = emptyList()) =
        NativeNavigationTransition(snapshot(), effects.toList())

    private fun snapshot() = NativeNavigationRuntimeState(
        navigationActive = navigationActive,
        appVisible = appVisible,
        gpsRequested = gpsRequested,
        gpsActive = gpsActive,
        wakelockRequired = wakelockEnabled,
        lifecycleGeneration = lifecycleGeneration,
        idleStopAtMillis = idleStopAtMillis,
    )

    companion object {
        const val BACKGROUND_GRACE_MILLIS = 30_000L
    }
}
