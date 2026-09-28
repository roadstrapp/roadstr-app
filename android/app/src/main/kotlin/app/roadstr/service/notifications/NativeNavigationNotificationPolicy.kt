package app.roadstr.service.notifications

data class NativeNavigationNotification(
    val title: String,
    val body: String,
    val id: Int = NativeNavigationNotificationPolicy.NOTIFICATION_ID,
    val channelId: String = NativeNavigationNotificationPolicy.CHANNEL_ID,
    val channelName: String = NativeNavigationNotificationPolicy.CHANNEL_NAME,
    val ongoing: Boolean = true,
    val onlyAlertOnce: Boolean = true,
    val autoCancel: Boolean = false,
    val visibility: NativeNotificationVisibility = NativeNotificationVisibility.Private,
)

enum class NativeNotificationVisibility {
    Private,
}

/** Exact throttle boundary used by the native NotificationManager adapter. */
class NativeNotificationThrottle(
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
) {
    init {
        require(minIntervalMillis >= 0) { "Notification interval must not be negative" }
    }

    private var instruction: String? = null
    private var distance: String? = null
    private var postedAtMillis: Long? = null

    fun shouldPost(nextInstruction: String, nextDistance: String, nowMillis: Long): Boolean {
        val postedAt = postedAtMillis
        if (postedAt == null || nextInstruction != instruction) {
            record(nextInstruction, nextDistance, nowMillis)
            return true
        }
        if (nextDistance == distance) return false
        if (nowMillis - postedAt < minIntervalMillis) return false
        record(nextInstruction, nextDistance, nowMillis)
        return true
    }

    fun reset() {
        instruction = null
        distance = null
        postedAtMillis = null
    }

    private fun record(nextInstruction: String, nextDistance: String, nowMillis: Long) {
        instruction = nextInstruction
        distance = nextDistance
        postedAtMillis = nowMillis
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 3_000L
    }
}

/**
 * Builds private, low-noise navigation updates for the Android service adapter.
 */
class NativeNavigationNotificationPolicy(
    private val throttle: NativeNotificationThrottle = NativeNotificationThrottle(),
) {
    fun nextUpdate(
        instruction: String,
        distance: String,
        nowMillis: Long,
    ): NativeNavigationNotification? {
        if (!throttle.shouldPost(instruction, distance, nowMillis)) return null
        return NativeNavigationNotification(title = instruction, body = distance)
    }

    fun reset() = throttle.reset()

    companion object {
        const val CHANNEL_ID = "roadstr_navigation"
        const val CHANNEL_NAME = "Navigation"
        const val NOTIFICATION_ID = 42
    }
}
