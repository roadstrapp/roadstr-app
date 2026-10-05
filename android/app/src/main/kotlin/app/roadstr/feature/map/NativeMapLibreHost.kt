package app.roadstr.feature.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.roadstr.R
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.core.ui.theme.RoadstrThemeTokens
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

internal object NativeMapHostContract {
    const val MAPLIBRE_VERSION = "13.5.2"
    // ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW is deprecated at API 35,
    // but its stable numeric protocol value is still delivered to callbacks.
    const val RUNNING_LOW_MEMORY_LEVEL = 10
    const val INITIAL_LATITUDE = 42.5
    const val INITIAL_LONGITUDE = 12.5
    const val INITIAL_ZOOM = 17.0
    const val INITIAL_TILT = 40.0
}

/** MapLibre Native host used only by the private native canary Activity. */
@Composable
fun NativeMapLibreHost(
    dark: Boolean,
    mapEngine: NativeMapEngine,
    tileUrl: String,
    routeOverlay: NativeRouteOverlaySnapshot,
    transitOverlay: NativeTransitOverlaySnapshot,
    cameraCommand: NativeMapCameraCommand?,
    cursorSnapshot: NativeMapCursorSnapshot,
    pointOverlay: NativeMapPointOverlaySnapshot,
    onCameraGesture: () -> Unit,
    onMapInteraction: (NativeMapInteraction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engineProfile = remember(mapEngine) { NativeMapEngineCompatibility.profile(mapEngine) }
    val styleJson = remember(dark, mapEngine, tileUrl) {
        NativeMapEngineCompatibility.rasterStyle(mapEngine, dark, tileUrl)
    }
    val mapDescription = stringResource(R.string.native_map_description)
    val currentCameraGesture = rememberUpdatedState(onCameraGesture)
    val currentMapInteraction = rememberUpdatedState(onMapInteraction)
    val host = remember(context, lifecycleOwner, engineProfile) {
        NativeMapLibreViewHost.create(
            context = context,
            dark = dark,
            engineProfile = engineProfile,
            initialStyleJson = styleJson,
            initialRouteOverlay = routeOverlay,
            initialTransitOverlay = transitOverlay,
            initialCameraCommand = cameraCommand,
            initialCursorSnapshot = cursorSnapshot,
            initialPointOverlay = pointOverlay,
            onCameraGesture = { currentCameraGesture.value() },
            onMapInteraction = { currentMapInteraction.value(it) },
        )
    }

    LaunchedEffect(host, styleJson) {
        host.updateStyle(styleJson)
    }
    LaunchedEffect(host, routeOverlay) {
        host.updateRouteOverlay(routeOverlay)
    }
    LaunchedEffect(host, transitOverlay) {
        host.updateTransitOverlay(transitOverlay)
    }
    LaunchedEffect(host, cameraCommand) {
        host.updateCamera(cameraCommand)
    }
    LaunchedEffect(host, cursorSnapshot) {
        host.updateCursor(cursorSnapshot)
    }
    LaunchedEffect(host, pointOverlay) {
        host.updatePointOverlay(pointOverlay)
    }

    DisposableEffect(host, lifecycleOwner) {
        val application = context.applicationContext
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> host.lifecycle.handle(NativeMapLifecycleEvent.Create)
                Lifecycle.Event.ON_START -> host.lifecycle.handle(NativeMapLifecycleEvent.Start)
                Lifecycle.Event.ON_RESUME -> host.lifecycle.handle(NativeMapLifecycleEvent.Resume)
                Lifecycle.Event.ON_PAUSE -> host.lifecycle.handle(NativeMapLifecycleEvent.Pause)
                Lifecycle.Event.ON_STOP -> host.lifecycle.handle(NativeMapLifecycleEvent.Stop)
                Lifecycle.Event.ON_DESTROY -> host.dispose()
                Lifecycle.Event.ON_ANY -> Unit
            }
        }
        val memoryCallbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() {
                host.lifecycle.handle(NativeMapLifecycleEvent.LowMemory)
            }

            override fun onTrimMemory(level: Int) {
                if (level >= NativeMapHostContract.RUNNING_LOW_MEMORY_LEVEL) {
                    host.lifecycle.handle(NativeMapLifecycleEvent.LowMemory)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        application.registerComponentCallbacks(memoryCallbacks)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            application.unregisterComponentCallbacks(memoryCallbacks)
            host.dispose()
        }
    }

    // A new engine builds a new host, and AndroidView only calls its factory
    // once per node. Keyed by the host, the old view is dropped with its
    // disposed MapView and the new one is attached; without the key the screen
    // kept showing the destroyed map and every camera call hit a dead MapView.
    key(host) {
        AndroidView(
            factory = { host.rootView },
            modifier = modifier.semantics { contentDescription = mapDescription },
        )
    }
}

