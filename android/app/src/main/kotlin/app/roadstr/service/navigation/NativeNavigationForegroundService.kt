package app.roadstr.service.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import app.roadstr.R
import app.roadstr.service.location.AndroidLocationManagerSource
import app.roadstr.service.location.NativeLocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Dormant Android adapter for the already-tested AOSP GPS coordinator.
 *
 * Nothing starts this service yet: Activity/ViewModel wiring is intentionally a
 * later migration step. Keeping the adapter explicit now lets that step use
 * the Android foreground contract without reimplementing GPS ownership.
 */
class NativeNavigationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var locationService: NativeLocationService? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createLocationChannel()
        locationService = NativeLocationService(
            source = AndroidLocationManagerSource(applicationContext, mainLooper),
            // The future native navigation/ViewModel owner consumes fixes.
            // The dormant adapter deliberately has no UI side effects.
            onFix = {},
            scope = serviceScope,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLocation()
            ACTION_STOP -> stopLocation()
            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runBlocking(Dispatchers.Default) {
            locationService?.dispose()
        }
        serviceScope.cancel()
        locationService = null
        super.onDestroy()
    }

    private fun startLocation() {
        if (foregroundStarted) return
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }
        try {
            startForeground(LOCATION_NOTIFICATION_ID, buildLocationNotification())
            foregroundStarted = true
        } catch (_: SecurityException) {
            stopSelf()
            return
        }

        val coordinator = locationService ?: return stopLocation()
        serviceScope.launch {
            if (!coordinator.start()) stopLocation()
        }
    }

    private fun stopLocation() {
        serviceScope.launch {
            locationService?.dispose()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            foregroundStarted = false
            stopSelf()
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun createLocationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            LOCATION_CHANNEL_ID,
            "Location",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Roadstr GPS activity"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildLocationNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, LOCATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Roadstr")
            .setContentText("GPS active")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        const val ACTION_START = "app.roadstr.action.START_NAVIGATION_GPS"
        const val ACTION_STOP = "app.roadstr.action.STOP_NAVIGATION_GPS"
        const val LOCATION_CHANNEL_ID = "roadstr_location"
        const val LOCATION_NOTIFICATION_ID = 41

        fun startIntent(context: android.content.Context): Intent =
            Intent(context, NativeNavigationForegroundService::class.java)
                .setAction(ACTION_START)

        fun stopIntent(context: android.content.Context): Intent =
            Intent(context, NativeNavigationForegroundService::class.java)
                .setAction(ACTION_STOP)
    }
}
