package app.roadstr.storage

import android.content.Context
import java.io.File

/**
 * Stable app-private root for native state.
 *
 * [Context.noBackupFilesDir] survives an in-place APK update while remaining
 * outside backup/restore flows. The caller still owns the decision of when to
 * create or use this directory; constructing this value performs no I/O.
 */
data class NativeStoragePaths(
    val rootDirectory: File,
) {
    init {
        require(rootDirectory.isAbsolute) { "Native storage root must be absolute" }
    }

    companion object {
        const val ROOT_DIRECTORY_NAME = "roadstr-native-v1"

        fun from(context: Context): NativeStoragePaths =
            under(context.applicationContext.noBackupFilesDir)

        fun under(noBackupFilesDir: File): NativeStoragePaths =
            NativeStoragePaths(File(noBackupFilesDir, ROOT_DIRECTORY_NAME))
    }
}
