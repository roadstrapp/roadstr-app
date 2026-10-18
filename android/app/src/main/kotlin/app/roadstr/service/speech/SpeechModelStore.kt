package app.roadstr.service.speech

import app.roadstr.core.speech.SpeechModelCatalog
import app.roadstr.core.speech.SpeechModelSpec
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Why a model download did not finish. Only [Integrity] discards the partial file. */
sealed class SpeechModelException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(message: String, cause: Throwable? = null) : SpeechModelException(message, cause)
    class InsufficientStorage(val neededBytes: Long) : SpeechModelException("not enough free storage")
    class Integrity(message: String) : SpeechModelException(message)
    class Cancelled : SpeechModelException("download cancelled")

    /** The server cannot resume from the offset we asked for; the store restarts from zero once. */
    internal class RangeRejected : SpeechModelException("range rejected")
}

/** An open model download. [startOffset] is where [stream] begins within the file. */
interface SpeechModelBody : Closeable {
    val startOffset: Long
    val stream: InputStream
}

fun interface SpeechModelTransport {
    /** Opens [url], resuming at [resumeFrom] when above zero. */
    @Throws(SpeechModelException::class)
    fun open(url: String, resumeFrom: Long): SpeechModelBody
}

enum class SpeechModelFileState { Missing, Ready, Corrupt }

/**
 * Keeps one verified model file in [directory]. Nothing is ever loaded from a file that has not
 * matched the spec's exact size and SHA-256: a download goes to a `.part` file, is hashed in full,
 * and only then renamed into place. A small marker remembers the verification so that later starts
 * do not have to hash half a gigabyte again.
 */
class SpeechModelStore(
    private val directory: File,
    val spec: SpeechModelSpec = SpeechModelCatalog.default,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {
    val modelFile = File(directory, spec.fileName)
    private val partFile = File(directory, spec.fileName + ".part")
    private val markerFile = File(directory, spec.fileName + ".verified")

    fun state(): SpeechModelFileState {
        if (!modelFile.isFile) return SpeechModelFileState.Missing
        if (modelFile.length() != spec.byteSize) return corrupt()
        if (markerMatches()) return SpeechModelFileState.Ready
        if (sha256(modelFile) != spec.sha256) return corrupt()
        writeMarker()
        return SpeechModelFileState.Ready
    }

    /** Bytes of an interrupted download that a new attempt will resume from. */
    fun partialBytes(): Long = if (partFile.isFile) partFile.length() else 0L

    fun delete() {
        modelFile.delete()
        partFile.delete()
        markerFile.delete()
    }

    /**
     * Downloads and installs the model; returns when it is verified and in place. [isCancelled] is
     * polled between reads; a cancelled or failed download keeps its partial file for resuming.
     */
    @Throws(SpeechModelException::class)
    fun download(
        transport: SpeechModelTransport,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean = { false },
    ) {
        check(directory.isDirectory || directory.mkdirs()) { "cannot create ${directory.path}" }
        discardOversizedPartial()
        requireFreeSpace()
        try {
            receive(transport, partFile.length(), onProgress, isCancelled)
        } catch (_: SpeechModelException.RangeRejected) {
            partFile.delete()
            receive(transport, 0L, onProgress, isCancelled)
        }
        install()
    }

    private fun discardOversizedPartial() {
        if (partFile.isFile && partFile.length() >= spec.byteSize) partFile.delete()
    }

    private fun requireFreeSpace() {
        val needed = spec.byteSize - partialBytes() + SAFETY_MARGIN_BYTES
        if (freeSpace(directory) < needed) throw SpeechModelException.InsufficientStorage(needed)
    }

    private fun receive(
        transport: SpeechModelTransport,
        resumeFrom: Long,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        transport.open(spec.downloadUrl, resumeFrom).use { body ->
            if (body.startOffset != resumeFrom) partFile.delete()
            val received = writeBody(body, onProgress, isCancelled)
            if (received != spec.byteSize) throw SpeechModelException.Network("the download ended early")
        }
    }

    private fun writeBody(body: SpeechModelBody, onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean): Long {
        onProgress(body.startOffset, spec.byteSize)
        try {
            return FileOutputStream(partFile, body.startOffset > 0).use { output ->
                copy(body.stream, output, body.startOffset, onProgress, isCancelled).also { output.fd.sync() }
            }
        } catch (error: IOException) {
            throw SpeechModelException.Network("the download was interrupted", error)
        }
    }

    private fun copy(
        input: InputStream,
        output: FileOutputStream,
        alreadyReceived: Long,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        var received = alreadyReceived
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            if (isCancelled()) throw SpeechModelException.Cancelled()
            val count = input.read(buffer)
            if (count < 0) return received
            received += count
            if (received > spec.byteSize) {
                partFile.delete()
                throw SpeechModelException.Integrity("the server sent more than the expected size")
            }
            output.write(buffer, 0, count)
            onProgress(received, spec.byteSize)
        }
    }

    private fun install() {
        if (sha256(partFile) != spec.sha256) {
            partFile.delete()
            throw SpeechModelException.Integrity("the downloaded model does not match its checksum")
        }
        modelFile.delete()
        if (!partFile.renameTo(modelFile)) throw SpeechModelException.Network("could not install the model")
        writeMarker()
    }

    private fun corrupt(): SpeechModelFileState {
        delete()
        return SpeechModelFileState.Corrupt
    }

    private fun markerText() = "${spec.sha256} ${modelFile.length()} ${modelFile.lastModified()}"

    private fun markerMatches() = runCatching { markerFile.readText() == markerText() }.getOrDefault(false)

    private fun writeMarker() {
        runCatching { markerFile.writeText(markerText()) }
    }

    companion object {
        private const val BUFFER_BYTES = 64 * 1024
        private const val SAFETY_MARGIN_BYTES = 64L * 1024 * 1024

        fun sha256(file: File): String {
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
    }
}
