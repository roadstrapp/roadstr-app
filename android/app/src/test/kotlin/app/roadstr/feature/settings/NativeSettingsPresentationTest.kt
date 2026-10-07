package app.roadstr.feature.settings

import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.navigation.NativeSpeedometerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSettingsPresentationTest {
    @Test
    fun `persisted choice enums retain Flutter values and safe defaults`() {
        assertEquals(NativeSettingsMapEngine.MapLibre, NativeSettingsMapEngine.fromStorage("unknown"))
        assertEquals(NativeSettingsMapEngine.Osm, NativeSettingsMapEngine.fromStorage("osm"))
        assertEquals(
            NativeSettingsRoutingProvider.GraphHopperCloud,
            NativeSettingsRoutingProvider.fromStorage("graphhopper_public"),
        )
        assertEquals(NativeSettingsRoutingProvider.Osrm, NativeSettingsRoutingProvider.fromStorage(null))
        assertEquals(NativeSettingsSearchEngine.Qwant, NativeSettingsSearchEngine.fromStorage("bad"))
        assertEquals(NativeSettingsCursorStyle.Arrow, NativeSettingsCursorStyle.fromStorage("ostrich"))
        assertEquals(NativeSettingsCursorColor.Violet, NativeSettingsCursorColor.fromStorage(1))
        assertEquals(NativeSettingsVoiceGender.Male, NativeSettingsVoiceGender.fromStorage(null))
    }

    @Test
    fun `default input preserves every scalar Flutter default`() {
        val value = NativeSettingsPresenter.present(1, NativeSettingsInput()).values

        assertEquals(RoadstrThemeId.LightNostr, value.themeId)
        assertFalse(value.autoDarkEnabled)
        assertNull(value.languageCode)
        assertFalse(value.avoidUnpavedRoads)
        assertEquals(NativeSettingsMapEngine.MapLibre, value.mapEngine)
        assertTrue(value.keepScreenOn)
        assertFalse(value.keepScreenOnAlways)
        assertEquals(0.0, value.minimumBrightness, 0.0)
        assertFalse(value.showAltitude)
        assertTrue(value.showCrosswalks)
        assertTrue(value.showTrafficLights)
        assertTrue(value.autoCenterOnLaunch)
        assertFalse(value.imperialUnits)
        assertEquals(NativeSettingsRoutingProvider.Osrm, value.routingProvider)
        assertEquals(NativeSettingsSearchEngine.Qwant, value.searchEngine)
        assertTrue(value.voiceEnabled)
        assertEquals(4, value.voiceSpeedStage)
        assertEquals(1.0, value.voiceVolume, 0.0)
    }

    @Test
    fun `language catalogue is exact normalized and immutable`() {
        assertEquals(27, NativeSettingsPresenter.SUPPORTED_LANGUAGE_CODES.size)
        assertTrue(NativeSettingsPresenter.SUPPORTED_LANGUAGE_CODES.containsAll(listOf("en", "it", "ja", "zh")))
        val value = NativeSettingsPresenter.present(
            1,
            NativeSettingsInput(languageCode = " IT "),
        ).values
        assertEquals("it", value.languageCode)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (NativeSettingsPresenter.SUPPORTED_LANGUAGE_CODES as MutableSet<String>).add("xx")
        }
    }

    @Test
    fun `presentation sanitizes bounded public text and safe relay summary`() {
        val values = NativeSettingsPresenter.present(
            1,
            NativeSettingsInput(
                mapTileUrl = "  https://tiles.example/{z}/{x}/{y}.png\u0000 ",
                graphHopperServer = "  http://localhost:8989/route\u0000 ",
                customSyncRelay = " wss://relay.example/path ",
                appVersion = " 0.5.11\u0000 ",
            ),
        ).values

        assertEquals("https://tiles.example/{z}/{x}/{y}.png", values.mapTileUrl)
        assertEquals("http://localhost:8989/route", values.graphHopperServer)
        assertEquals("wss://relay.example/path", values.customSyncRelay)
        assertEquals("0.5.11", values.appVersion)
    }

    @Test
    fun `unsafe custom relay is omitted without rejecting the settings screen`() {
        assertNull(
            NativeSettingsPresenter.present(
                1,
                NativeSettingsInput(customSyncRelay = "ws://user:pass@relay.example/#secret"),
            ).values.customSyncRelay,
        )
    }

    @Test
    fun `voice status controls bounded progress and exact speed stages`() {
        val ready = NativeSettingsPresenter.present(
            1,
            NativeSettingsInput(
                voiceModelStatus = NativeSettingsVoiceModelStatus.Ready,
                voiceDownloadProgress = 0.2,
            ),
        ).values
        val absent = NativeSettingsPresenter.present(
            2,
            NativeSettingsInput(
                voiceModelStatus = NativeSettingsVoiceModelStatus.NotDownloaded,
                voiceDownloadProgress = 0.8,
            ),
        ).values

        assertEquals(1.0, ready.voiceDownloadProgress, 0.0)
        assertEquals(0.0, absent.voiceDownloadProgress, 0.0)
        assertEquals(listOf(0.7, 0.85, 1.0, 1.15, 1.3, 1.5), NativeSettingsPresenter.VOICE_SPEED_STAGES)
        assertEquals(1.3, NativeSettingsPresenter.voiceSpeed(4), 0.0)
    }

    @Test
    fun `presentation rejects invalid numerical and catalogue input`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1, NativeSettingsInput(languageCode = "xx"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1, NativeSettingsInput(minimumBrightness = 1.1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1, NativeSettingsInput(favoritesCount = 1_001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1, NativeSettingsInput(voiceSpeedStage = 6))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1, NativeSettingsInput(voiceVolume = 0.1))
        }
    }

    @Test
    fun `boolean keys preserve exact storage names and defaults`() {
        assertEquals(13, NativeSettingsBooleanKey.entries.size)
        assertEquals("autoDark", NativeSettingsBooleanKey.AutoDark.storageKey)
        assertEquals("darkMapEnabled", NativeSettingsBooleanKey.DarkMap.storageKey)
        assertEquals("roadstr_profile_public", NativeSettingsBooleanKey.ProfilePublic.storageKey)
        assertEquals("favoritesSyncAutoEnabled", NativeSettingsBooleanKey.FavoritesSyncAuto.storageKey)
        assertTrue(NativeSettingsBooleanKey.KeepScreenOn.defaultValue)
        assertTrue(NativeSettingsBooleanKey.ShowCrosswalks.defaultValue)
        assertFalse(NativeSettingsBooleanKey.AvoidUnpavedRoads.defaultValue)
    }

    @Test
    fun `session opens only newer revisions and refreshes the active generation`() {
        val session = NativeSettingsSession()

        assertTrue(session.show(3, NativeSettingsInput(appVersion = "1")))
        assertFalse(session.show(3, NativeSettingsInput()))
        assertFalse(session.show(2, NativeSettingsInput()))
        assertTrue(session.refresh(3, NativeSettingsInput(appVersion = "2")))
        assertEquals("2", session.state.value.values.appVersion)
        assertFalse(session.refresh(2, NativeSettingsInput()))
    }

    @Test
    fun `the saved places list follows a refresh and is gone while the screen is hidden`() {
        val session = NativeSettingsSession()
        val places = listOf(NativeSettingsFavorite("Home", "1 Example Street"), NativeSettingsFavorite("Work", ""))

        assertTrue(session.show(5, NativeSettingsInput(favoritesCount = 2, favoritePlaces = places)))
        assertEquals(places, session.state.value.values.favoritePlaces)

        assertTrue(session.refresh(5, session.state.value.values.copy(favoritesCount = 1, favoritePlaces = places.take(1))))
        assertEquals(places.take(1), session.state.value.values.favoritePlaces)

        assertTrue(session.hide(5))
        assertEquals(emptyList<NativeSettingsFavorite>(), session.state.value.values.favoritePlaces)
        assertEquals(0, session.state.value.values.favoritesCount)
    }

    @Test
    fun `a saved places list longer than the stored maximum is refused`() {
        val tooMany = List(NativeSettingsPresenter.MAX_FAVORITES + 1) { NativeSettingsFavorite("Place $it", "") }

        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1, NativeSettingsInput(favoritePlaces = tooMany))
        }
    }

    @Test
    fun `boolean mutation returns typed persistence write and updates snapshot`() {
        val session = openSession()

        val write = session.updateBoolean(1, NativeSettingsBooleanKey.ImperialUnits, true)

        assertEquals("imperialUnits", write?.storageKey)
        assertEquals(
            NativeSettingsStoredValue.BooleanValue(true),
            write?.value,
        )
        assertTrue(session.state.value.values.imperialUnits)
        assertNull(session.updateBoolean(0, NativeSettingsBooleanKey.ImperialUnits, false))
    }

    @Test
    fun `theme and language writes preserve legacy shapes`() {
        val session = openSession()

        val theme = session.updateTheme(1, RoadstrThemeId.DarkBitcoin)
        val language = session.updateLanguage(1, "DE")
        val system = session.updateLanguage(1, null)

        assertEquals(NativeSettingsStoredValue.IntegerValue(3), theme?.value)
        assertEquals(NativeSettingsStoredValue.StringValue("de"), language?.value)
        assertEquals(NativeSettingsStoredValue.StringValue(""), system?.value)
        assertNull(session.state.value.values.languageCode)
    }

    @Test
    fun `all choice mutations emit exact legacy wire values`() {
        val session = openSession()

        assertWrite(session.updateMapEngine(1, NativeSettingsMapEngine.Osm), "mapEngine", "osm")
        assertWrite(
            session.updateRoutingProvider(1, NativeSettingsRoutingProvider.OpenRouteService),
            "routingProvider",
            "openroute",
        )
        assertWrite(
            session.updateSpeedometer(1, NativeSpeedometerStyle.Sport),
            "speedometerStyle",
            "sport",
        )
        assertWrite(
            session.updateCursorStyle(1, NativeSettingsCursorStyle.Classic500),
            "movementCursorStyle",
            "classic500",
        )
        assertWrite(
            session.updateCursorColor(1, NativeSettingsCursorColor.Red),
            "movementCursorColor",
            "red",
        )
        assertWrite(
            session.updateSearchEngine(1, NativeSettingsSearchEngine.Brave),
            "searchEngine",
            "brave",
        )
    }

    @Test
    fun `slider writes snap to Flutter divisions and remain typed`() {
        val session = openSession()

        val brightness = session.updateBrightness(1, 0.26)
        val volume = session.updateVoiceVolume(1, 0.84)
        val speed = session.updateVoiceSpeedStage(1, 5)

        assertEquals(NativeSettingsStoredValue.DoubleValue(0.3), brightness?.value)
        assertEquals(NativeSettingsStoredValue.DoubleValue(0.8), volume?.value)
        assertEquals(NativeSettingsStoredValue.IntegerValue(5), speed?.value)
        assertEquals(0.3, session.state.value.values.minimumBrightness, 0.0)
    }

    @Test
    fun `text mutations strip controls cap length and permit empty local server`() {
        val session = openSession()

        val tile = session.updateMapTileUrl(1, " https://tiles.example/\u0000 ")
        val server = session.updateGraphHopperServer(1, "   ")

        assertEquals(
            NativeSettingsStoredValue.StringValue("https://tiles.example/"),
            tile?.value,
        )
        assertEquals(NativeSettingsStoredValue.StringValue(""), server?.value)
        assertThrows(IllegalArgumentException::class.java) {
            session.updateMapTileUrl(1, "   ")
        }
    }

    @Test
    fun `hide clears summaries and fences stale mutations`() {
        val session = openSession(
            NativeSettingsInput(
                nwcConfigured = true,
                routingApiKeyConfigured = true,
                syncPassphraseConfigured = true,
            ),
        )

        assertTrue(session.hide(1))
        assertEquals(NativeSettingsStatus.Hidden, session.state.value.status)
        assertFalse(session.state.value.values.nwcConfigured)
        assertFalse(session.state.value.values.routingApiKeyConfigured)
        assertFalse(session.state.value.values.syncPassphraseConfigured)
        assertNull(session.updateBoolean(1, NativeSettingsBooleanKey.VoiceEnabled, false))
        assertFalse(session.hide(1))

        assertTrue(session.reopen(2))
        assertTrue(session.state.value.values.nwcConfigured)
        assertTrue(session.state.value.values.routingApiKeyConfigured)
        assertTrue(session.state.value.values.syncPassphraseConfigured)
    }

    private fun openSession(input: NativeSettingsInput = NativeSettingsInput()): NativeSettingsSession =
        NativeSettingsSession().also { assertTrue(it.show(1, input)) }

    private fun assertWrite(write: NativeSettingsWrite?, key: String, value: String) {
        assertEquals(key, write?.storageKey)
        assertEquals(NativeSettingsStoredValue.StringValue(value), write?.value)
    }
}
