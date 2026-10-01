package app.roadstr.storage

import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.navigation.NativeSpeedometerStyle
import app.roadstr.feature.settings.NativeSettingsBooleanKey
import app.roadstr.feature.settings.NativeSettingsCursorColor
import app.roadstr.feature.settings.NativeSettingsCursorStyle
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.feature.settings.NativeSettingsMapEngine
import app.roadstr.feature.settings.NativeSettingsPresenter
import app.roadstr.feature.settings.NativeSettingsRoutingProvider
import app.roadstr.feature.settings.NativeSettingsSearchEngine
import app.roadstr.feature.settings.NativeSettingsStoredValue
import app.roadstr.feature.settings.NativeSettingsVoiceGender
import app.roadstr.feature.settings.NativeSettingsWrite
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.TreeMap

internal const val NATIVE_PREFERENCE_FILE = "native_preferences_v1.bin"

class NativePreferenceCodecException(message: String) : IllegalArgumentException(message)

class NativePreferenceRecord(
    val schemaVersion: Int,
    val sequence: Long,
    values: Map<String, NativeSettingsStoredValue>,
) {
    val values: Map<String, NativeSettingsStoredValue> =
        Collections.unmodifiableMap(TreeMap(values))

    override fun equals(other: Any?): Boolean =
        other is NativePreferenceRecord &&
            schemaVersion == other.schemaVersion &&
            sequence == other.sequence &&
            values == other.values

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + sequence.hashCode()
        result = 31 * result + values.hashCode()
        return result
    }

    override fun toString(): String =
        "NativePreferenceRecord(schemaVersion=$schemaVersion, sequence=$sequence, keys=${values.keys})"

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

private enum class NativePreferenceKind(val wireId: Int) {
    BooleanValue(1),
    IntegerValue(2),
    LongValue(3),
    DoubleValue(4),
    StringValue(5),
}

/** Closed non-secret scalar key catalogue shared by migration and Settings UI. */
object NativePreferenceSchema {
    private val booleanKeys = NativeSettingsBooleanKey.entries.mapTo(mutableSetOf()) {
        it.storageKey
    }.apply {
        addAll(
            listOf(
                "disclaimer_accepted",
                "fav_sync_legacy_cleaned",
                "onboarding_v1",
                "privacy_disclosure_v2",
                "road_report_privacy_ack",
                "voice_unsupported_notice_shown",
            ),
        )
    }
    private val integerKeys = setOf("kokoroSpeedStage", "themeId")
    private val longKeys = setOf("fav_sync_last_ts", "favoritesSyncLastAt")
    private val doubleKeys = setOf("kokoroVolume", "minBrightness")
    private val stringKeys = setOf(
        "fav_sync_custom_relay",
        "graphhopperServer",
        "kokoroVoiceGender",
        "language",
        "mapEngine",
        "mapTileUrl",
        "movementCursorColor",
        "movementCursorStyle",
        "routingProvider",
        "searchEngine",
        "speedometerStyle",
    )

    val keys: Set<String> = Collections.unmodifiableSet(
        (booleanKeys + integerKeys + longKeys + doubleKeys + stringKeys).toSortedSet(),
    )

    private fun kind(key: String): NativePreferenceKind? = when (key) {
        in booleanKeys -> NativePreferenceKind.BooleanValue
        in integerKeys -> NativePreferenceKind.IntegerValue
        in longKeys -> NativePreferenceKind.LongValue
        in doubleKeys -> NativePreferenceKind.DoubleValue
        in stringKeys -> NativePreferenceKind.StringValue
        else -> null
    }

    internal fun validate(key: String, value: NativeSettingsStoredValue) {
        val actual = when (value) {
            is NativeSettingsStoredValue.BooleanValue -> NativePreferenceKind.BooleanValue
            is NativeSettingsStoredValue.IntegerValue -> NativePreferenceKind.IntegerValue
            is NativeSettingsStoredValue.LongValue -> NativePreferenceKind.LongValue
            is NativeSettingsStoredValue.DoubleValue -> NativePreferenceKind.DoubleValue
            is NativeSettingsStoredValue.StringValue -> NativePreferenceKind.StringValue
        }
        if (kind(key) != actual) {
            throw NativePreferenceCodecException("Native preference type is invalid")
        }
        when (value) {
            is NativeSettingsStoredValue.DoubleValue -> if (!value.value.isFinite()) {
                throw NativePreferenceCodecException("Native preference number is invalid")
            }
            is NativeSettingsStoredValue.StringValue -> if (
                value.value.toByteArray(Charsets.UTF_8).size > MAX_STRING_BYTES
            ) {
                throw NativePreferenceCodecException("Native preference text is too large")
            }
            else -> Unit
        }
    }

