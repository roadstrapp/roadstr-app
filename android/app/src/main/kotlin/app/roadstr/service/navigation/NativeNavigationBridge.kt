package app.roadstr.service.navigation

data class NativeNavigationNotificationUpdate(
    val instruction: String,
    val distance: String,
)

/** Stable opt-in MethodChannel contract for the native GPS service canary. */
object NativeNavigationBridge {
    const val CHANNEL = "app.roadstr/native_navigation"
    const val FIXES_CHANNEL = "app.roadstr/native_navigation_fixes"
    const val START_FOREGROUND_GPS = "startForegroundGps"
    const val STOP_FOREGROUND_GPS = "stopForegroundGps"
    const val IS_FOREGROUND_GPS_RUNNING = "isForegroundGpsRunning"
    const val UPDATE_NAVIGATION_NOTIFICATION = "updateNavigationNotification"
    const val RESET_NAVIGATION_NOTIFICATION = "resetNavigationNotification"
    const val ERROR_PERMISSION_DENIED = "permission_denied"
    const val ERROR_START_FAILED = "start_failed"
    const val ERROR_INVALID_ARGUMENTS = "invalid_arguments"

    fun parseNotificationUpdate(arguments: Any?): NativeNavigationNotificationUpdate? {
        val values = arguments as? Map<*, *> ?: return null
        val instruction = values["instruction"] as? String ?: return null
        val distance = values["distance"] as? String ?: return null
        if (
            instruction.isBlank() ||
                instruction.length > MAX_INSTRUCTION_LENGTH ||
                distance.isBlank() ||
                distance.length > MAX_DISTANCE_LENGTH
        ) {
            return null
        }
        return NativeNavigationNotificationUpdate(instruction, distance)
    }

    fun isSupportedMethod(method: String): Boolean =
        method == START_FOREGROUND_GPS ||
            method == STOP_FOREGROUND_GPS ||
            method == IS_FOREGROUND_GPS_RUNNING ||
            method == UPDATE_NAVIGATION_NOTIFICATION ||
            method == RESET_NAVIGATION_NOTIFICATION

    private const val MAX_INSTRUCTION_LENGTH = 256
    private const val MAX_DISTANCE_LENGTH = 64
}
