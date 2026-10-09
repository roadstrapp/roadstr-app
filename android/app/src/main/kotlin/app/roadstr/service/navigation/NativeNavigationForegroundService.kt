package app.roadstr.service.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import app.roadstr.R
import app.roadstr.service.location.AndroidLocationManagerSource
import app.roadstr.service.location.NativeLocationService
import app.roadstr.service.notifications.NativeNavigationNotification
import app.roadstr.service.notifications.NativeNavigationNotificationCommand
import app.roadstr.service.notifications.NativeNavigationNotificationDispatcher
import app.roadstr.service.notifications.NativeNavigationNotificationPolicy
import app.roadstr.service.notifications.NativeNavigationNotificationSubscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Android adapter for the already-tested AOSP GPS coordinator.
 *
 * Production screens can start it only through the opt-in navigation canary.
 * The runtime state lets a recreated Activity or renderer adopt or clean up an
 * existing service without issuing duplicate foreground commands.
 */
class NativeNavigationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val navigationNotificationPolicy = NativeNavigationNotificationPolicy()
    private var locationService: NativeLocationService? = null
    private var notificationSubscription: NativeNavigationNotificationSubscription? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        NativeNavigationServiceState.markForegroundGpsStopped()
        createLocationChannel()
        createNavigationChannel()
        notificationSubscription =
            NativeNavigationNotificationDispatcher.attach(::handleNavigationNotification)
        locationService = NativeLocationService(
            source = AndroidLocationManagerSource(applicationContext, mainLooper),
            // Canary consumers observe this process-local stream without
            // replacing the established Flutter GPS source yet.
            onFix = NativeNavigationFixDispatcher::publish,
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
        NativeNavigationServiceState.markForegroundGpsStopped()
        foregroundStarted = false
        notificationSubscription?.cancel()
        notificationSubscription = null
        navigationNotificationPolicy.reset()
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
            NativeNavigationServiceState.markForegroundGpsStopped()
            stopSelf()
            return
        }
        try {
            startForeground(LOCATION_NOTIFICATION_ID, buildLocationNotification())
            foregroundStarted = true
            NativeNavigationServiceState.markForegroundGpsStarted()
        } catch (_: SecurityException) {
            NativeNavigationServiceState.markForegroundGpsStopped()
            stopSelf()
            return
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException on Android 12+.
            NativeNavigationServiceState.markForegroundGpsStopped()
            stopSelf()
            return
        }

        val coordinator = locationService ?: return stopLocation()
        serviceScope.launch {
            if (!coordinator.start()) stopLocation()
        }
    }

    private fun stopLocation() {
        NativeNavigationServiceState.markForegroundGpsStopped()
        navigationNotificationPolicy.reset()
        serviceScope.launch {
            // stop(), not dispose(): a START can reach this same instance
            // before onDestroy (stop then immediate restart), and a disposed
            // coordinator would refuse it. onDestroy owns disposal.
            locationService?.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
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

    private fun createNavigationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NativeNavigationNotificationPolicy.CHANNEL_ID,
            NativeNavigationNotificationPolicy.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Roadstr turn-by-turn navigation"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun handleNavigationNotification(command: NativeNavigationNotificationCommand) {
        when (command) {
            is NativeNavigationNotificationCommand.Update -> {
                val update = navigationNotificationPolicy.nextUpdate(
                    instruction = command.instruction,
                    distance = command.distance,
                    nowMillis = System.currentTimeMillis(),
                ) ?: return
                try {
                    getSystemService(NotificationManager::class.java).notify(
                        update.id,
                        buildNavigationNotification(update),
                    )
                } catch (_: SecurityException) {
                    navigationNotificationPolicy.reset()
                }
            }

            NativeNavigationNotificationCommand.Reset -> {
                navigationNotificationPolicy.reset()
                getSystemService(NotificationManager::class.java).cancel(
                    NativeNavigationNotificationPolicy.NOTIFICATION_ID,
                )
            }
        }
    }

    private fun buildNavigationNotification(
        update: NativeNavigationNotification,
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, update.channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(update.title)
            .setContentText(update.body)
            .setOngoing(update.ongoing)
            .setOnlyAlertOnce(update.onlyAlertOnce)
            .setAutoCancel(update.autoCancel)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent())
            .build()
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
            .setContentIntent(contentIntent())
            .build()
    }

    /** Tapping either notification brings the app back to the front, on whichever launcher the build enables. */
    private fun contentIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
