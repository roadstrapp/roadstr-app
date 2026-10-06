package app.roadstr.startup

import app.roadstr.migration.LegacySnapshotReader
import app.roadstr.migration.LegacyStorageContract
import app.roadstr.migration.LegacyStorageSnapshot

/**
 * Hands the validator only what the new app understands.
 *
 * The old settings box is never cleaned of keys that older versions wrote and later versions stopped
 * using, so a real box can hold names no list of today's keys has. The validator refuses a snapshot with
 * an unknown key, which is right for a fixture and wrong for a person: it would leave them on the
 * "protected data unavailable" screen over a value the new app could not use anyway. Unknown keys are
 * left in the old files, where they have always been, and the rest goes through every check.
 */
class TolerantLegacySnapshotReader(private val delegate: LegacySnapshotReader) : LegacySnapshotReader {
    override fun read(): LegacyStorageSnapshot? {
        val snapshot = delegate.read() ?: return null
        return snapshot.copy(
            ordinaryValues = snapshot.ordinaryValues.filterKeys(::isKnownOrdinary),
            secureValues = snapshot.secureValues.filterKeys { it in LegacyStorageContract.secureKeys },
        )
    }

    // A secret named in the ordinary box would be refused as a leak, so it is dropped here too.
    private fun isKnownOrdinary(key: String): Boolean =
        key !in LegacyStorageContract.secureKeys &&
            (key in LegacyStorageContract.hiveKeys || LegacyStorageContract.isDynamicKey(key))
}