    fun importLegacy(values: Map<String, String>): Map<String, NativeSettingsStoredValue> {
        val imported = TreeMap<String, NativeSettingsStoredValue>()
        try {
            for (key in keys) {
                val raw = values[key] ?: continue
                val value = when (kind(key)) {
                    NativePreferenceKind.BooleanValue -> NativeSettingsStoredValue.BooleanValue(
                        when (raw) {
                            "true" -> true
                            "false" -> false
                            else -> throw NativePreferenceCodecException("Legacy preference boolean is invalid")
                        },
                    )
                    NativePreferenceKind.IntegerValue ->
                        NativeSettingsStoredValue.IntegerValue(raw.toInt())
                    NativePreferenceKind.LongValue ->
                        NativeSettingsStoredValue.LongValue(raw.toLong())
                    NativePreferenceKind.DoubleValue ->
                        NativeSettingsStoredValue.DoubleValue(raw.toDouble())
                    NativePreferenceKind.StringValue ->
                        NativeSettingsStoredValue.StringValue(raw)
                    null -> continue
                }
                validate(key, value)
                imported[key] = value
            }
        } catch (error: NativePreferenceCodecException) {
            throw error
        } catch (_: RuntimeException) {
            throw NativePreferenceCodecException("Legacy preference value is invalid")
        }
        return Collections.unmodifiableMap(imported)
    }

    fun projectSettings(
        record: NativePreferenceRecord?,
        base: NativeSettingsInput = NativeSettingsInput(),
    ): NativeSettingsInput {
        val values = record?.values.orEmpty()
        fun bool(key: NativeSettingsBooleanKey): Boolean =
            (values[key.storageKey] as? NativeSettingsStoredValue.BooleanValue)?.value
                ?: key.defaultValue
        fun text(key: String): String? =
            (values[key] as? NativeSettingsStoredValue.StringValue)?.value
        fun integer(key: String): Int? =
            (values[key] as? NativeSettingsStoredValue.IntegerValue)?.value
        fun long(key: String): Long? =
            (values[key] as? NativeSettingsStoredValue.LongValue)?.value
        fun decimal(key: String): Double? =
            (values[key] as? NativeSettingsStoredValue.DoubleValue)?.value

        val language = text("language")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in NativeSettingsPresenter.SUPPORTED_LANGUAGE_CODES }
        val brightness = decimal("minBrightness")
            ?.takeIf { it in 0.0..1.0 }
            ?: NativeSettingsInput().minimumBrightness
        val volume = decimal("kokoroVolume")
            ?.takeIf { it in 0.2..1.0 }
            ?: NativeSettingsInput().voiceVolume
        val speedStage = integer("kokoroSpeedStage")
            ?.takeIf { it in NativeSettingsPresenter.VOICE_SPEED_STAGES.indices }
            ?: NativeSettingsInput.DEFAULT_VOICE_SPEED_STAGE
        val tileUrl = text("mapTileUrl")
            ?.takeIf(String::isNotBlank)
            ?: NativeSettingsInput.DEFAULT_TILE_URL
        val lastSync = long("favoritesSyncLastAt")
            ?.takeIf { it in 0..NativeSettingsPresenter.MAX_SYNC_MILLIS }

        return NativeSettingsPresenter.present(
            revision = 0,
            input = base.copy(
                themeId = RoadstrThemeId.fromStoredOrdinal(integer("themeId") ?: 0),
                autoDarkEnabled = bool(NativeSettingsBooleanKey.AutoDark),
                languageCode = language,
                profilePublic = bool(NativeSettingsBooleanKey.ProfilePublic),
                avoidUnpavedRoads = bool(NativeSettingsBooleanKey.AvoidUnpavedRoads),
                mapEngine = NativeSettingsMapEngine.fromStorage(text("mapEngine")),
                keepScreenOn = bool(NativeSettingsBooleanKey.KeepScreenOn),
                keepScreenOnAlways = bool(NativeSettingsBooleanKey.KeepScreenOnAlways),
                minimumBrightness = brightness,
                showAltitude = bool(NativeSettingsBooleanKey.ShowAltitude),
                showCrosswalks = bool(NativeSettingsBooleanKey.ShowCrosswalks),
                showTrafficLights = bool(NativeSettingsBooleanKey.ShowTrafficLights),
                autoCenterOnLaunch = bool(NativeSettingsBooleanKey.AutoCenterOnLaunch),
                imperialUnits = bool(NativeSettingsBooleanKey.ImperialUnits),
                mapTileUrl = tileUrl,
                routingProvider = NativeSettingsRoutingProvider.fromStorage(text("routingProvider")),
                graphHopperServer = text("graphhopperServer").orEmpty(),
                speedometerStyle = NativeSpeedometerStyle.fromStorage(text("speedometerStyle")),
                cursorStyle = NativeSettingsCursorStyle.fromStorage(text("movementCursorStyle")),
                cursorColor = NativeSettingsCursorColor.fromStorage(text("movementCursorColor")),
                searchEngine = NativeSettingsSearchEngine.fromStorage(text("searchEngine")),
                favoritesSyncAutoEnabled = bool(NativeSettingsBooleanKey.FavoritesSyncAuto),
                customSyncRelay = text("fav_sync_custom_relay"),
                lastSyncMillis = lastSync,
                voiceEnabled = bool(NativeSettingsBooleanKey.VoiceEnabled),
                voiceGender = NativeSettingsVoiceGender.fromStorage(text("kokoroVoiceGender")),
                voiceSpeedStage = speedStage,
                voiceVolume = volume,
            ),
        ).values
    }

    const val MAX_STRING_BYTES = 8 * 1024
}

