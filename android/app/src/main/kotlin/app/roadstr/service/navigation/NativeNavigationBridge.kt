package app.roadstr.service.navigation

/** Stable opt-in MethodChannel contract for the dormant native GPS service. */
object NativeNavigationBridge {
    const val CHANNEL = "app.roadstr/native_navigation"
    const val START_FOREGROUND_GPS = "startForegroundGps"
    const val STOP_FOREGROUND_GPS = "stopForegroundGps"
    const val ERROR_PERMISSION_DENIED = "permission_denied"
    const val ERROR_START_FAILED = "start_failed"

    fun isSupportedMethod(method: String): Boolean =
        method == START_FOREGROUND_GPS || method == STOP_FOREGROUND_GPS
}
