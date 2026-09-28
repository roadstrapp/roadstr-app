package app.roadstr.service.notifications

sealed interface NativeNavigationNotificationCommand {
    data class Update(
        val instruction: String,
        val distance: String,
    ) : NativeNavigationNotificationCommand

    data object Reset : NativeNavigationNotificationCommand
}

fun interface NativeNavigationNotificationSubscription {
    fun cancel()
}

/** Process-local command boundary between the Flutter bridge and service. */
object NativeNavigationNotificationDispatcher {
    private class Entry(
        val handler: (NativeNavigationNotificationCommand) -> Unit,
    )

    private val lock = Any()
    private var active: Entry? = null

    internal fun attach(
        handler: (NativeNavigationNotificationCommand) -> Unit,
    ): NativeNavigationNotificationSubscription {
        val entry = Entry(handler)
        synchronized(lock) {
            active = entry
        }
        return NativeNavigationNotificationSubscription {
            synchronized(lock) {
                if (active === entry) active = null
            }
        }
    }

    internal fun dispatch(command: NativeNavigationNotificationCommand): Boolean {
        val entry = synchronized(lock) { active } ?: return false
        return try {
            entry.handler(command)
            true
        } catch (_: Exception) {
            false
        }
    }
}