object NativePreferenceRecordCodec {
    private val MAGIC = "RSTRPRF1".toByteArray(Charsets.US_ASCII)
    private const val DIGEST_BYTES = 32
    const val MAX_RECORD_BYTES = 128 * 1024

    fun encode(record: NativePreferenceRecord): ByteArray {
        validateRecord(record)
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(record.schemaVersion)
                output.writeLong(record.sequence)
                output.writeInt(record.values.size)
                for ((key, value) in record.values) {
                    output.writeText(key)
                    when (value) {
                        is NativeSettingsStoredValue.BooleanValue -> {
                            output.writeByte(NativePreferenceKind.BooleanValue.wireId)
                            output.writeBoolean(value.value)
                        }
                        is NativeSettingsStoredValue.IntegerValue -> {
                            output.writeByte(NativePreferenceKind.IntegerValue.wireId)
                            output.writeInt(value.value)
                        }
                        is NativeSettingsStoredValue.LongValue -> {
                            output.writeByte(NativePreferenceKind.LongValue.wireId)
                            output.writeLong(value.value)
                        }
                        is NativeSettingsStoredValue.DoubleValue -> {
                            output.writeByte(NativePreferenceKind.DoubleValue.wireId)
                            output.writeDouble(value.value)
                        }
                        is NativeSettingsStoredValue.StringValue -> {
                            output.writeByte(NativePreferenceKind.StringValue.wireId)
                            output.writeText(value.value)
                        }
                    }
                }
            }
            bytes.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val encoded = payload + digest
        if (encoded.size > MAX_RECORD_BYTES) {
            throw NativePreferenceCodecException("Native preference record is too large")
        }
        return encoded
    }

    fun decode(encoded: ByteArray): NativePreferenceRecord {
        if (encoded.size <= MAGIC.size + DIGEST_BYTES || encoded.size > MAX_RECORD_BYTES) {
            throw NativePreferenceCodecException("Native preference record size is invalid")
        }
        val payload = encoded.copyOfRange(0, encoded.size - DIGEST_BYTES)
        val expected = encoded.copyOfRange(encoded.size - DIGEST_BYTES, encoded.size)
        val actual = MessageDigest.getInstance("SHA-256").digest(payload)
        if (!MessageDigest.isEqual(expected, actual)) {
            throw NativePreferenceCodecException("Native preference record digest is invalid")
        }
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                if (!MessageDigest.isEqual(MAGIC, magic)) {
                    throw NativePreferenceCodecException("Native preference record magic is invalid")
                }
                val schemaVersion = input.readInt()
                val sequence = input.readLong()
                val count = input.readInt()
                if (count !in 0..NativePreferenceSchema.keys.size) {
                    throw NativePreferenceCodecException("Native preference count is invalid")
                }
                val values = TreeMap<String, NativeSettingsStoredValue>()
                var previousKey: String? = null
                repeat(count) {
                    val key = input.readText(MAX_KEY_BYTES)
                    if (previousKey != null && key <= previousKey!!) {
                        throw NativePreferenceCodecException("Native preference order is invalid")
                    }
                    previousKey = key
                    val wireId = input.readUnsignedByte()
                    val kind = NativePreferenceKind.entries.firstOrNull {
                        it.wireId == wireId
                    } ?: throw NativePreferenceCodecException("Native preference type is invalid")
                    val value = when (kind) {
                        NativePreferenceKind.BooleanValue ->
                            NativeSettingsStoredValue.BooleanValue(input.readBoolean())
                        NativePreferenceKind.IntegerValue ->
                            NativeSettingsStoredValue.IntegerValue(input.readInt())
                        NativePreferenceKind.LongValue ->
                            NativeSettingsStoredValue.LongValue(input.readLong())
                        NativePreferenceKind.DoubleValue ->
                            NativeSettingsStoredValue.DoubleValue(input.readDouble())
                        NativePreferenceKind.StringValue ->
                            NativeSettingsStoredValue.StringValue(
                                input.readText(NativePreferenceSchema.MAX_STRING_BYTES),
                            )
                    }
                    NativePreferenceSchema.validate(key, value)
                    values[key] = value
                }
                if (input.available() != 0) {
                    throw NativePreferenceCodecException("Native preference record has trailing data")
                }
                NativePreferenceRecord(schemaVersion, sequence, values).also(::validateRecord)
            }
        } catch (error: NativePreferenceCodecException) {
            throw error
        } catch (_: Exception) {
            throw NativePreferenceCodecException("Native preference record cannot be decoded")
        }
    }

    private fun validateRecord(record: NativePreferenceRecord) {
        if (record.schemaVersion != NativePreferenceRecord.CURRENT_SCHEMA_VERSION || record.sequence < 0) {
            throw NativePreferenceCodecException("Native preference record metadata is invalid")
        }
        if (record.values.size > NativePreferenceSchema.keys.size) {
            throw NativePreferenceCodecException("Native preference count is invalid")
        }
        record.values.forEach { (key, value) ->
            NativePreferenceSchema.validate(key, value)
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(limit: Int): String {
        val size = readInt()
        if (size !in 0..limit || size > available()) {
            throw NativePreferenceCodecException("Native preference text size is invalid")
        }
        val bytes = ByteArray(size).also(::readFully)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private const val MAX_KEY_BYTES = 128
}

enum class NativePreferenceImportOutcome {
    Imported,
    AlreadyPresent,
}

/**
 * Recoverable app-private persistence for non-secret scalar preferences.
 *
 * The migration snapshot remains immutable. This store imports only the
 * closed public scalar catalogue, then applies typed Settings writes through
 * atomic replace/reopen verification. It never accepts protected aliases.
 */
class FileNativePreferenceStore(directory: File) {
    private val lock = Any()
    private var acceptedUiRevision = -1L
    private val file = RecoverableAtomicFile(
        directory = directory,
        fileName = NATIVE_PREFERENCE_FILE,
        maxBytes = NativePreferenceRecordCodec.MAX_RECORD_BYTES,
        validator = { NativePreferenceRecordCodec.decode(it) },
    )

    fun read(): NativePreferenceRecord? = synchronized(lock) {
        readUnlocked()
    }

    fun importSnapshot(snapshot: NativeSnapshotRecord): NativePreferenceImportOutcome = synchronized(lock) {
        if (readUnlocked() != null) return NativePreferenceImportOutcome.AlreadyPresent
        val values = NativePreferenceSchema.importLegacy(snapshot.ordinaryValues)
        persist(NativePreferenceRecord(NativePreferenceRecord.CURRENT_SCHEMA_VERSION, 0, values))
        NativePreferenceImportOutcome.Imported
    }

    fun apply(write: NativeSettingsWrite): Boolean = synchronized(lock) {
        require(write.revision >= 0) { "Settings write revision must be non-negative" }
        if (write.revision < acceptedUiRevision) return false
        NativePreferenceSchema.validate(write.storageKey, write.value)
        val current = readUnlocked() ?: NativePreferenceRecord(
            NativePreferenceRecord.CURRENT_SCHEMA_VERSION,
            0,
            emptyMap(),
        )
        if (write.revision == acceptedUiRevision && current.values[write.storageKey] == write.value) {
            return true
        }
        val updated = TreeMap(current.values)
        updated[write.storageKey] = write.value
        persist(
            NativePreferenceRecord(
                NativePreferenceRecord.CURRENT_SCHEMA_VERSION,
                current.sequence + 1,
                updated,
            ),
        )
        acceptedUiRevision = write.revision
        true
    }

    fun settings(base: NativeSettingsInput = NativeSettingsInput()): NativeSettingsInput =
        synchronized(lock) {
            NativePreferenceSchema.projectSettings(readUnlocked(), base)
        }

    private fun persist(record: NativePreferenceRecord) {
        val encoded = NativePreferenceRecordCodec.encode(record)
        file.stage(encoded)
        file.commit()
        val reopened = readUnlocked()
        if (reopened != record) {
            throw NativePersistenceException("Native preference reopen verification failed")
        }
    }

    private fun readUnlocked(): NativePreferenceRecord? =
        file.read()?.let(NativePreferenceRecordCodec::decode)
}
