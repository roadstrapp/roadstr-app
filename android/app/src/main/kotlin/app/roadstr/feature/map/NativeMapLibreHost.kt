package app.roadstr.feature.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val styleJson = remember(dark) { NativeMapStyle.rasterStyle(dark = dark) }
    val mapDescription = stringResource(R.string.native_map_description)
    val host = remember(context, lifecycleOwner) {
        NativeMapLibreViewHost.create(
            context = context,
            dark = dark,
            initialStyleJson = styleJson,
        )
    }

    LaunchedEffect(host, styleJson) {
        host.updateStyle(styleJson)
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

    AndroidView(
        factory = { host.mapView },
        modifier = modifier.semantics { contentDescription = mapDescription },
    )
}

private class NativeMapLibreViewHost private constructor(
    val mapView: MapView,
    val lifecycle: NativeMapLifecycleController,
    initialStyleJson: String,
) {
    private var active = true
    private var map: MapLibreMap? = null
    private var desiredStyleJson = initialStyleJson
    private var appliedStyleJson: String? = null

    init {
        lifecycle.handle(NativeMapLifecycleEvent.Create)
        mapView.getMapAsync { readyMap ->
            if (!active) return@getMapAsync
            map = readyMap
            applyStyleIfNeeded()
        }
    }

    fun updateStyle(styleJson: String) {
        if (!active || (styleJson == desiredStyleJson && styleJson == appliedStyleJson)) return
        desiredStyleJson = styleJson
        applyStyleIfNeeded()
    }

    fun dispose() {
        if (!active) return
        active = false
        map = null
        lifecycle.handle(NativeMapLifecycleEvent.Destroy)
    }

    private fun applyStyleIfNeeded() {
        val liveMap = map ?: return
        if (appliedStyleJson == desiredStyleJson) return
        liveMap.setStyle(Style.Builder().fromJson(desiredStyleJson))
        appliedStyleJson = desiredStyleJson
    }

    companion object {
        fun create(
            context: Context,
            dark: Boolean,
            initialStyleJson: String,
        ): NativeMapLibreViewHost {
            MapLibre.getInstance(context.applicationContext)
            val camera = CameraPosition.Builder()
                .target(
                    LatLng(
                        NativeMapHostContract.INITIAL_LATITUDE,
                        NativeMapHostContract.INITIAL_LONGITUDE,
                    ),
                )
                .zoom(NativeMapHostContract.INITIAL_ZOOM)
                .tilt(NativeMapHostContract.INITIAL_TILT)
                .build()
            val options = MapLibreMapOptions.createFromAttributes(context)
                .camera(camera)
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
                mapView = mapView,
                lifecycle = lifecycle,
                initialStyleJson = initialStyleJson,
            )
        }
    }
}
