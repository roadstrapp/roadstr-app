package app.roadstr.storage

import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.settings.NativeSettingsBooleanKey
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.feature.settings.NativeSettingsMapEngine
import app.roadstr.feature.settings.NativeSettingsStoredValue
import app.roadstr.feature.settings.NativeSettingsWrite
import app.roadstr.migration.LegacyIdentity
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePreferenceStoreTest {
    @Test
    fun `schema covers every non-secret scalar legacy key only`() {
        assertEquals(35, NativePreferenceSchema.keys.size)
        assertTrue(NativePreferenceSchema.keys.containsAll(NativeSettingsBooleanKey.entries.map { it.storageKey }))
        assertFalse(NativePreferenceSchema.keys.contains("graphhopperApiKey"))
        assertFalse(NativePreferenceSchema.keys.contains("nwcUri"))
        assertFalse(NativePreferenceSchema.keys.contains("fav_sync_pass"))
        assertFalse(NativePreferenceSchema.keys.contains("favorites"))
        assertFalse(NativePreferenceSchema.keys.contains("searchHistory"))
        assertFalse(NativePreferenceSchema.keys.contains("parking_position"))
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (NativePreferenceSchema.keys as MutableSet<String>).add("secret")
        }
    }

    @Test
    fun `codec round trips all scalar types deterministically`() {
        val record = NativePreferenceRecord(
            NativePreferenceRecord.CURRENT_SCHEMA_VERSION,
            7,
            linkedMapOf(
                "language" to NativeSettingsStoredValue.StringValue("it"),
                "themeId" to NativeSettingsStoredValue.IntegerValue(2),
                "autoDark" to NativeSettingsStoredValue.BooleanValue(true),
                "favoritesSyncLastAt" to NativeSettingsStoredValue.LongValue(1_700_000_000_123),
                "minBrightness" to NativeSettingsStoredValue.DoubleValue(0.2),
            ),
        )

        val encoded = NativePreferenceRecordCodec.encode(record)
        val reordered = NativePreferenceRecord(
            record.schemaVersion,
            record.sequence,
            record.values.entries.reversed().associate { it.toPair() },
        )

        assertArrayEquals(encoded, NativePreferenceRecordCodec.encode(reordered))
        assertEquals(record, NativePreferenceRecordCodec.decode(encoded))
    }

    @Test
    fun `codec rejects corruption unknown keys wrong types and non-finite values`() {
        val valid = NativePreferenceRecordCodec.encode(
            NativePreferenceRecord(
                1,
                0,
                mapOf("language" to NativeSettingsStoredValue.StringValue("it")),
            ),
        )
        valid[valid.lastIndex - 1] = (valid[valid.lastIndex - 1].toInt() xor 1).toByte()
        assertThrows(NativePreferenceCodecException::class.java) {
            NativePreferenceRecordCodec.decode(valid)
        }
        assertThrows(NativePreferenceCodecException::class.java) {
            NativePreferenceRecordCodec.encode(
                NativePreferenceRecord(1, 0, mapOf("nwcUri" to NativeSettingsStoredValue.StringValue("secret"))),
            )
        }
        assertThrows(NativePreferenceCodecException::class.java) {
            NativePreferenceRecordCodec.encode(
                NativePreferenceRecord(1, 0, mapOf("themeId" to NativeSettingsStoredValue.StringValue("2"))),
            )
        }
        assertThrows(NativePreferenceCodecException::class.java) {
            NativePreferenceRecordCodec.encode(
                NativePreferenceRecord(1, 0, mapOf("minBrightness" to NativeSettingsStoredValue.DoubleValue(Double.NaN))),
            )
        }
    }

    @Test
    fun `legacy import is strict typed bounded and ignores non-scalar values`() {
        val imported = NativePreferenceSchema.importLegacy(
            mapOf(
                "autoDark" to "true",
                "themeId" to "6",
                "favoritesSyncLastAt" to "1700000000123",
                "minBrightness" to "0.3",
                "language" to "it",
                "favorites" to "[]",
                "searchHistory" to "[]",
                "nwcUri" to "must-not-enter",
            ),
        )

        assertEquals(NativeSettingsStoredValue.BooleanValue(true), imported["autoDark"])
        assertEquals(NativeSettingsStoredValue.IntegerValue(6), imported["themeId"])
        assertEquals(
            NativeSettingsStoredValue.LongValue(1_700_000_000_123),
            imported["favoritesSyncLastAt"],
        )
        assertEquals(NativeSettingsStoredValue.DoubleValue(0.3), imported["minBrightness"])
        assertEquals(NativeSettingsStoredValue.StringValue("it"), imported["language"])
        assertFalse(imported.containsKey("favorites"))
        assertFalse(imported.containsKey("searchHistory"))
        assertFalse(imported.containsKey("nwcUri"))
        assertThrows(NativePreferenceCodecException::class.java) {
            NativePreferenceSchema.importLegacy(mapOf("autoDark" to "1"))
        }
    }

    @Test
    fun `settings projection preserves Flutter defaults aliases and external summaries`() {
        val values = NativePreferenceSchema.importLegacy(
            mapOf(
                "themeId" to "6",
                "autoDark" to "true",
                "language" to "xx",
                "roadstr_profile_public" to "true",
                "mapEngine" to "osm",
                "minBrightness" to "0.3",
                "movementCursorColor" to "bitcoin",
                "favoritesSyncLastAt" to "1700000000123",
                "kokoroSpeedStage" to "2",
                "kokoroVolume" to "0.8",
            ),
        )
        val projected = NativePreferenceSchema.projectSettings(
            NativePreferenceRecord(1, 0, values),
            NativeSettingsInput(
                routingApiKeyConfigured = true,
                nwcConfigured = true,
                favoritesCount = 4,
                appVersion = "0.5.11",
            ),
        )

        assertEquals(RoadstrThemeId.DarkNostr, projected.themeId)
        assertTrue(projected.autoDarkEnabled)
        assertNull(projected.languageCode)
        assertTrue(projected.profilePublic)
        assertEquals(NativeSettingsMapEngine.Osm, projected.mapEngine)
        assertEquals(0.3, projected.minimumBrightness, 0.0)
        assertEquals(1_700_000_000_123L, projected.lastSyncMillis)
        assertEquals(2, projected.voiceSpeedStage)
        assertEquals(0.8, projected.voiceVolume, 0.0)
        assertTrue(projected.routingApiKeyConfigured)
        assertTrue(projected.nwcConfigured)
        assertEquals(4, projected.favoritesCount)
        assertEquals("0.5.11", projected.appVersion)
    }

    @Test
    fun `snapshot import persists and is idempotent across reopen`() = withTempDirectory { directory ->
        val store = FileNativePreferenceStore(directory)
        val first = snapshot(mapOf("language" to "it", "themeId" to "2"))
        val second = snapshot(mapOf("language" to "de", "themeId" to "3"))

        assertEquals(NativePreferenceImportOutcome.Imported, store.importSnapshot(first))
        assertEquals(NativePreferenceImportOutcome.AlreadyPresent, store.importSnapshot(second))

        val reopened = FileNativePreferenceStore(directory)
        assertEquals("it", reopened.settings().languageCode)
        assertEquals(RoadstrThemeId.DarkNostr, reopened.settings().themeId)
        assertEquals(0L, reopened.read()?.sequence)
    }

    @Test
    fun `typed writes survive reopen and same UI revision can update multiple keys`() =
        withTempDirectory { directory ->
            val store = FileNativePreferenceStore(directory)

            assertTrue(store.apply(write(4, "language", NativeSettingsStoredValue.StringValue("de"))))
            assertTrue(store.apply(write(4, "autoDark", NativeSettingsStoredValue.BooleanValue(true))))

            val reopened = FileNativePreferenceStore(directory)
            assertEquals("de", reopened.settings().languageCode)
            assertTrue(reopened.settings().autoDarkEnabled)
            assertEquals(2L, reopened.read()?.sequence)
        }

    @Test
    fun `process-local revision fencing rejects stale callbacks without poisoning reopen`() =
        withTempDirectory { directory ->
            val store = FileNativePreferenceStore(directory)
            assertTrue(store.apply(write(8, "language", NativeSettingsStoredValue.StringValue("it"))))
            assertFalse(store.apply(write(7, "language", NativeSettingsStoredValue.StringValue("de"))))
            assertEquals("it", store.settings().languageCode)

            val reopened = FileNativePreferenceStore(directory)
            assertTrue(reopened.apply(write(0, "language", NativeSettingsStoredValue.StringValue("de"))))
            assertEquals("de", reopened.settings().languageCode)
        }

    @Test
    fun `protected and unsupported writes fail before touching disk`() = withTempDirectory { directory ->
        val store = FileNativePreferenceStore(directory)

        assertThrows(NativePreferenceCodecException::class.java) {
            store.apply(write(1, "nwcUri", NativeSettingsStoredValue.StringValue("secret")))
        }
        assertFalse(File(directory, NATIVE_PREFERENCE_FILE).exists())
    }

    @Test
    fun `corrupt active file fails closed without replacing it`() = withTempDirectory { directory ->
        val store = FileNativePreferenceStore(directory)
        assertTrue(store.apply(write(1, "language", NativeSettingsStoredValue.StringValue("it"))))
        val active = File(directory, NATIVE_PREFERENCE_FILE)
        RandomAccessFile(active, "rw").use { file ->
            val lastByteOffset = file.length() - 1
            file.seek(lastByteOffset)
            val original = file.readByte().toInt()
            file.seek(lastByteOffset)
            file.writeByte(original xor 1)
            file.fd.sync()
        }

        assertThrows(NativePersistenceException::class.java) {
            FileNativePreferenceStore(directory).read()
        }
        assertTrue(active.isFile)
    }

    private fun snapshot(values: Map<String, String>) = NativeSnapshotRecord(
        schemaVersion = NativeSnapshotRecord.CURRENT_SCHEMA_VERSION,
        ordinaryValues = values,
        secureValueDigests = emptyMap(),
        identity = LegacyIdentity(null, null, null),
        assets = emptyList(),
    )

    private fun write(revision: Long, key: String, value: NativeSettingsStoredValue) =
        NativeSettingsWrite(revision, key, value)

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-preferences-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
