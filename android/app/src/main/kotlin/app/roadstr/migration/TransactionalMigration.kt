package app.roadstr.migration

/** Reads legacy data without mutating or deleting it. */
fun interface LegacySnapshotReader {
    fun read(): LegacyStorageSnapshot?
}

/**
 * Writes a complete replacement into native staging, then atomically commits
 * it. Implementations must make stage/commit/verify safe to retry.
 */
interface NativeSnapshotWriter {
    fun stage(snapshot: LegacyStorageSnapshot)
    fun commit()
    fun verify(snapshot: LegacyStorageSnapshot)
}

/** Completion marker is written only after the native store has been verified. */
interface MigrationMarker {
    fun isComplete(): Boolean
    fun markComplete()
}

enum class MigrationOutcome {
    AlreadyComplete,
    NotNeeded,
    Migrated,
    Failed,
}

data class MigrationResult(
    val outcome: MigrationOutcome,
    val reason: String? = null,
)

/**
 * Crash-safe orchestration for the first native startup.
 *
 * Deliberately absent from this class: any delete operation on legacy data.
 * Legacy files stay available for rollback until a later, separately approved
 * cleanup policy is implemented.
 */
class TransactionalMigration(
    private val reader: LegacySnapshotReader,
    private val writer: NativeSnapshotWriter,
    private val marker: MigrationMarker,
    private val identityVerifier: IdentityVerifier,
) {
    fun run(): MigrationResult {
        if (marker.isComplete()) return MigrationResult(MigrationOutcome.AlreadyComplete)

        val snapshot = try {
            reader.read()
        } catch (error: RuntimeException) {
            return MigrationResult(MigrationOutcome.Failed, "Legacy read failed: ${error.message}")
        } ?: return MigrationResult(MigrationOutcome.NotNeeded)

        val validation = LegacyStorageValidator.validate(snapshot, identityVerifier)
        if (!validation.valid) {
            return MigrationResult(MigrationOutcome.Failed, validation.reason)
        }

        return try {
            // Each operation is retryable; the marker is intentionally last.
            writer.stage(snapshot)
            writer.commit()
            writer.verify(snapshot)
            marker.markComplete()
            MigrationResult(MigrationOutcome.Migrated)
        } catch (error: RuntimeException) {
            MigrationResult(MigrationOutcome.Failed, "Native migration failed: ${error.message}")
        }
    }
}
