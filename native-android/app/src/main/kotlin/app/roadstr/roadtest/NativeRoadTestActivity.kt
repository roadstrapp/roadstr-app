package app.roadstr.roadtest

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roadstr.R
import app.roadstr.core.network.RoutingProviderConfigProtocol
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.feature.home.NativeRoadstrShell
import app.roadstr.feature.home.NativeShellGpsPhase
import app.roadstr.feature.home.NativeShellMode
import app.roadstr.feature.home.NativeShellNostr
import app.roadstr.feature.saved.NativeParkingPosition
import app.roadstr.feature.history.NativeRouteHistoryProtocol
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import app.roadstr.service.hazards.NativeOsmHazardService
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.nostr.ExecutorNativeScheduler
import app.roadstr.service.nostr.NativeActivityService
import app.roadstr.service.nostr.NativeFavoritesSyncService
import app.roadstr.service.nostr.NativeProfileVisibilityService
import app.roadstr.service.nostr.NativeRelayFetcher
import app.roadstr.service.nostr.NativeRelayPublisher
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.NativeUserReportsService
import app.roadstr.service.nostr.NativeZapService
import app.roadstr.service.nostr.NativeRelayConnectorSelector
import app.roadstr.service.nostr.OkHttpRelayConnector

/** Standalone Compose launcher for the side-by-side Kotlin road-test APK. */
class NativeRoadTestActivity : ComponentActivity() {
    private lateinit var locationController: NativeRoadTestLocationController
    // One transport for every network service: a single connection pool and
    // the same deadline and response-size policy for all of them.
    private val httpClient by lazy(LazyThreadSafetyMode.NONE) { NativeBoundedHttpClient() }
    private val journeyGateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestJourneyGateway(
            context = applicationContext,
            transport = httpClient,
            routingConfiguration = ::routingConfiguration,
        )
    }
    private val hazardService by lazy(LazyThreadSafetyMode.NONE) {
        NativeOsmHazardService(httpClient, diagnostics = { Log.d("RoadstrHazards", it) })
    }
    private val voiceGateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestVoiceGateway(applicationContext)
    }
    private val identityGateway by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestIdentityGateway(applicationContext)
    }
    private var amberRequestRevision = -1L
    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val candidates = listOfNotNull(
            data?.getStringExtra("signature"),
            data?.getStringExtra("result"),
            data?.data?.schemeSpecificPart,
        ).map(String::trim).filter(String::isNotEmpty)
        val loggedIn = result.resultCode == RESULT_OK &&
            candidates.any { identityGateway.loginAmberPublicKey(it) }
        if (!loggedIn) {
            Toast.makeText(this, R.string.native_roadtest_amber_failed, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        data?.getStringExtra("package")?.trim()?.takeIf(SIGNER_PACKAGE::matches)?.let { signerPackage ->
            amberPreferences.edit().putString("package", signerPackage).apply()
        }
    }
    // Signing and encryption round trips with the signer app, one at a time.
    private val amberSignerLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> amberBridge.deliver(result.resultCode == RESULT_OK, result.data) }
    private val amberPreferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences("roadtest_amber", MODE_PRIVATE)
    }
    private val amberBridge: NativeRoadTestAmberBridge = NativeRoadTestAmberBridge(
        resolver = { contentResolver },
        launch = { intent -> amberSignerLauncher.launch(intent) },
        signerPackage = { amberPreferences.getString("package", null)?.takeIf(SIGNER_PACKAGE::matches) },
    )
    private val favoriteFiles = NativeRoadTestFavoriteFiles(this)
    private val scheduler = ExecutorNativeScheduler()
    private var nostr: NativeShellNostr? = null
    private val onboardingPreferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences("roadtest_onboarding", MODE_PRIVATE)
    }
    private val parkingPreferences by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestProtectedPreferences(
            context = applicationContext,
            preferencesName = "roadtest_parking",
            keyAlias = "app.roadstr.roadtest.parking.v1",
        )
    }
    // Where the driver went is sensitive: AES-256-GCM under a non-exportable Keystore key.
    private val historyPreferences by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestProtectedPreferences(
            context = applicationContext,
            preferencesName = "roadtest_route_history",
            keyAlias = "app.roadstr.roadtest.route-history.v1",
        )
    }
    private val uiPreferences by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestUiPreferences(applicationContext)
    }
    private val favoritesStore by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestFavoritesStore(applicationContext)
    }
    private val nwcPreferences by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestProtectedPreferences(
            context = applicationContext,
            preferencesName = "roadtest_nwc",
            keyAlias = "app.roadstr.roadtest.nwc.v1",
        )
    }
    private val routingKeyPreferences by lazy(LazyThreadSafetyMode.NONE) {
        NativeRoadTestProtectedPreferences(
            context = applicationContext,
            preferencesName = "roadtest_routing",
            keyAlias = "app.roadstr.roadtest.routing.v1",
        )
    }
    // The key is decrypted once; routes are requested often and each read is a Keystore call.
    private var routingKeyCache: String? = null
    private var routingKeyLoaded = false
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        locationController.onPermissionResult(grants.values.any { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationController = NativeRoadTestLocationController(applicationContext)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        val nostrBridge = buildNostr().also { nostr = it }
        val initialFavorites = favoritesStore.load()
        val initialSettings = uiPreferences.load().copy(
            nwcConfigured = nwcPreferences.read("uri") != null,
            routingApiKeyConfigured = runCatching { routingKeyPreferences.read("api_key") }.getOrNull() != null,
            appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                .getOrNull().orEmpty().ifBlank { "—" },
            favoritesCount = initialFavorites.size,
            syncIdentityAvailable = identityGateway.privateKeyHex() != null,
        )
        setContent {
            val gpsSnapshot by locationController.state.collectAsStateWithLifecycle()
            NativeRoadstrShell(
                mode = NativeShellMode.RoadTest,
                gpsSnapshot = gpsSnapshot,
                journeyGateway = journeyGateway,
                voiceGateway = voiceGateway,
                onGpsAction = ::handleGpsAction,
                identityGateway = identityGateway,
                onAmberLogin = { revision -> launchAmber(revision) },
                onOpenExternal = ::openExternal,
                onboardingCompleted = onboardingPreferences.getBoolean("completed", false),
                onOnboardingCompleted = {
                    onboardingPreferences.edit().putBoolean("completed", true).apply()
                },
                initialParking = runCatching {
                    parkingPreferences.read("parking")?.let(NativeSavedPlacesProtocol::decodeParking)
                }.getOrNull(),
                onParkingChanged = { parking ->
                    if (parking == null) {
                        parkingPreferences.remove("parking")
                    } else {
                        parkingPreferences.write(
                            "parking",
                            NativeSavedPlacesProtocol.encodeParking(parking),
                        )
                    }
                },
                initialRouteHistory = runCatching {
                    NativeRouteHistoryProtocol.decode(historyPreferences.read("routes"))
                }.getOrDefault(emptyList()),
                onRouteHistoryChanged = { history ->
                    if (history.isEmpty()) {
                        historyPreferences.remove("routes")
                    } else {
                        historyPreferences.write("routes", NativeRouteHistoryProtocol.encode(history))
                    }
                },
                initialFavorites = initialFavorites,
                onFavoritesChanged = favoritesStore::save,
                initialSettings = initialSettings,
                onSettingsChanged = uiPreferences::save,
                onNwcChanged = ::saveNwc,
                onRoutingKeyChanged = ::saveRoutingKey,
                hazardService = hazardService,
                nostr = nostrBridge,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        locationController.onHostStart()
    }

    override fun onStop() {
        voiceGateway.stop()
        locationController.onHostStop()
        super.onStop()
    }

    override fun onDestroy() {
        nostr?.roadEvents?.close()
        scheduler.shutdown()
        locationController.close()
        voiceGateway.close()
        super.onDestroy()
    }

    /** One transport and one signer for every Nostr feature of the shell. */
    private fun buildNostr(): NativeShellNostr {
        // wss:// through OkHttp; a ws:// relay on the home network through the local connector.
        val connector = NativeRelayConnectorSelector(OkHttpRelayConnector())
        val relays = NativeRoadEventService.DEFAULT_RELAYS
        val publisher = NativeRelayPublisher(connector, relays)
        val signer = NativeRoadTestSigner(identityGateway, identityGateway::privateKeyHex, amberBridge)
        val syncStorage = NativeRoadTestSyncStorage(applicationContext)
        val profiles = NativeRoadTestNostrProfileService()
        val zaps = NativeZapService(connector, relays = relays) { Log.d("RoadstrZap", it) }
        val userReports = NativeUserReportsService(connector, relays)
        val activityStore = NativeRoadTestActivityStore(applicationContext)
        return NativeShellNostr(
            signer = signer,
            roadEvents = NativeRoadEventService(
                connector = connector,
                publisher = publisher,
                scheduler = scheduler,
                relays = relays,
                pendingStorage = NativeRoadTestPendingReports(applicationContext),
            ),
            favoritesSync = NativeFavoritesSyncService(
                signer = signer,
                connector = connector,
                store = syncStorage,
                passphrase = syncStorage::passphrase,
                customRelay = syncStorage::customRelay,
                fetcher = NativeRelayFetcher(connector) { Log.d("RoadstrSync", it) },
                publisherFactory = { relayUrls ->
                    NativeRelayPublisher(connector, relayUrls) { Log.d("RoadstrSync", it) }
                },
                diagnostics = { Log.d("RoadstrSync", it) },
            ),
            visibility = NativeProfileVisibilityService(
                signer = signer,
                publisher = publisher,
                fetcher = NativeRelayFetcher(connector),
                relays = relays,
            ),
            syncSecrets = syncStorage,
            files = favoriteFiles,
            profileLookup = profiles::fetch,
            reportPrivacyAcknowledged = { onboardingPreferences.getBoolean("report_privacy_ack", false) },
            acknowledgeReportPrivacy = {
                onboardingPreferences.edit().putBoolean("report_privacy_ack", true).apply()
            },
            zaps = zaps,
            userReports = userReports,
            activity = NativeActivityService(zaps, userReports, connector, activityStore, relays),
            activityStore = activityStore,
            nwcUri = { runCatching { nwcPreferences.read("uri") }.getOrNull() },
            openWallet = ::openExternal,
        )
    }

    private fun handleGpsAction() {
        when (locationController.state.value.phase) {
            NativeShellGpsPhase.PermissionRequired,
            NativeShellGpsPhase.PermissionDenied,
            -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            NativeShellGpsPhase.ProviderDisabled -> {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            NativeShellGpsPhase.Paused,
            NativeShellGpsPhase.Failed,
            NativeShellGpsPhase.Disabled,
            NativeShellGpsPhase.Starting,
            NativeShellGpsPhase.WaitingForFix,
            -> locationController.retry()
            NativeShellGpsPhase.Active,
            -> Unit
        }
    }

    private fun launchAmber(revision: Long) {
        amberRequestRevision = revision
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:login"))
            .putExtra("type", "get_public_key")
            .putExtra("uri_data", "login")
            .putExtra("permissions", "[{\"type\":\"sign_event\",\"int\":null}]")
        runCatching { amberLauncher.launch(intent) }
            .onFailure {
                Toast.makeText(this, R.string.native_roadtest_amber_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun openExternal(rawUri: String) {
        val uri = Uri.parse(rawUri)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val opened = runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
        if (opened) return
        if (uri.scheme.equals("lightning", ignoreCase = true)) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Roadstr", uri.schemeSpecificPart))
            Toast.makeText(this, R.string.native_roadtest_invoice_copied, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.native_roadtest_no_app, Toast.LENGTH_LONG).show()
        }
    }

    /** Provider, GraphHopper server and key as the user configured them. */
    private fun routingConfiguration(): RoutingProviderConfiguration {
        val settings = uiPreferences.load()
        if (!routingKeyLoaded) {
            routingKeyCache = runCatching { routingKeyPreferences.read("api_key") }.getOrNull()
            routingKeyLoaded = true
        }
        return RoutingProviderConfigProtocol.resolve(
            providerKey = settings.routingProvider.storageValue,
            secureApiKey = routingKeyCache,
            legacyApiKey = "",
            graphHopperServer = settings.graphHopperServer,
            deferCredentialReadForOsrm = false,
        )
    }

    private fun saveRoutingKey(raw: String): Boolean {
        val value = raw.trim()
        if (value.length > 256 || value.any { it.code < 0x20 || it.code == 0x7f }) return false
        val stored = if (value.isEmpty()) routingKeyPreferences.remove("api_key") else routingKeyPreferences.write("api_key", value)
        if (stored) routingKeyCache = value.ifEmpty { null }.also { routingKeyLoaded = true }
        return stored
    }

    private fun saveNwc(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty()) return nwcPreferences.remove("uri")
        if (value.length > 4_096 || value.any { it.code < 0x20 || it.code == 0x7f }) return false
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        if (!uri.scheme.equals("nostr+walletconnect", ignoreCase = true)) return false
        val walletPubkey = uri.host?.lowercase() ?: return false
        if (!walletPubkey.matches(Regex("[0-9a-f]{64}"))) return false
        val secret = uri.getQueryParameter("secret")?.lowercase() ?: return false
        if (!secret.matches(Regex("[0-9a-f]{64}"))) return false
        val relays = uri.getQueryParameters("relay")
        if (relays.isEmpty() || relays.any { relay ->
                val relayUri = runCatching { Uri.parse(relay) }.getOrNull()
                relayUri == null || !relayUri.scheme.equals("wss", true) || relayUri.host.isNullOrBlank()
            }
        ) return false
        return nwcPreferences.write("uri", value)
    }

    private companion object {
        /** An Android package name, as reported by the signer app. */
        val SIGNER_PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
