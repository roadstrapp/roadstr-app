package app.roadstr.core.journey

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

object JourneyRecordCodec {
    private const val MAGIC = "RSTRJNY1"
    const val MAX_SESSIONS = 10_000
    const val MAX_UNLOCKS = 17
    const val MAX_ENCODED_BYTES = 2 * 1024 * 1024

    fun encode(record: JourneyRecord): String {
        validate(record)
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(MAGIC.toByteArray(Charsets.US_ASCII))
                output.writeInt(record.formatVersion)
                val sessions = record.sessions.distinctBy { it.id }.sortedBy { it.startedAtEpochMillis }
                output.writeInt(sessions.size)
                sessions.forEach { session ->
                    output.writeUTF(session.id)
                    output.writeUTF(session.deviceId)
                    output.writeInt(session.mode.ordinal)
                    output.writeLong(session.startedAtEpochMillis)
                    output.writeLong(session.endedAtEpochMillis ?: -1L)
                    output.writeLong(session.validatedMeters)
                    output.writeLong(session.uncertainMeters)
                    output.writeInt(session.status.ordinal)
                    output.writeInt(session.formatVersion)
                }
                val unlocks = record.unlocks.distinctBy { it.achievementId }
                    .sortedBy { it.achievementId }
                output.writeInt(unlocks.size)
                unlocks.forEach { unlock ->
                    output.writeUTF(unlock.achievementId)
                    output.writeLong(unlock.unlockedAtEpochMillis)
                }
            }
            buffer.toByteArray()
        }
        require(bytes.size <= MAX_ENCODED_BYTES) { "Journey record is too large" }
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(encoded: String?): JourneyRecord {
        if (encoded.isNullOrBlank()) return JourneyRecord()
        require(encoded.length <= MAX_ENCODED_BYTES * 2) { "Journey record is too large" }
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size <= MAX_ENCODED_BYTES) { "Journey record is too large" }
        val record = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = ByteArray(MAGIC.length).also(input::readFully).toString(Charsets.US_ASCII)
            require(magic == MAGIC) { "Invalid journey record header" }
            val version = input.readInt()
            require(version == JOURNEY_FORMAT_VERSION) { "Unsupported journey record version" }
            val sessionCount = input.readInt()
            require(sessionCount in 0..MAX_SESSIONS) { "Invalid journey session count" }
            val sessions = List(sessionCount) {
                JourneySession(
                    id = input.readUTF(),
                    deviceId = input.readUTF(),
                    mode = JourneyMode.entries.getOrNull(input.readInt())
                        ?: error("Invalid journey mode"),
                    startedAtEpochMillis = input.readLong(),
                    endedAtEpochMillis = input.readLong().takeIf { it >= 0L },
                    validatedMeters = input.readLong(),
                    uncertainMeters = input.readLong(),
                    status = JourneySessionStatus.entries.getOrNull(input.readInt())
                        ?: error("Invalid journey status"),
                    formatVersion = input.readInt(),
                )
            }
            val unlockCount = input.readInt()
            require(unlockCount in 0..MAX_UNLOCKS) { "Invalid achievement count" }
            val unlocks = List(unlockCount) {
                JourneyAchievementUnlock(input.readUTF(), input.readLong())
            }
            require(input.read() == -1) { "Journey record has trailing data" }
            JourneyRecord(sessions, unlocks, version)
        }
        validate(record)
        return record
    }

    private fun validate(record: JourneyRecord) {
        require(record.formatVersion == JOURNEY_FORMAT_VERSION)
        require(record.sessions.size <= MAX_SESSIONS)
        require(record.sessions.map { it.id }.distinct().size == record.sessions.size)
        require(record.unlocks.size <= MAX_UNLOCKS)
        require(record.unlocks.map { it.achievementId }.distinct().size == record.unlocks.size)
        require(record.unlocks.all { unlock ->
            unlock.unlockedAtEpochMillis >= 0L &&
                AchievementCatalog.achievements.any { it.id == unlock.achievementId }
        })
    }
}

interface JourneyStore {
    fun load(): JourneyRecord
    fun save(record: JourneyRecord)
    fun clear()
}

