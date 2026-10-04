package app.roadstr.feature.voice

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class NativeVoiceAssetState { Valid, Missing, InvalidSize, InvalidHash, UnsafeFile }

data class NativeVoiceAssetInspection(
    val asset: NativeVoiceAsset,
    val state: NativeVoiceAssetState,
    val actualBytes: Long?,
)

/**
 * Read-only validation of reusable Flutter voice files in app documents.
 *
 * Uses java.io.File only: java.nio.file appears in API 26 and core library
 * desugaring does not provide it, while both APKs still support API 24.
 */
class NativeVoiceAssetStore(private val documentsDirectory: File) {
    private val canonicalRoot = documentsDirectory.canonicalFile

    fun inspect(asset: NativeVoiceAsset): NativeVoiceAssetInspection {
        val file = candidate(asset)
        if (hasUnsafePath(file)) {
            return NativeVoiceAssetInspection(asset, NativeVoiceAssetState.UnsafeFile, null)
        }
        if (!file.exists()) {
            return NativeVoiceAssetInspection(asset, NativeVoiceAssetState.Missing, null)
        }
        val size = file.length()
        if (size != asset.expectedBytes) {
            return NativeVoiceAssetInspection(asset, NativeVoiceAssetState.InvalidSize, size)
        }
        val hash = file.inputStream().buffered().use(::sha256)
        val state = if (hash == asset.sha256) {
            NativeVoiceAssetState.Valid
        } else {
            NativeVoiceAssetState.InvalidHash
        }
        return NativeVoiceAssetInspection(asset, state, size)
    }

    fun inspectCatalogue(
        assets: List<NativeVoiceAsset> = NativeVoiceCatalog.downloadAssets,
    ): List<NativeVoiceAssetInspection> = assets.map(::inspect)

    fun pendingAssets(
        assets: List<NativeVoiceAsset> = NativeVoiceCatalog.downloadAssets,
    ): List<NativeVoiceAsset> = inspectCatalogue(assets)
        .filterNot { it.state == NativeVoiceAssetState.Valid }
        .map(NativeVoiceAssetInspection::asset)

    internal fun resolve(asset: NativeVoiceAsset): File {
        val candidate = candidate(asset)
        require(!hasUnsafePath(candidate)) { "Voice asset path is unsafe" }
        return candidate
    }

    private fun candidate(asset: NativeVoiceAsset): File {
        val candidate = File(canonicalRoot, asset.relativePath).absoluteFile.normalize()
        require(candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "Voice asset escaped the documents directory"
        }
        return candidate
    }

