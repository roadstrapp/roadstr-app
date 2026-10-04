package app.roadstr.roadtest

import android.content.Context
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.navigation.NativeSpeedometerStyle
import app.roadstr.feature.settings.NativeSettingsCursorColor
import app.roadstr.feature.settings.NativeSettingsCursorStyle
import app.roadstr.feature.settings.NativeSettingsInput

/** Small scalar bridge for the standalone harness; secrets never enter it. */
class NativeRoadTestUiPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): NativeSettingsInput = NativeSettingsInput(
        themeId = RoadstrThemeId.fromStoredOrdinal(preferences.getInt("theme", 0)),
        autoDarkEnabled = preferences.getBoolean("auto_dark", false),
        darkMapEnabled = preferences.getBoolean("dark_map", false),
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
