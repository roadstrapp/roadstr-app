package test.routing.probe

import android.content.Context
import com.valhalla.config.ValhallaConfigBuilder
import com.valhalla.valhalla.Valhalla
import org.json.JSONObject
import java.io.File

class ValhallaSuite(private val ctx: Context, private val tarName: String, private val reroutesOnly: Boolean) {
    fun run() {
        val tar = File(ctx.filesDir, tarName)
        Util.log("SUITE valhalla tar=${tar.name} sizeMB=${tar.length() / 1048576}")
        Util.log("device ${Util.device(ctx)}")
        Util.log("mem before ${Util.mem()}")
        val t0 = System.nanoTime()
        val config = ValhallaConfigBuilder().withTileExtract(tar.absolutePath).build()
        val v = Valhalla(ctx, config)
        Util.log("engine created in ${(System.nanoTime() - t0) / 1_000_000} ms; mem ${Util.mem()}")
        val pairs = JSONObject(ctx.assets.open("pairs.json").bufferedReader().readText())
        val items = ArrayList<Triple<String, DoubleArray, DoubleArray>>()
        for (key in listOf("pairs", "reroutes")) {
            if (reroutesOnly && key == "pairs") {
                // keep only the corridor route itself
            }
            val arr = pairs.getJSONArray(key)
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                if (reroutesOnly && key == "pairs" && p.getString("name") != "Firenze-Perugia") continue
                val a = p.getJSONArray("a"); val b = p.getJSONArray("b")
                items += Triple(p.getString("name"), doubleArrayOf(a.getDouble(0), a.getDouble(1)), doubleArrayOf(b.getDouble(0), b.getDouble(1)))
            }
        }
        for ((name, a, b) in items) {
            val req = """{"locations":[{"lat":${a[0]},"lon":${a[1]}},{"lat":${b[0]},"lon":${b[1]}}],"costing":"auto","language":"it-IT"}"""
            val times = ArrayList<Long>()
            var summary = ""
            var maneuvers = -1
            for (rep in 0 until 6) {
                val t = System.nanoTime()
                val raw = try { v.routeRaw(req) } catch (e: Throwable) { """{"code":-1,"message":"${e.javaClass.simpleName}: ${e.message?.take(60)}"}""" }
                times += (System.nanoTime() - t) / 1_000_000
                if (rep == 0) {
                    val j = JSONObject(raw)
                    if (j.has("trip")) {
                        val s = j.getJSONObject("trip").getJSONObject("summary")
                        summary = "km=%.1f min=%.0f".format(s.getDouble("length"), s.getDouble("time") / 60)
                        maneuvers = j.getJSONObject("trip").getJSONArray("legs").getJSONObject(0).getJSONArray("maneuvers").length()
                    } else summary = "ERR " + (j.optString("message", raw.take(60)))
                }
            }
            val warm = times.drop(1).sorted()
            Util.log("V $name | $summary maneuvers=$maneuvers | cold=${times[0]}ms warm_median=${warm[warm.size / 2]}ms warm_min=${warm[0]}ms | ${Util.mem()}")
        }
        Util.log("device ${Util.device(ctx)}")
        v.close()
        Util.log("SUITE valhalla DONE")
    }
}
