package test.routing.probe

import android.content.Context
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import org.json.JSONObject
import java.io.File

class BrouterSuite(private val ctx: Context, private val vmax: Int, private val memClass: Int) {
    private fun node(lat: Double, lon: Double, name: String) = OsmNodeNamed().also {
        it.name = name
        it.ilon = ((lon + 180.0) * 1_000_000.0 + 0.5).toInt()
        it.ilat = ((lat + 90.0) * 1_000_000.0 + 0.5).toInt()
    }

    fun run() {
        val seg = File(ctx.filesDir, "rd5")
        val prof = File(ctx.filesDir, "brouter_profiles")
        Util.log("rc.memoryclass=$memClass (0 = BRouter default 64) deviceMemoryClass=${(ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass}")
        Util.log("SUITE brouter segmentsMB=${seg.listFiles()!!.sumOf { it.length() } / 1048576} vmax=$vmax")
        Util.log("device ${Util.device(ctx)}")
        Util.log("mem before ${Util.mem()}")
        val pairs = JSONObject(ctx.assets.open("pairs.json").bufferedReader().readText())
        val items = ArrayList<Triple<String, DoubleArray, DoubleArray>>()
        for (key in listOf("pairs", "reroutes")) {
            val arr = pairs.getJSONArray(key)
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val a = p.getJSONArray("a"); val b = p.getJSONArray("b")
                items += Triple(p.getString("name"), doubleArrayOf(a.getDouble(0), a.getDouble(1)), doubleArrayOf(b.getDouble(0), b.getDouble(1)))
            }
        }
        for ((name, a, b) in items) {
            val times = ArrayList<Long>()
            var summary = ""
            for (rep in 0 until 2) {
                val rc = RoutingContext()
                rc.localFunction = File(prof, "car-vario.brf").absolutePath
                if (memClass > 0) rc.memoryclass = minOf(256, maxOf(16, memClass))
                if (vmax > 0) rc.keyValues = hashMapOf("vmax" to vmax.toString())
                val t = System.nanoTime()
                var err: String? = null
                var km = 0.0; var min = 0.0
                try {
                    val e = RoutingEngine(null, null, seg, arrayListOf(node(a[0], a[1], "from"), node(b[0], b[1], "to")), rc, 0)
                    e.quite = false
                    e.doRun(60000)
                    err = e.errorMessage
                    val tr = e.foundTrack
                    if (tr != null) { km = tr.distance / 1000.0; min = tr.totalSeconds / 60.0 }
                    if (rep == 0) Util.log("  dbg track=${tr != null} nodes=${tr?.nodes?.size} dist=${tr?.distance} err=${e.errorMessage} cacheOk=${File(seg, "E10_N40.rd5").exists()}")
                } catch (ex: Throwable) { err = ex.javaClass.simpleName + ": " + ex.message?.take(60) }
                times += (System.nanoTime() - t) / 1_000_000
                if (rep == 0) summary = if (err != null) "ERR " + err.trim().take(60) else "km=%.1f min=%.0f".format(km, min)
            }
            Util.log("B $name | $summary | run1=${times[0]}ms run2=${times[1]}ms | ${Util.mem()}")
        }
        Util.log("device ${Util.device(ctx)}")
        Util.log("SUITE brouter DONE")
    }
}
