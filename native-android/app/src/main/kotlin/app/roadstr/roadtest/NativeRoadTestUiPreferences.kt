package app.roadstr.roadtest

import android.content.Context
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.navigation.NativeSpeedometerStyle
import app.roadstr.feature.settings.NativeSettingsCursorColor
import app.roadstr.feature.settings.NativeSettingsCursorStyle
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.feature.settings.NativeSettingsRoutingProvider
import app.roadstr.feature.settings.NativeSettingsSearchEngine
import app.roadstr.feature.settings.NativeSettingsVoiceGender

/** Small scalar bridge for the standalone harness; secrets never enter it. */
class NativeRoadTestUiPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): NativeSettingsInput = NativeSettingsInput(
        themeId = RoadstrThemeId.fromStoredOrdinal(preferences.getInt("theme", 0)),
        autoDarkEnabled = preferences.getBoolean("auto_dark", false),
        darkMapEnabled = preferences.getBoolean("dark_map", false),
        languageCode = preferences.getString("language", null),
        keepScreenOn = preferences.getBoolean("keep_screen_on", true),
        keepScreenOnAlways = preferences.getBoolean("keep_screen_on_always", false),
        minimumBrightness = preferences.getFloat("min_brightness", 0f).toDouble(),
        autoCenterOnLaunch = preferences.getBoolean("auto_center", true),
        mapTileUrl = preferences.getString("tile_url", null) ?: NativeSettingsInput().mapTileUrl,
        routingProvider = NativeSettingsRoutingProvider.fromStorage(
            preferences.getString("routing_provider", null),
        ),
        graphHopperServer = preferences.getString("graphhopper_server", null).orEmpty(),
        searchEngine = NativeSettingsSearchEngine.fromStorage(
            preferences.getString("search_engine", null),
        ),
        voiceEnabled = preferences.getBoolean("voice_enabled", true),
        voiceGender = NativeSettingsVoiceGender.fromStorage(
            preferences.getString("voice_gender", null),
        ),
        voiceSpeedStage = preferences.getInt(
            "voice_speed_stage",
            NativeSettingsInput.DEFAULT_VOICE_SPEED_STAGE,
        ),
        voiceVolume = preferences.getFloat("voice_volume", 1f).toDouble(),
        profilePublic = preferences.getBoolean("profile_public", false),
        avoidUnpavedRoads = preferences.getBoolean("avoid_unpaved", false),
        mapEngine = app.roadstr.feature.settings.NativeSettingsMapEngine.fromStorage(
            preferences.getString("map_engine", null),
        ),
        showAltitude = preferences.getBoolean("show_altitude", false),
        showCrosswalks = preferences.getBoolean("show_crosswalks", true),
        showTrafficLights = preferences.getBoolean("show_traffic_lights", true),
        favoritesSyncAutoEnabled = preferences.getBoolean("favorites_sync_auto", false),
        imperialUnits = preferences.getBoolean("imperial", false),
        speedometerStyle = NativeSpeedometerStyle.fromStorage(
            preferences.getString("speedometer", null),
        ),
        cursorStyle = NativeSettingsCursorStyle.fromStorage(
            preferences.getString("cursor_style", null),
        ),
        cursorColor = NativeSettingsCursorColor.fromStorage(
            preferences.getString("cursor_color", null),
        ),
    )

    fun save(value: NativeSettingsInput) {
        preferences.edit()
            .putInt("theme", value.themeId.storedOrdinal)
            .putBoolean("auto_dark", value.autoDarkEnabled)
            .putBoolean("dark_map", value.darkMapEnabled)
            .putString("language", value.languageCode)
            .putBoolean("keep_screen_on", value.keepScreenOn)
            .putBoolean("keep_screen_on_always", value.keepScreenOnAlways)
            .putFloat("min_brightness", value.minimumBrightness.toFloat())
            .putBoolean("auto_center", value.autoCenterOnLaunch)
            .putString("tile_url", value.mapTileUrl)
            .putString("routing_provider", value.routingProvider.storageValue)
            .putString("graphhopper_server", value.graphHopperServer)
            .putString("search_engine", value.searchEngine.storageValue)
            .putBoolean("voice_enabled", value.voiceEnabled)
            .putString("voice_gender", value.voiceGender.storageValue)
            .putInt("voice_speed_stage", value.voiceSpeedStage)
            .putFloat("voice_volume", value.voiceVolume.toFloat())
            .putBoolean("profile_public", value.profilePublic)
            .putBoolean("avoid_unpaved", value.avoidUnpavedRoads)
            .putString("map_engine", value.mapEngine.storageValue)
            .putBoolean("show_altitude", value.showAltitude)
            .putBoolean("show_crosswalks", value.showCrosswalks)
            .putBoolean("show_traffic_lights", value.showTrafficLights)
            .putBoolean("favorites_sync_auto", value.favoritesSyncAutoEnabled)
            .putBoolean("imperial", value.imperialUnits)
            .putString("speedometer", value.speedometerStyle.storageValue)
            .putString("cursor_style", value.cursorStyle.storageValue)
            .putString("cursor_color", value.cursorColor.storageValue)
            .apply()
    }

    private companion object {
        const val NAME = "roadtest_ui_preferences"
    }
}
