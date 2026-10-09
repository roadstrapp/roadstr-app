package test.routing.probe

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.util.Log
import java.io.File

object Util {
    const val TAG = "PROBE"
    lateinit var out: File

    fun init(ctx: Context, suite: String) {
        out = File(ctx.filesDir, "results_$suite.txt").also { it.writeText("") }
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
        out.appendText(msg + "\n")
    }

    private fun status(key: String): Long =
        File("/proc/self/status").readLines().firstOrNull { it.startsWith(key) }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: -1

    fun mem(): String {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        return "pssMB=${mi.totalPss / 1024} rssMB=${status("VmRSS") / 1024} hwmMB=${status("VmHWM") / 1024} " +
            "javaHeapMB=${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576} " +
            "nativeHeapMB=${Debug.getNativeHeapAllocatedSize() / 1048576}"
    }

    fun device(ctx: Context): String {
        val b: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return "battery=$level% tempC=$temp thermalStatus=${pm.currentThermalStatus} maxJavaHeapMB=${Runtime.getRuntime().maxMemory() / 1048576}"
    }
}
