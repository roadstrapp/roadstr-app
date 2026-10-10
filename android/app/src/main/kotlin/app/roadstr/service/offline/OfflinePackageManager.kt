package app.roadstr.service.offline

import app.roadstr.core.network.PublicAddressPolicy
import java.io.Closeable
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

data class OfflineDownloadResponse(
    val statusCode: Int,
    val contentLength: Long?,
    val contentRange: String?,
    val etag: String?,
    val lastModified: String?,
    val contentEncoding: String?,
    val body: InputStream,
    private val closeAction: () -> Unit = { body.close() },
) : Closeable {
    override fun close() = closeAction()
}

fun interface OfflinePackageDownloadTransport {
    fun open(url: String, rangeStart: Long, ifRange: String?): OfflineDownloadResponse
}

class OkHttpOfflinePackageDownloadTransport(
    private val client: OkHttpClient = publicOnlyClient(),
) : OfflinePackageDownloadTransport {
    override fun open(url: String, rangeStart: Long, ifRange: String?): OfflineDownloadResponse {
        OfflinePackageManifestProtocol.validateUrl(url)
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").apply {
            if (rangeStart > 0) header("Range", "bytes=$rangeStart-")
            if (rangeStart > 0 && ifRange != null) header("If-Range", ifRange)
        }.build()
        val response = client.newCall(request).execute()
        val body = response.body
        return OfflineDownloadResponse(
            statusCode = response.code,
            contentLength = body?.contentLength()?.takeIf { it >= 0 },
            contentRange = response.header("Content-Range"),
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            contentEncoding = response.header("Content-Encoding"),
            body = body?.byteStream() ?: ByteArrayInputStream(ByteArray(0)),
            closeAction = { response.close() },
        )
    }

    companion object {
        private fun publicOnlyClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(PublicOnlyDns)
            .build()

        private object PublicOnlyDns : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !PublicAddressPolicy.isPublic(it.address) }) {
                    throw UnknownHostException("Package host did not resolve to a public address")
                }
                return addresses
            }
        }
    }
}

class OfflineManifestClient(
    private val transport: OfflinePackageDownloadTransport,
) {
    suspend fun load(url: String): OfflinePackageManifest = withContext(Dispatchers.IO) {
        OfflinePackageManifestProtocol.validateUrl(url)
        val response = transport.open(url, 0L, null)
        response.use {
            if (response.statusCode != 200) throw IOException("Manifest request failed")
            if (!response.contentEncoding.isNullOrBlank() && response.contentEncoding != "identity") {
                throw IOException("Encoded manifests are not accepted")
            }
            val declared = response.contentLength
            if (declared != null && declared > OfflinePackageManifestProtocol.MAX_MANIFEST_BYTES) {
                throw IOException("Manifest is too large")
            }
            val output = java.io.ByteArrayOutputStream(
                minOf(declared?.toInt() ?: 8_192, OfflinePackageManifestProtocol.MAX_MANIFEST_BYTES),
            )
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                coroutineContext.ensureActive()
                val count = response.body.read(buffer)
                if (count < 0) break
                if (total > OfflinePackageManifestProtocol.MAX_MANIFEST_BYTES - count) {
                    throw IOException("Manifest is too large")
                }
                output.write(buffer, 0, count)
                total += count
            }
            OfflinePackageManifestProtocol.decode(output.toByteArray())
        }
    }
}

enum class OfflineDownloadRejection {
    MobileDataConfirmationRequired,
    InsufficientSpace,
    InvalidServerResponse,
    IntegrityFailure,
    ArtifactInvalid,
    TransportFailure,
}

sealed interface OfflineInstallOutcome {
    data class Installed(val value: InstalledOfflinePackage) : OfflineInstallOutcome
    data class Rejected(val reason: OfflineDownloadRejection) : OfflineInstallOutcome
}

data class OfflineNetworkState(
    val connected: Boolean,
    val wifi: Boolean,
    val metered: Boolean,
)

fun interface OfflineArtifactValidator {
    fun validate(file: File, artifact: OfflinePackageArtifact): Boolean
}

