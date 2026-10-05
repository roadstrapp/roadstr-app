package app.roadstr.roadtest

import android.content.Context
import java.io.File
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

/**
 * The one Gecko runtime of the process, created the first time a page is opened and never at
 * app start, so a user who never opens a page pays nothing for it.
 *
 * It is not shut down while the process lives: `GeckoRuntime.shutdown()` stops the engine thread
 * for good, and starting it again in the same process is not something the library supports.
 * Memory is given back by closing the session and clearing what the engine stored.
 */
internal class GeckoRuntimeHolder(context: Context, private val onPageMessage: (String) -> Unit) {
    private val appContext = context.applicationContext
    private var runtime: GeckoRuntime? = null

    /** Must be called on the main thread, as the engine requires. */
    fun get(): GeckoRuntime = runtime ?: create().also { runtime = it }

    /** Drops everything the engine stored: cookies, caches, site data, permissions. */
    fun clearAllData() {
        runtime?.storageController?.clearData(StorageController.ClearFlags.ALL)
    }

    /** Drops only the caches; used when the system is short of memory and no page is open. */
    fun clearCaches() {
        runtime?.storageController?.clearData(StorageController.ClearFlags.ALL_CACHES)
    }

    private fun create(): GeckoRuntime {
        // Under no-backup storage: the file is regenerated at each start and is not user data.
        val file = File(appContext.noBackupFilesDir, "gecko/preferences.yaml")
        GeckoPrivacyProfile.writePreferences(file)
        val created = GeckoRuntime.create(appContext, GeckoPrivacyProfile.runtimeSettings(file))
        GeckoPagePlaceExtractor(onPageMessage).install(created)
        return created
    }
}
