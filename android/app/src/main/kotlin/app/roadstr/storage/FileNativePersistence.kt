package app.roadstr.storage

import app.roadstr.migration.LegacySnapshotLimits
import app.roadstr.migration.MigrationMarker
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

internal const val NATIVE_PUBLIC_SNAPSHOT_FILE = "native_snapshot_v1.bin"
internal const val NATIVE_MIGRATION_MARKER_FILE = "native_snapshot_v1.complete"
internal const val NATIVE_STAGE_SUFFIX = ".stage"
internal const val NATIVE_BACKUP_SUFFIX = ".backup"

class NativePersistenceException(message: String) : IllegalStateException(message)

/**
 * App-private, recoverable single-file replacement.
 *
 * The staged file is fsynced before a same-directory rename. The previous
 * active file remains as a backup until the replacement has been reopened and
 * validated. A restart with only a backup restores it; a restart with both
 * files keeps the valid active value or rolls back to the valid backup.
 */
internal class RecoverableAtomicFile(
    private val directory: File,
    fileName: String,
    private val maxBytes: Int,
    private val validator: (ByteArray) -> Unit,
) {
    private val active = File(directory, fileName)
    private val stage = File(directory, fileName + NATIVE_STAGE_SUFFIX)
    private val backup = File(directory, fileName + NATIVE_BACKUP_SUFFIX)

    init {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) {
            throw NativePersistenceException("Native persistence file name is invalid")
        }
        if (maxBytes <= 0) {
            throw NativePersistenceException("Native persistence size limit is invalid")
        }
    }

    fun stage(bytes: ByteArray) = guarded("Native persistence staging failed") {
        if (bytes.isEmpty() || bytes.size > maxBytes) fail("Native persistence value is invalid")
        validator(bytes)
        ensureDirectory()
        FileOutputStream(stage, false).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        readValidated(stage)
    }

    fun commit() = guarded("Native persistence commit failed") {
        ensureDirectory()
        if (!stage.isFile) fail("Native persistence has no staged value")
        readValidated(stage)

        // Resolve only ambiguous crash state here. An active-only value may be
        // corrupt, but a fresh valid stage is still allowed to replace it.
        if (active.exists() && backup.exists()) recoverAndRead()
        if (!active.exists() && backup.exists()) recoverAndRead()
        if (backup.exists()) fail("Native persistence recovery is incomplete")

        val hadActive = active.exists()
        if (hadActive && !active.isFile) fail("Native persistence active value is invalid")
        if (hadActive && !active.renameTo(backup)) {
            fail("Native persistence backup creation failed")
        }
        if (!stage.renameTo(active)) {
            if (hadActive) restoreBackup()
            fail("Native persistence activation failed")
        }

        try {
            readValidated(active)
        } catch (error: Exception) {
            if (!active.delete() && active.exists()) {
                fail("Native persistence rollback failed")
            }
            if (hadActive) restoreBackup()
            throw error
        }

        if (backup.exists() && !backup.delete()) {
            fail("Native persistence backup cleanup failed")
        }
    }

    fun read(): ByteArray? = guarded("Native persistence read failed") {
        ensureDirectory()
        recoverAndRead()
    }

    private fun recoverAndRead(): ByteArray? {
        if (!active.exists()) {
            if (!backup.exists()) return null
            val recovered = readValidated(backup)
            if (!backup.renameTo(active)) fail("Native persistence recovery failed")
            return recovered
        }
        if (!active.isFile) fail("Native persistence active value is invalid")
        if (!backup.exists()) return readValidated(active)
        if (!backup.isFile) fail("Native persistence backup value is invalid")

        return try {
            val current = readValidated(active)
            if (!backup.delete()) fail("Native persistence backup cleanup failed")
            current
        } catch (_: Exception) {
            val previous = readValidated(backup)
            if (!active.delete() && active.exists()) {
                fail("Native persistence rollback failed")
            }
            if (!backup.renameTo(active)) fail("Native persistence rollback failed")
            previous
        }
    }

    private fun restoreBackup() {
        if (!backup.isFile || !backup.renameTo(active)) {
            fail("Native persistence rollback failed")
        }
    }

    private fun readValidated(file: File): ByteArray {
        if (!file.isFile) fail("Native persistence value is unavailable")
        val expectedLength = file.length()
        if (expectedLength <= 0 || expectedLength > maxBytes) {
            fail("Native persistence value is invalid")
        }

        val bytes = ByteArrayOutputStream(expectedLength.toInt()).use { output ->
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) fail("Native persistence value is too large")
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray()
        }
        if (bytes.size.toLong() != expectedLength || file.length() != expectedLength) {
            fail("Native persistence value changed while reading")
        }
        validator(bytes)
        return bytes
    }

    private fun ensureDirectory() {
        if (directory.exists()) {
            if (!directory.isDirectory) fail("Native persistence directory is invalid")
            return
        }
        if (!directory.mkdirs() && !directory.isDirectory) {
            fail("Native persistence directory creation failed")
        }
    }

    private inline fun <T> guarded(message: String, block: () -> T): T = try {
        block()
    } catch (error: NativePersistenceException) {
        throw error
    } catch (_: Exception) {
        throw NativePersistenceException(message)
    }

    private fun fail(message: String): Nothing = throw NativePersistenceException(message)
}

