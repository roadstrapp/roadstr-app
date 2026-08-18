package app.roadstr

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
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
    }

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
