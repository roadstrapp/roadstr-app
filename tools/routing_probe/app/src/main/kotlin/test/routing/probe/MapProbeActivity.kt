package test.routing.probe

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File

class MapProbeActivity : Activity() {
    private lateinit var mapView: MapView
    private val handler = Handler(Looper.getMainLooper())

    private val stops = listOf(
        Triple("italy-z5.5", LatLng(42.0, 12.5), 5.5), Triple("roma-z10", LatLng(41.9, 12.5), 10.0),
        Triple("roma-z13", LatLng(41.89, 12.49), 13.0), Triple("roma-z14", LatLng(41.8902, 12.4922), 14.0),
        Triple("firenze-z12", LatLng(43.77, 11.25), 12.0), Triple("milano-z14", LatLng(45.4642, 9.19), 14.0),
        Triple("napoli-z13", LatLng(40.85, 14.27), 13.0), Triple("palermo-z14", LatLng(38.1157, 13.3615), 14.0),
        Triple("cagliari-z12", LatLng(39.22, 9.12), 12.0), Triple("torino-z14", LatLng(45.07, 7.68), 14.0),
        Triple("trieste-z13", LatLng(45.65, 13.77), 13.0), Triple("bari-z14", LatLng(41.1171, 16.8719), 14.0),
    )
    private var idx = 0
    private var t0 = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.init(this, "map")
        MapLibre.getInstance(this)
        mapView = MapView(this)
        setContentView(mapView)
        mapView.onCreate(savedInstanceState)
        val pm = File(filesDir, "italy.pmtiles")
        Util.log("SUITE map pmtilesMB=${pm.length() / 1048576} device ${Util.device(this)}")
        Util.log("mem before ${Util.mem()}")
        val style = styleJson("pmtiles://file://${pm.absolutePath}")
        val tStart = System.nanoTime()
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(style)) {
                Util.log("style loaded after ${(System.nanoTime() - tStart) / 1_000_000} ms; mem ${Util.mem()}")
                mapView.addOnDidBecomeIdleListener {
                    if (t0 != 0L) {
                        Util.log("STOP ${stops[idx].first} idle after ${(System.nanoTime() - t0) / 1_000_000} ms | ${Util.mem()}")
                        t0 = 0L
                        idx++
                        handler.postDelayed({ next(map) }, 400)
                    }
                }
                next(map)
            }
        }
    }

    private fun next(map: org.maplibre.android.maps.MapLibreMap) {
        if (idx >= stops.size) { Util.log("device ${Util.device(this)}"); Util.log("SUITE map DONE"); return }
        val (name, ll, z) = stops[idx]
        t0 = System.nanoTime()
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, z))
    }

    private fun styleJson(url: String) = """
{"version":8,"name":"probe","sources":{"omt":{"type":"vector","url":"$url"}},
"layers":[
{"id":"bg","type":"background","paint":{"background-color":"#f2efe9"}},
{"id":"landcover","type":"fill","source":"omt","source-layer":"landcover","paint":{"fill-color":"#cfe3c0"}},
{"id":"landuse","type":"fill","source":"omt","source-layer":"landuse","paint":{"fill-color":"#e3e0d6"}},
{"id":"water","type":"fill","source":"omt","source-layer":"water","paint":{"fill-color":"#9ec5e8"}},
{"id":"building","type":"fill","source":"omt","source-layer":"building","minzoom":13,"paint":{"fill-color":"#d8d3c8"}},
{"id":"road-minor","type":"line","source":"omt","source-layer":"transportation","filter":["in","class","minor","service","tertiary"],"paint":{"line-color":"#ffffff","line-width":1}},
{"id":"road-major","type":"line","source":"omt","source-layer":"transportation","filter":["in","class","primary","secondary","trunk","motorway"],"paint":{"line-color":"#f5a742","line-width":2}},
{"id":"boundary","type":"line","source":"omt","source-layer":"boundary","paint":{"line-color":"#888","line-width":1}}
]}""".trimIndent()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}