/** Durable public half of the native migration snapshot. */
class FileNativePublicSnapshotStore(directory: File) : NativePublicSnapshotStore {
    private val file = RecoverableAtomicFile(
        directory = directory,
        fileName = NATIVE_PUBLIC_SNAPSHOT_FILE,
        maxBytes = LegacySnapshotLimits.MAX_ENVELOPE_BYTES,
        validator = { NativeSnapshotRecordCodec.decode(it) },
    )

    override fun stage(encodedRecord: ByteArray) {
        file.stage(encodedRecord.copyOf())
    }

    override fun commit() {
        file.commit()
    }

    override fun read(): ByteArray? = file.read()?.copyOf()
}

/**
 * Durable completion marker bound to the exact committed public record.
 *
 * Protected values are verified by [CompositeNativeSnapshotWriter] before
 * this marker is written; they are never copied into this public marker.
 */
class FileSnapshotBoundMigrationMarker(
    directory: File,
    private val publicStore: NativePublicSnapshotStore,
) : MigrationMarker {
    private val marker = RecoverableAtomicFile(
        directory = directory,
        fileName = NATIVE_MIGRATION_MARKER_FILE,
        maxBytes = MARKER_BYTES,
        validator = ::validateMarker,
    )

    override fun isComplete(): Boolean = try {
        val markerBytes = marker.read() ?: return false
        val publicBytes = publicStore.read() ?: return false
        NativeSnapshotRecordCodec.decode(publicBytes)
        markerMatches(markerBytes, publicBytes)
    } catch (_: RuntimeException) {
        false
    }

    override fun markComplete() {
        try {
            val publicBytes = publicStore.read()
                ?: throw NativePersistenceException("Native public snapshot is unavailable")
            NativeSnapshotRecordCodec.decode(publicBytes)
            marker.stage(markerFor(publicBytes))
            marker.commit()
            if (!isComplete()) {
                throw NativePersistenceException("Native migration marker verification failed")
            }
        } catch (error: NativePersistenceException) {
            throw error
        } catch (_: RuntimeException) {
            throw NativePersistenceException("Native migration marker write failed")
        }
    }

    private companion object {
        val MAGIC = "RSTRMIG1".toByteArray(Charsets.US_ASCII)
        const val DIGEST_BYTES = 32
        val MARKER_BYTES = MAGIC.size + DIGEST_BYTES

        fun validateMarker(bytes: ByteArray) {
            if (bytes.size != MARKER_BYTES ||
                !MessageDigest.isEqual(MAGIC, bytes.copyOfRange(0, MAGIC.size))
            ) {
                throw NativePersistenceException("Native migration marker is invalid")
            }
        }

        fun markerFor(publicBytes: ByteArray): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicBytes)
            return ByteArray(MARKER_BYTES).also { result ->
                MAGIC.copyInto(result)
                digest.copyInto(result, destinationOffset = MAGIC.size)
            }
        }

        fun markerMatches(markerBytes: ByteArray, publicBytes: ByteArray): Boolean {
            validateMarker(markerBytes)
            val storedDigest = markerBytes.copyOfRange(MAGIC.size, MARKER_BYTES)
            val actualDigest = MessageDigest.getInstance("SHA-256").digest(publicBytes)
            return MessageDigest.isEqual(storedDigest, actualDigest)
        }
    }
}