    private fun hasUnsafePath(file: File): Boolean {
        if (file.exists() && !file.isFile) return true
        // The candidate is built from the canonical root with `..` already
        // normalized away, so any symbolic link between the root and the file
        // (or the file itself) is exactly what makes its canonical path differ.
        val canonicalCandidate = runCatching { file.canonicalFile }.getOrNull() ?: return true
        return canonicalCandidate.path != file.path ||
            !canonicalCandidate.path.startsWith(canonicalRoot.path + File.separator)
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

interface NativeVoiceDownloadBody : Closeable {
    val contentLength: Long?
    val stream: InputStream
}

fun interface NativeVoiceDownloadTransport {
    fun open(asset: NativeVoiceAsset): NativeVoiceDownloadBody
}

/** Fixed-deadline transport. It is packaged but has no production owner yet. */
class OkHttpNativeVoiceDownloadTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build(),
) : NativeVoiceDownloadTransport {
    override fun open(asset: NativeVoiceAsset): NativeVoiceDownloadBody {
        val call = client.newCall(Request.Builder().url(asset.remoteUrl).get().build())
        val response = call.execute()
        if (response.code != 200) {
            response.close()
            throw NativeVoiceDownloadException("Voice asset request failed with HTTP ${response.code}")
        }
        val body = response.body ?: run {
            response.close()
            throw NativeVoiceDownloadException("Voice asset response had no body")
        }
        return OkHttpVoiceDownloadBody(call, response, body.contentLength().takeIf { it >= 0 })
    }

    private class OkHttpVoiceDownloadBody(
        private val call: Call,
        private val response: Response,
        override val contentLength: Long?,
    ) : NativeVoiceDownloadBody {
        override val stream: InputStream = requireNotNull(response.body).byteStream()

        override fun close() {
            response.close()
            if (!call.isExecuted()) call.cancel()
        }
    }
}

class NativeVoiceDownloadException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

data class NativeVoiceDownloadProgress(
    val completedBytes: Long,
    val totalBytes: Long,
    val currentRelativePath: String?,
) {
    val fraction: Double = if (totalBytes == 0L) 1.0 else completedBytes.toDouble() / totalBytes
}

/**
 * Verifies existing files first and installs only invalid/missing assets via a
 * same-directory `.part` file, fsync, hash check and atomic replacement.
 */
class NativeVoiceAssetDownloader(
    private val transport: NativeVoiceDownloadTransport,
) {
    fun ensure(
        documentsDirectory: File,
        assets: List<NativeVoiceAsset> = NativeVoiceCatalog.downloadAssets,
        onProgress: (NativeVoiceDownloadProgress) -> Unit = {},
    ): List<NativeVoiceAssetInspection> {
        require(assets.distinctBy(NativeVoiceAsset::relativePath).size == assets.size) {
            "Duplicate voice asset path"
        }
        val store = NativeVoiceAssetStore(documentsDirectory)
        val inspections = store.inspectCatalogue(assets)
        require(inspections.none { it.state == NativeVoiceAssetState.UnsafeFile }) {
            "Refusing to replace an unsafe voice asset path"
        }
        val pending = inspections.filterNot { it.state == NativeVoiceAssetState.Valid }.map { it.asset }
        val total = pending.sumOf(NativeVoiceAsset::expectedBytes)
        var completed = 0L
        onProgress(NativeVoiceDownloadProgress(completed, total, pending.firstOrNull()?.relativePath))
        pending.forEachIndexed { index, asset ->
            installOne(store, asset) { current ->
                onProgress(
                    NativeVoiceDownloadProgress(
                        completedBytes = completed + current,
                        totalBytes = total,
                        currentRelativePath = asset.relativePath,
                    ),
                )
            }
            completed += asset.expectedBytes
            onProgress(
                NativeVoiceDownloadProgress(
                    completedBytes = completed,
                    totalBytes = total,
                    currentRelativePath = pending.getOrNull(index + 1)?.relativePath,
                ),
            )
        }
        return store.inspectCatalogue(assets).also { final ->
            check(final.all { it.state == NativeVoiceAssetState.Valid }) {
                "Voice asset verification failed after installation"
            }
        }
    }

    private fun installOne(
        store: NativeVoiceAssetStore,
        asset: NativeVoiceAsset,
        onBytes: (Long) -> Unit,
    ) {
        val target = store.resolve(asset)
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.part")
        if (partial.exists() && !partial.delete()) {
            throw NativeVoiceDownloadException("Could not clear stale voice partial")
        }
        try {
            transport.open(asset).use { body ->
                body.contentLength?.let { declared ->
                    if (declared != asset.expectedBytes) {
                        throw NativeVoiceDownloadException("Voice asset declared an unexpected size")
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = body.stream.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > asset.expectedBytes) {
                            throw NativeVoiceDownloadException("Voice asset exceeded its expected size")
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        onBytes(received)
                    }
                    output.fd.sync()
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (received != asset.expectedBytes || hash != asset.sha256) {
                    throw NativeVoiceDownloadException("Voice asset integrity verification failed")
                }
            }
            replaceAtomically(partial, target)
        } catch (error: Exception) {
            partial.delete()
            if (error is NativeVoiceDownloadException) throw error
            throw NativeVoiceDownloadException("Voice asset download failed", error)
        }
    }

    private fun replaceAtomically(partial: File, target: File) {
        // Same directory, same filesystem: rename(2) atomically replaces the
        // target, which is what File.renameTo calls on Android.
        if (!partial.renameTo(target)) {
            throw NativeVoiceDownloadException("Could not install verified voice asset")
        }
    }
}
