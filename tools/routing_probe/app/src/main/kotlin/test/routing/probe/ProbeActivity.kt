package test.routing.probe

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlin.concurrent.thread

class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val suite = intent.getStringExtra("suite") ?: "none"
        val tv = TextView(this).also { it.text = "probe: $suite" }
        setContentView(tv)
        Util.init(this, suite)
        thread(name = "probe-$suite") {
            try {
                when (suite) {
                    "valhalla_full" -> ValhallaSuite(this, "car_full.tar", false).run()
                    "valhalla_corridor" -> ValhallaSuite(this, "corridor.tar", true).run()
                    "brouter" -> BrouterSuite(this, intent.getIntExtra("vmax", 90), intent.getIntExtra("memclass", 0)).run()
                    else -> Util.log("unknown suite $suite")
                }
            } catch (t: Throwable) {
                Util.log("FATAL " + android.util.Log.getStackTraceString(t))
            }
        }
    }
}