class BasicOfflineArtifactValidator : OfflineArtifactValidator {
    override fun validate(file: File, artifact: OfflinePackageArtifact): Boolean {
        if (!file.isFile || file.length() != artifact.sizeBytes) return false
        if (artifact.datasetType != OfflineDatasetType.ValhallaRouting) return true
        if (file.length() < TAR_BLOCK_BYTES || file.length() % TAR_BLOCK_BYTES != 0L) return false
        val header = ByteArray(TAR_BLOCK_BYTES.toInt())
        file.inputStream().use { input -> if (input.read(header) != header.size) return false }
        return header.any { it.toInt() != 0 }
    }

    private companion object {
        const val TAR_BLOCK_BYTES = 512L
    }
}

class OfflinePackageManager(
    rootDirectory: File,
    private val transport: OfflinePackageDownloadTransport,
    private val validator: OfflineArtifactValidator = BasicOfflineArtifactValidator(),
    private val freeBytes: () -> Long = { rootDirectory.usableSpace },
    private val closeEngine: (String) -> Unit = {},
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val root = rootDirectory
    private val downloads = File(root, "downloads")
    private val packages = File(root, "packages")
    private val metadata = File(root, "metadata")
    private val active = File(root, "active")

    init {
        listOf(root, downloads, packages, metadata, active).forEach { directory ->
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Offline package storage is unavailable")
        }
    }

    suspend fun install(
        artifact: OfflinePackageArtifact,
        network: OfflineNetworkState,
        allowMobileDataOnce: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): OfflineInstallOutcome = withContext(Dispatchers.IO) {
        if (!network.connected) return@withContext rejected(OfflineDownloadRejection.TransportFailure)
        if ((!network.wifi || network.metered) && !allowMobileDataOnce) {
            return@withContext rejected(OfflineDownloadRejection.MobileDataConfirmationRequired)
        }
        if (!hasSpace(artifact)) return@withContext rejected(OfflineDownloadRejection.InsufficientSpace)
        downloadAndInstall(artifact, onProgress)
    }

    fun installed(): List<InstalledOfflinePackage> = active.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(POINTER_SUFFIX) }
        .mapNotNull(::readActive)
        .sortedBy { it.artifact.id }

    fun availableBytes(): Long = freeBytes().coerceAtLeast(0L)

    fun delete(packageId: String): Boolean {
        if (!PACKAGE_ID.matches(packageId)) return false
        closeEngine(packageId)
        val pointer = File(active, "$packageId$POINTER_SUFFIX")
        if (pointer.exists() && !pointer.delete()) return false
        packages.listFiles().orEmpty().filter { it.name.startsWith("$packageId-") }.forEach(File::delete)
        metadata.listFiles().orEmpty().filter { it.name.startsWith("$packageId-") }.forEach(File::delete)
        downloads.listFiles().orEmpty().filter { it.name.startsWith("$packageId-") }.forEach(File::delete)
        return true
    }

    fun discardPartial(artifact: OfflinePackageArtifact): Boolean {
        val files = downloadFiles(artifact)
        return (!files.part.exists() || files.part.delete()) &&
            (!files.journal.exists() || files.journal.delete())
    }

    private suspend fun downloadAndInstall(
        artifact: OfflinePackageArtifact,
        onProgress: (Long, Long) -> Unit,
    ): OfflineInstallOutcome {
        val files = downloadFiles(artifact)
        val journal = readJournal(files.journal, artifact)
        var received = journal?.receivedBytes?.coerceAtMost(files.part.length()) ?: 0L
        var validatorValue = journal?.validator
        if (received > 0 && validatorValue == null) {
            received = 0
            files.part.writeBytes(ByteArray(0))
        }
        val response = try {
            transport.open(artifact.url, received, validatorValue)
        } catch (_: IOException) {
            return rejected(OfflineDownloadRejection.TransportFailure)
        }
        response.use {
            if (!response.contentEncoding.isNullOrBlank() && response.contentEncoding != "identity") {
                return rejected(OfflineDownloadRejection.InvalidServerResponse)
            }
            if (received > 0 && response.statusCode == HTTP_OK) {
                received = 0
                validatorValue = null
                files.part.writeBytes(ByteArray(0))
            } else if (!validResponse(response, received, artifact.sizeBytes, validatorValue)) {
                return rejected(OfflineDownloadRejection.InvalidServerResponse)
            }
            val newValidator = response.etag ?: response.lastModified ?: validatorValue
            val journalValue = DownloadJournal(artifact.id, artifact.version, artifact.sizeBytes, received, newValidator)
            writeJournal(files.journal, journalValue)
            try {
                stream(response.body, files.part, received, artifact.sizeBytes, files.journal, journalValue, onProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                return rejected(OfflineDownloadRejection.TransportFailure)
            }
        }
        if (files.part.length() != artifact.sizeBytes || sha256(files.part) != artifact.sha256) {
            discardPartial(artifact)
            return rejected(OfflineDownloadRejection.IntegrityFailure)
        }
        if (!validator.validate(files.part, artifact)) {
            discardPartial(artifact)
            return rejected(OfflineDownloadRejection.ArtifactInvalid)
        }
        if (!hasSpace(artifact)) return rejected(OfflineDownloadRejection.InsufficientSpace)
        return activate(files, artifact)
    }

    private suspend fun stream(
        input: InputStream,
        part: File,
        start: Long,
        expected: Long,
        journalFile: File,
        journal: DownloadJournal,
        onProgress: (Long, Long) -> Unit,
    ) {
        RandomAccessFile(part, "rw").use { output ->
            output.seek(start)
            var received = start
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (received > expected - count) throw IOException("Package exceeds manifest size")
                output.write(buffer, 0, count)
                received += count
                writeJournal(journalFile, journal.copy(receivedBytes = received))
                onProgress(received, expected)
            }
            output.fd.sync()
        }
    }

    private fun activate(files: DownloadFiles, artifact: OfflinePackageArtifact): OfflineInstallOutcome {
        val extension = if (artifact.datasetType == OfflineDatasetType.ValhallaRouting) "tar" else "bin"
        val finalFile = File(packages, "${artifact.id}-${artifact.version}.$extension")
        val metadataFile = File(metadata, "${artifact.id}-${artifact.version}.json")
        if (!atomicWrite(metadataFile, OfflinePackageManifestProtocol.encodeArtifact(artifact))) {
            return rejected(OfflineDownloadRejection.ArtifactInvalid)
        }
        // File.renameTo maps to rename(2) on Android and replaces the target on the same
        // filesystem.  Do not delete the old target first: that would create a crash window.
        if (!files.part.renameTo(finalFile)) return rejected(OfflineDownloadRejection.ArtifactInvalid)
        val pointer = Properties().apply {
            setProperty("file", finalFile.name)
            setProperty("metadata", metadataFile.name)
            setProperty("installedAt", clockMillis().toString())
        }
        val pointerFile = File(active, "${artifact.id}$POINTER_SUFFIX")
        if (!atomicWriteProperties(pointerFile, pointer)) {
            finalFile.delete()
            return rejected(OfflineDownloadRejection.ArtifactInvalid)
        }
        files.journal.delete()
        removeInactiveVersions(artifact, finalFile, metadataFile)
        return OfflineInstallOutcome.Installed(
            InstalledOfflinePackage(artifact, finalFile.absolutePath, clockMillis()),
        )
    }

    private fun removeInactiveVersions(artifact: OfflinePackageArtifact, keepFile: File, keepMetadata: File) {
        closeEngine(artifact.id)
        packages.listFiles().orEmpty().filter {
            it.name.startsWith("${artifact.id}-") && it != keepFile
        }.forEach(File::delete)
        metadata.listFiles().orEmpty().filter {
            it.name.startsWith("${artifact.id}-") && it != keepMetadata
        }.forEach(File::delete)
    }

    private fun readActive(pointerFile: File): InstalledOfflinePackage? = runCatching {
        val values = Properties().apply { pointerFile.inputStream().use(::load) }
        val fileName = values.getProperty("file")
        val metadataName = values.getProperty("metadata")
        if (!SAFE_FILE.matches(fileName) || !SAFE_FILE.matches(metadataName)) return null
        val packageFile = File(packages, fileName)
        val metadataFile = File(metadata, metadataName)
        if (!packageFile.isFile || !metadataFile.isFile) return null
        val artifact = OfflinePackageManifestProtocol.decode(metadataFile.readBytes()).artifacts.single()
        InstalledOfflinePackage(
            artifact = artifact,
            filePath = packageFile.absolutePath,
            installedAtEpochMillis = values.getProperty("installedAt").toLong(),
        )
    }.getOrNull()

    private fun validResponse(
        response: OfflineDownloadResponse,
        start: Long,
        expected: Long,
        previousValidator: String?,
    ): Boolean {
        if (start == 0L && response.statusCode != HTTP_OK && response.statusCode != HTTP_PARTIAL) return false
        if (start > 0L && response.statusCode != HTTP_PARTIAL) return false
        val responseValidator = response.etag ?: response.lastModified
        if (start > 0L && (previousValidator == null || responseValidator != previousValidator)) return false
        if (response.statusCode == HTTP_PARTIAL) {
            val match = CONTENT_RANGE.matchEntire(response.contentRange.orEmpty()) ?: return false
            if (match.groupValues[1].toLongOrNull() != start) return false
            if (match.groupValues[3].toLongOrNull() != expected) return false
        }
        val length = response.contentLength
        return length == null || length in 0..(expected - start)
    }

    private fun hasSpace(artifact: OfflinePackageArtifact): Boolean {
        val tenPercent = artifact.installedSizeBytes / 10
        val margin = maxOf(tenPercent, MINIMUM_SAFETY_BYTES)
        val required = safeSum(artifact.installedSizeBytes, artifact.sizeBytes, margin) ?: return false
        return freeBytes() >= required
    }

    private fun safeSum(vararg values: Long): Long? {
        var total = 0L
        values.forEach {
            if (it < 0 || total > Long.MAX_VALUE - it) return null
            total += it
        }
        return total
    }

    private fun readJournal(file: File, artifact: OfflinePackageArtifact): DownloadJournal? = runCatching {
        if (!file.isFile) return null
        val values = Properties().apply { file.inputStream().use(::load) }
        DownloadJournal(
            id = values.getProperty("id"),
            version = values.getProperty("version").toInt(),
            expectedBytes = values.getProperty("expectedBytes").toLong(),
            receivedBytes = values.getProperty("receivedBytes").toLong(),
            validator = values.getProperty("validator")?.takeIf(String::isNotEmpty),
        ).takeIf {
            it.id == artifact.id && it.version == artifact.version &&
                it.expectedBytes == artifact.sizeBytes && it.receivedBytes in 0..artifact.sizeBytes
        }
    }.getOrNull()

    private fun writeJournal(file: File, value: DownloadJournal) {
        val properties = Properties().apply {
            setProperty("id", value.id)
            setProperty("version", value.version.toString())
            setProperty("expectedBytes", value.expectedBytes.toString())
            setProperty("receivedBytes", value.receivedBytes.toString())
            setProperty("validator", value.validator.orEmpty())
        }
        if (!atomicWriteProperties(file, properties)) throw IOException("Cannot persist download journal")
    }

    private fun atomicWriteProperties(file: File, values: Properties): Boolean {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        return runCatching {
            FileOutputStream(temporary).use { output ->
                values.store(output, null)
                output.fd.sync()
            }
            temporary.renameTo(file)
        }.getOrDefault(false)
    }

    private fun atomicWrite(file: File, bytes: ByteArray): Boolean {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        return runCatching {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            temporary.renameTo(file)
        }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadFiles(artifact: OfflinePackageArtifact): DownloadFiles {
        val base = "${artifact.id}-${artifact.version}"
        return DownloadFiles(File(downloads, "$base.part"), File(downloads, "$base.journal"))
    }

    private fun rejected(reason: OfflineDownloadRejection) = OfflineInstallOutcome.Rejected(reason)

    private data class DownloadFiles(val part: File, val journal: File)
    private data class DownloadJournal(
        val id: String,
        val version: Int,
        val expectedBytes: Long,
        val receivedBytes: Long,
        val validator: String?,
    )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val BUFFER_BYTES = 64 * 1024
        const val MINIMUM_SAFETY_BYTES = 256L * 1024 * 1024
        const val POINTER_SUFFIX = ".pointer"
        val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)")
        val PACKAGE_ID = Regex("[a-z0-9][a-z0-9._-]{0,95}")
        val SAFE_FILE = Regex("[a-z0-9][a-z0-9._-]{0,159}")
    }
}
