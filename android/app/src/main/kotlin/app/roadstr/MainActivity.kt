package app.roadstr

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import app.roadstr.service.navigation.NativeNavigationBridge
import app.roadstr.service.navigation.NativeNavigationForegroundService
import app.roadstr.service.navigation.NativeNavigationFixStreamHandler
import app.roadstr.service.navigation.NativeNavigationServiceState
import app.roadstr.service.notifications.NativeNavigationNotificationCommand
import app.roadstr.service.notifications.NativeNavigationNotificationDispatcher
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

// FlutterFragmentActivity is required by amberflutter (NIP-55 startActivityForResult).
class MainActivity : FlutterFragmentActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, GNSS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "primeAssistanceData" -> result.success(primeAssistanceData())
                    else -> result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NativeNavigationBridge.CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    NativeNavigationBridge.START_FOREGROUND_GPS -> startNativeForegroundGps(result)
                    NativeNavigationBridge.STOP_FOREGROUND_GPS -> stopNativeForegroundGps(result)
                    NativeNavigationBridge.IS_FOREGROUND_GPS_RUNNING ->
                        result.success(NativeNavigationServiceState.isForegroundGpsRunning())
                    NativeNavigationBridge.UPDATE_NAVIGATION_NOTIFICATION ->
                        updateNativeNavigationNotification(call.arguments, result)
                    NativeNavigationBridge.RESET_NAVIGATION_NOTIFICATION ->
                        resetNativeNavigationNotification(result)
                    else -> result.notImplemented()
                }
            }
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            NativeNavigationBridge.FIXES_CHANNEL,
        ).setStreamHandler(NativeNavigationFixStreamHandler())
    }

    private fun startNativeForegroundGps(result: MethodChannel.Result) {
        if (!hasLocationPermission()) {
            result.error(
                NativeNavigationBridge.ERROR_PERMISSION_DENIED,
                "Location permission must be granted before starting native GPS",
                null,
            )
            return
        }
        try {
            val intent = NativeNavigationForegroundService.startIntent(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            result.success(true)
        } catch (error: SecurityException) {
            result.error(
                NativeNavigationBridge.ERROR_START_FAILED,
                "Android rejected the native GPS foreground service",
                error.message,
            )
        }
    }

    private fun stopNativeForegroundGps(result: MethodChannel.Result) {
        stopService(NativeNavigationForegroundService.stopIntent(this))
        result.success(true)
    }

    private fun updateNativeNavigationNotification(
        arguments: Any?,
        result: MethodChannel.Result,
    ) {
        val update = NativeNavigationBridge.parseNotificationUpdate(arguments)
        if (update == null) {
            result.error(
                NativeNavigationBridge.ERROR_INVALID_ARGUMENTS,
                "Navigation notification requires bounded instruction and distance text",
                null,
            )
            return
        }
        if (!NativeNavigationServiceState.isForegroundGpsRunning()) {
            result.success(false)
            return
        }
        result.success(
            NativeNavigationNotificationDispatcher.dispatch(
                NativeNavigationNotificationCommand.Update(
                    instruction = update.instruction,
                    distance = update.distance,
                ),
            ),
        )
    }

    private fun resetNativeNavigationNotification(result: MethodChannel.Result) {
        if (!NativeNavigationServiceState.isForegroundGpsRunning()) {
            result.success(false)
            return
        }
        result.success(
            NativeNavigationNotificationDispatcher.dispatch(
                NativeNavigationNotificationCommand.Reset,
            ),
        )
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Asks the GNSS engine to refresh its assistance data (PSDS/XTRA) and its
     * clock.
     *
     * This is what makes a cold fix take seconds instead of a minute: without
     * predicted orbit data the receiver has to demodulate the almanac from the
     * satellites themselves, which is slow by physics, not by software.
     *
     * Deliberately *not* a Google dependency. The download URL lives in the
     * device's own /etc/gps.conf and points at the chipset vendor's service
     * (or, on privacy-focused ROMs, at that project's proxy) — this only asks
     * the platform to go fetch whatever it is already configured to use.
     *
     * Entirely best-effort: the commands are provider extensions, a device may
     * ignore them, and a device with no network will simply refuse. Failure is
     * silent because there is nothing the user could do about it and the
     * receiver still works, just slower.
     */
    private fun primeAssistanceData(): Boolean {
        val manager =
            getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        // "force_psds_injection" superseded the older XTRA name in API 30; try
        // the modern one first and fall back so older devices still benefit.
        val commands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf("force_psds_injection", "force_time_injection")
        } else {
            listOf("force_xtra_injection", "force_time_injection")
        }
        var accepted = false
        for (command in commands) {
            accepted = runCatching {
                manager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null)
            }.getOrDefault(false) || accepted
        }
        return accepted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PRIVACY — hide only the recents/app-switcher thumbnail, not screenshots.
        //
        // A blanket FLAG_SECURE was overkill: it also blocked users from taking a
        // normal screenshot (e.g. to share a route), which is a legitimate need.
        // The genuinely valuable, zero-downside protection is hiding the live
        // thumbnail Android snapshots for the task switcher — otherwise minimizing
        // the app leaves a picture of the map centered on the user's exact location
        // visible to anyone who opens recents (a fully involuntary "where is home"
        // leak). setRecentsScreenshotEnabled(false) suppresses exactly that while
        // leaving manual screenshots and screen recording enabled. API 33+ only;
        // on older versions there is no thumbnail-only API, so screenshots simply
        // stay fully enabled.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
    }

    private companion object {
        const val GNSS_CHANNEL = "app.roadstr/gnss"
    }
}
