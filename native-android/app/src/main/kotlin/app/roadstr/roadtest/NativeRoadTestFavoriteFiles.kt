package app.roadstr.roadtest

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.roadstr.R
import app.roadstr.feature.home.NativeShellFavoriteFiles
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Favourites export and import through the system file picker (Storage Access
 * Framework): the app asks for no storage permission and sees only the file
 * the user chose.
 *
 * Must be created while the Activity is being constructed, because the result
 * launchers have to be registered before it starts.
 */
internal class NativeRoadTestFavoriteFiles(private val activity: ComponentActivity) : NativeShellFavoriteFiles {
    private val io = CoroutineScope(Dispatchers.IO)
    // A channel, not a shared flow: a file picked while the screen is being
    // rebuilt waits for the collector instead of being dropped.
    private val importedTexts = Channel<String>(Channel.BUFFERED)

    private val createLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri -> writePending(uri) }

    private val openLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> readPicked(uri) }

    override val imports: Flow<String> = importedTexts.receiveAsFlow()

    override fun export(fileName: String, content: String) {
        pendingExport = content
        runCatching { createLauncher.launch(fileName) }.onFailure {
            pendingExport = null
            toast(R.string.native_nostr_export_failed)
        }
    }

    override fun requestImport() {
        runCatching { openLauncher.launch(arrayOf(MIME_JSON, "text/plain", "application/octet-stream")) }
            .onFailure { toast(R.string.native_nostr_import_failed) }
    }

    private fun writePending(uri: Uri?) {
        val content = pendingExport
        pendingExport = null
        if (uri == null || content == null) return
        io.launch {
            val written = runCatching {
                activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    ?: error("no output stream")
            }.isSuccess
            toast(if (written) R.string.native_nostr_export_success else R.string.native_nostr_export_failed)
        }
    }

    private fun readPicked(uri: Uri?) {
        if (uri == null) return
        io.launch {
            val text = runCatching { readBounded(uri) }.getOrNull()
            if (text == null) {
                toast(R.string.native_nostr_import_failed)
            } else {
                importedTexts.trySend(text)
            }
        }
    }

    /** UTF-8 text of [uri], or null when it is larger than any favourites file can be. */
    private fun readBounded(uri: Uri): String? {
        val buffer = ByteArrayOutputStream()
        activity.contentResolver.openInputStream(uri)?.use { input ->
            val chunk = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                if (buffer.size() + read > MAX_IMPORT_BYTES) return null
                buffer.write(chunk, 0, read)
            }
        } ?: return null
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun toast(message: Int) {
        activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val MIME_JSON = "application/json"
        const val MAX_IMPORT_BYTES = 2 * 1024 * 1024

        // Survives the Activity being re-created while the picker is open.
        @Volatile
        var pendingExport: String? = null
    }
}