private class NativeMapLibreViewHost private constructor(
    val rootView: FrameLayout,
    val mapView: MapView,
    val lifecycle: NativeMapLifecycleController,
    private val routeRenderer: NativeMapRouteRenderer,
    private val transitRenderer: NativeMapTransitRenderer,
    private val cameraRenderer: NativeMapCameraRenderer,
    private val cursorOverlay: NativeMapCursorOverlayView,
    private val pointOverlay: NativeMapPointOverlayView,
    private val engineProfile: NativeMapEngineProfile,
    private val onCameraGesture: () -> Unit,
    private val onMapInteraction: (NativeMapInteraction) -> Unit,
    initialCameraCommand: NativeMapCameraCommand?,
    initialCursorSnapshot: NativeMapCursorSnapshot,
    initialPointOverlay: NativeMapPointOverlaySnapshot,
    initialStyleJson: String,
) {
    private var active = true
    private var map: MapLibreMap? = null
    private var desiredStyleJson = initialStyleJson
    private var requestedStyleJson: String? = null
    private val styleGeneration = NativeMapStyleGenerationGate()
    private val cameraMoveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
        if (active && reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            onCameraGesture()
        }
    }
    private val cameraMoveListener = MapLibreMap.OnCameraMoveListener {
        if (active) {
            pointOverlay.refreshProjection()
            cursorOverlay.refreshProjection()
        }
    }
    private val mapClickListener = MapLibreMap.OnMapClickListener { tap ->
        if (!active) {
            false
        } else {
            val point = NativeMapPoint(tap.latitude, tap.longitude)
            val marker = if (pointOverlay.revision >= 0) {
                pointOverlay.hitRoadEvent(tap)
            } else {
                null
            }
            onMapInteraction(
                NativeMapInteractionPolicy.tap(
                    point = point,
                    marker = marker,
                    markerRevision = pointOverlay.revision,
                ),
            )
            true
        }
    }
    private val mapLongClickListener = MapLibreMap.OnMapLongClickListener { tap ->
        if (!active) {
            false
        } else {
            onMapInteraction(
                NativeMapInteractionPolicy.longPress(
                    NativeMapPoint(tap.latitude, tap.longitude),
                ),
            )
            true
        }
    }

    init {
        lifecycle.handle(NativeMapLifecycleEvent.Create)
        cameraRenderer.update(initialCameraCommand)
        cursorOverlay.update(initialCursorSnapshot)
        pointOverlay.update(initialPointOverlay)
        mapView.getMapAsync { readyMap ->
            if (!active) return@getMapAsync
            map = readyMap
            readyMap.setMinZoomPreference(engineProfile.minimumZoom)
            readyMap.setMaxZoomPreference(engineProfile.maximumZoom)
            readyMap.setMinPitchPreference(0.0)
            readyMap.setMaxPitchPreference(engineProfile.maximumPitchDegrees)
            readyMap.uiSettings.setTiltGesturesEnabled(engineProfile.tiltGesturesEnabled)
            // The map is embedded in Roadstr's own chrome. MapLibre's logo and
            // attribution affordances otherwise remain visible in the corner
            // and overlap the native controls.
            readyMap.uiSettings.setLogoEnabled(false)
            readyMap.uiSettings.setAttributionEnabled(false)
            readyMap.uiSettings.setCompassEnabled(false)
            readyMap.addOnCameraMoveStartedListener(cameraMoveStartedListener)
            readyMap.addOnCameraMoveListener(cameraMoveListener)
            readyMap.addOnMapClickListener(mapClickListener)
            readyMap.addOnMapLongClickListener(mapLongClickListener)
            cameraRenderer.attach(readyMap)
            pointOverlay.attach(readyMap)
            cursorOverlay.attach(readyMap)
            applyStyleIfNeeded()
        }
    }

    fun updateStyle(styleJson: String) {
        if (!active || (styleJson == desiredStyleJson && styleJson == requestedStyleJson)) return
        desiredStyleJson = styleJson
        applyStyleIfNeeded()
    }

    fun updateRouteOverlay(snapshot: NativeRouteOverlaySnapshot) {
        if (!active) return
        routeRenderer.update(snapshot)
    }

    fun updateTransitOverlay(snapshot: NativeTransitOverlaySnapshot) {
        if (!active) return
        transitRenderer.update(snapshot)
    }

    fun updateCamera(command: NativeMapCameraCommand?) {
        if (!active) return
        cameraRenderer.update(command)
    }

    fun updateCursor(snapshot: NativeMapCursorSnapshot) {
        if (!active) return
        cursorOverlay.update(snapshot)
    }

    fun updatePointOverlay(snapshot: NativeMapPointOverlaySnapshot) {
        if (!active) return
        pointOverlay.update(snapshot)
    }

    fun dispose() {
        if (!active) return
        active = false
        map?.removeOnCameraMoveStartedListener(cameraMoveStartedListener)
        map?.removeOnCameraMoveListener(cameraMoveListener)
        map?.removeOnMapClickListener(mapClickListener)
        map?.removeOnMapLongClickListener(mapLongClickListener)
        cameraRenderer.detach()
        pointOverlay.detach()
        cursorOverlay.detach()
        map = null
        routeRenderer.detach()
        transitRenderer.detach()
        styleGeneration.dispose()
        lifecycle.handle(NativeMapLifecycleEvent.Destroy)
    }

    private fun applyStyleIfNeeded() {
        val liveMap = map ?: return
        if (requestedStyleJson == desiredStyleJson) return
        val requestedJson = desiredStyleJson
        requestedStyleJson = requestedJson
        val generation = styleGeneration.next()
        routeRenderer.detach()
        transitRenderer.detach()
        liveMap.setStyle(Style.Builder().fromJson(requestedJson)) { loadedStyle ->
            if (!active || !styleGeneration.accepts(generation)) return@setStyle
            // Flutter paints transit first, then route alternatives and the active route.
            transitRenderer.attach(loadedStyle)
            routeRenderer.attach(loadedStyle)
        }
    }

    companion object {
        fun create(
            context: Context,
            dark: Boolean,
            engineProfile: NativeMapEngineProfile,
            initialStyleJson: String,
            initialRouteOverlay: NativeRouteOverlaySnapshot,
            initialTransitOverlay: NativeTransitOverlaySnapshot,
            initialCameraCommand: NativeMapCameraCommand?,
            initialCursorSnapshot: NativeMapCursorSnapshot,
            initialPointOverlay: NativeMapPointOverlaySnapshot,
            onCameraGesture: () -> Unit,
            onMapInteraction: (NativeMapInteraction) -> Unit,
        ): NativeMapLibreViewHost {
            MapLibre.getInstance(context.applicationContext)
            val camera = CameraPosition.Builder()
                .target(
                    LatLng(
                        NativeMapHostContract.INITIAL_LATITUDE,
                        NativeMapHostContract.INITIAL_LONGITUDE,
                    ),
                )
                .zoom(engineProfile.initialZoom)
                .tilt(engineProfile.initialPitchDegrees)
                .build()
            val options = MapLibreMapOptions.createFromAttributes(context)
                .camera(camera)
                .minZoomPreference(engineProfile.minimumZoom)
                .maxZoomPreference(engineProfile.maximumZoom)
                .textureMode(true)
                .foregroundLoadColor(
                    RoadstrThemeTokens.palette(
                        if (dark) {
                            RoadstrThemeId.DarkNostr
                        } else {
                            RoadstrThemeId.LightNostr
                        },
                    ).backgroundArgb.toInt(),
                )
            val mapView = MapView(context, options)
            val pointOverlay = NativeMapPointOverlayView(context)
            val cursorOverlay = NativeMapCursorOverlayView(context)
            val matchParent = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            val rootView = FrameLayout(context).apply {
                addView(mapView, matchParent)
                addView(
                    pointOverlay,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    cursorOverlay,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            val routeRenderer = NativeMapRouteRenderer(
                displayDensity = context.resources.displayMetrics.density,
                initialSnapshot = initialRouteOverlay,
            )
            val transitRenderer = NativeMapTransitRenderer(
                displayDensity = context.resources.displayMetrics.density,
                initialSnapshot = initialTransitOverlay,
            )
            val cameraRenderer = NativeMapCameraRenderer(engineProfile)
            val lifecycle = NativeMapLifecycleController(
                object : NativeMapLifecycleTarget {
                    override fun create() = mapView.onCreate(null)
                    override fun start() = mapView.onStart()
                    override fun resume() = mapView.onResume()
                    override fun pause() = mapView.onPause()
                    override fun stop() = mapView.onStop()
                    override fun destroy() = mapView.onDestroy()
                    override fun lowMemory() = mapView.onLowMemory()
                },
            )
            return NativeMapLibreViewHost(
                rootView = rootView,
                mapView = mapView,
                lifecycle = lifecycle,
                routeRenderer = routeRenderer,
                transitRenderer = transitRenderer,
                cameraRenderer = cameraRenderer,
                cursorOverlay = cursorOverlay,
                pointOverlay = pointOverlay,
                engineProfile = engineProfile,
                onCameraGesture = onCameraGesture,
                onMapInteraction = onMapInteraction,
                initialCameraCommand = initialCameraCommand,
                initialCursorSnapshot = initialCursorSnapshot,
                initialPointOverlay = initialPointOverlay,
                initialStyleJson = initialStyleJson,
            )
        }
    }
}
