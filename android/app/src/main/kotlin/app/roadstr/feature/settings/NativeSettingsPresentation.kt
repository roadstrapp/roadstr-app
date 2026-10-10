package app.roadstr.feature.settings

import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.navigation.NativeSpeedometerStyle
import java.net.URI
import java.util.Collections
import java.util.Locale
import kotlin.math.round
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeSettingsStatus { Hidden, Ready }

enum class NativeSettingsMapEngine(val storageValue: String) {
    MapLibre("maplibre"),
    Osm("osm");

    companion object {
        fun fromStorage(value: Any?): NativeSettingsMapEngine = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: MapLibre
    }
}

enum class NativeSettingsRoutingProvider(val storageValue: String) {
    Osrm("osrm"),
    GraphHopperSelfHosted("graphhopper"),
    GraphHopperCloud("graphhopper_public"),
    OpenRouteService("openroute");

    companion object {
        fun fromStorage(value: Any?): NativeSettingsRoutingProvider = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: Osrm
    }
}

enum class NativeSettingsSearchEngine(val storageValue: String, val displayName: String) {
    Qwant("qwant", "Qwant"),
    Brave("brave", "Brave"),
    DuckDuckGo("ddg", "DuckDuckGo"),
    Startpage("startpage", "Startpage"),
    Google("google", "Google");

    /** The results page of this engine for [query], as Flutter's place panel builds it. */
    fun searchUrl(query: String): String {
        val q = java.net.URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
        return when (this) {
            Qwant -> "https://www.qwant.com/?q=$q"
            Brave -> "https://search.brave.com/search?q=$q"
            DuckDuckGo -> "https://duckduckgo.com/?q=$q"
            Startpage -> "https://www.startpage.com/search?query=$q"
            Google -> "https://www.google.com/search?q=$q"
        }
    }

    companion object {
        fun fromStorage(value: Any?): NativeSettingsSearchEngine = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: Qwant
    }
}

enum class NativeSettingsCursorStyle(val storageValue: String) {
    Arrow("arrow"),
    Formula1("formula1"),
    Suv("suv"),
    Racing("racing"),
    Electric("electric"),
    City("city"),
    Classic500("classic500");

    companion object {
        fun fromStorage(value: Any?): NativeSettingsCursorStyle = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: Arrow
    }
}

enum class NativeSettingsCursorColor(val storageValue: String) {
    Violet("violet"),
    Indigo("indigo"),
    Blue("blue"),
    Green("green"),
    Yellow("yellow"),
    Orange("orange"),
    Red("red");

    companion object {
        fun fromStorage(value: Any?): NativeSettingsCursorColor = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: Violet
    }
}

enum class NativeSettingsVoiceModelStatus { Unknown, NotDownloaded, Downloading, Ready }

enum class NativeSettingsVoiceGender(val storageValue: String) {
    Female("f"),
    Male("m");

    companion object {
        fun fromStorage(value: Any?): NativeSettingsVoiceGender = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: Male
    }
}

enum class NativeSettingsBooleanKey(
    val storageKey: String,
    val defaultValue: Boolean,
) {
    AutoDark("autoDark", false),
    DarkMap("darkMapEnabled", false),
    ProfilePublic("roadstr_profile_public", false),
    AvoidUnpavedRoads("avoidUnpavedRoads", false),
    KeepScreenOn("keepScreenOn", true),
    KeepScreenOnAlways("keepScreenOnAlways", false),
    ShowAltitude("showAltitude", false),
    ShowCrosswalks("showCrosswalks", false),
    ShowTrafficLights("showTrafficLights", false),
    AutoCenterOnLaunch("autoCenterOnLaunch", true),
    ImperialUnits("imperialUnits", false),
    FavoritesSyncAuto("favoritesSyncAutoEnabled", false),
    VoiceEnabled("voiceEnabled", true),
    OfflineRouting("offlineRoutingEnabled", false),
    OfflineOnlineFallback("offlineOnlineFallbackAllowed", false),
}

sealed interface NativeSettingsStoredValue {
    data class BooleanValue(val value: Boolean) : NativeSettingsStoredValue
    data class IntegerValue(val value: Int) : NativeSettingsStoredValue
    data class LongValue(val value: Long) : NativeSettingsStoredValue
    data class DoubleValue(val value: Double) : NativeSettingsStoredValue
    data class StringValue(val value: String) : NativeSettingsStoredValue
}

data class NativeSettingsWrite(
    val revision: Long,
    val storageKey: String,
    val value: NativeSettingsStoredValue,
)

/** One saved place as the settings list shows it; the list is cleared while the screen is hidden. */
data class NativeSettingsFavorite(
    val label: String,
    val address: String,
)

data class NativeSettingsInput(
    val themeId: RoadstrThemeId = RoadstrThemeId.LightNostr,
    val autoDarkEnabled: Boolean = false,
    val darkMapEnabled: Boolean = false,
    val languageCode: String? = null,
    val profilePublic: Boolean = false,
    val avoidUnpavedRoads: Boolean = false,
    val mapEngine: NativeSettingsMapEngine = NativeSettingsMapEngine.MapLibre,
    val keepScreenOn: Boolean = true,
    val keepScreenOnAlways: Boolean = false,
    val minimumBrightness: Double = 0.0,
    val showAltitude: Boolean = false,
    val showCrosswalks: Boolean = false,
    val showTrafficLights: Boolean = false,
    val autoCenterOnLaunch: Boolean = true,
    val imperialUnits: Boolean = false,
    val mapTileUrl: String = DEFAULT_TILE_URL,
    val routingProvider: NativeSettingsRoutingProvider = NativeSettingsRoutingProvider.Osrm,
    val offlineRoutingEnabled: Boolean = false,
    val offlineOnlineFallbackAllowed: Boolean = false,
    val graphHopperServer: String = "",
    val routingApiKeyConfigured: Boolean = false,
    val speedometerStyle: NativeSpeedometerStyle = NativeSpeedometerStyle.Classic,
    val cursorStyle: NativeSettingsCursorStyle = NativeSettingsCursorStyle.Arrow,
    val cursorColor: NativeSettingsCursorColor = NativeSettingsCursorColor.Violet,
    val searchEngine: NativeSettingsSearchEngine = NativeSettingsSearchEngine.Qwant,
    val nwcConfigured: Boolean = false,
    val favoritesCount: Int = 0,
    val favoritePlaces: List<NativeSettingsFavorite> = emptyList(),
    val favoritesSyncAutoEnabled: Boolean = false,
    val syncIdentityAvailable: Boolean = false,
    val syncBusy: Boolean = false,
    val syncPassphraseConfigured: Boolean = false,
    val customSyncRelay: String? = null,
    val lastSyncMillis: Long? = null,
    val voiceEnabled: Boolean = true,
    val voiceModelStatus: NativeSettingsVoiceModelStatus = NativeSettingsVoiceModelStatus.Unknown,
    val voiceDownloadProgress: Double = 0.0,
    val voiceGender: NativeSettingsVoiceGender = NativeSettingsVoiceGender.Male,
    val voiceGenderChoiceAvailable: Boolean = true,
    val voiceSpeedStage: Int = DEFAULT_VOICE_SPEED_STAGE,
    val voiceVolume: Double = 1.0,
    val voicePreviewing: Boolean = false,
    val appVersion: String = "—",
) {
    companion object {
        const val DEFAULT_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val DEFAULT_VOICE_SPEED_STAGE = 4
    }
}

data class NativeSettingsSnapshot(
    val revision: Long,
    val status: NativeSettingsStatus,
    val values: NativeSettingsInput,
) {
    companion object {
        const val NO_REVISION = -1L

        fun hidden(revision: Long = NO_REVISION) = NativeSettingsSnapshot(
            revision = revision,
            status = NativeSettingsStatus.Hidden,
            values = NativeSettingsInput(),
        )
    }
}

/** Pure bounded projection of the scalar SettingsScreen state and safe summaries. */
object NativeSettingsPresenter {
    const val MAX_URL_CHARS = 2_048
    const val MAX_VERSION_CHARS = 64
    const val MAX_FAVORITES = 1_000
    const val MAX_SYNC_MILLIS = 253_402_300_799_999L
    val SUPPORTED_LANGUAGE_CODES: Set<String> = Collections.unmodifiableSet(
        setOf(
            "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "ga",
            "hr", "hu", "it", "ja", "lt", "lv", "mt", "nl", "pl", "pt", "ro",
            "ru", "sk", "sl", "sv", "zh",
        ),
    )
    val VOICE_SPEED_STAGES: List<Double> = Collections.unmodifiableList(
        listOf(0.7, 0.85, 1.0, 1.15, 1.3, 1.5),
    )
    private val CONTROLS = Regex("[\\u0000-\\u001f]")

    fun present(revision: Long, input: NativeSettingsInput): NativeSettingsSnapshot {
        require(revision >= 0) { "Settings revision must be non-negative" }
        val language = input.languageCode?.trim()?.lowercase(Locale.ROOT)
        require(language == null || language in SUPPORTED_LANGUAGE_CODES) {
            "Unsupported settings language"
        }
        require(input.minimumBrightness.isFinite() && input.minimumBrightness in 0.0..1.0) {
            "Minimum brightness must be between zero and one"
        }
        require(input.favoritesCount in 0..MAX_FAVORITES) { "Invalid favorite count" }
        require(input.favoritePlaces.size <= MAX_FAVORITES) { "Invalid favorite list" }
        input.lastSyncMillis?.let {
            require(it in 0..MAX_SYNC_MILLIS) { "Invalid last-sync timestamp" }
        }
        require(input.voiceDownloadProgress.isFinite() && input.voiceDownloadProgress in 0.0..1.0) {
            "Voice progress must be between zero and one"
        }
        require(input.voiceSpeedStage in VOICE_SPEED_STAGES.indices) { "Invalid voice speed stage" }
        require(input.voiceVolume.isFinite() && input.voiceVolume in 0.2..1.0) {
            "Voice volume must be between 0.2 and one"
        }
        val progress = when (input.voiceModelStatus) {
            NativeSettingsVoiceModelStatus.Ready -> 1.0
            NativeSettingsVoiceModelStatus.NotDownloaded,
            NativeSettingsVoiceModelStatus.Unknown,
            -> 0.0

            NativeSettingsVoiceModelStatus.Downloading -> input.voiceDownloadProgress
        }
        return NativeSettingsSnapshot(
            revision = revision,
            status = NativeSettingsStatus.Ready,
            values = input.copy(
                languageCode = language,
                minimumBrightness = tenth(input.minimumBrightness),
                mapTileUrl = cleanRequired(input.mapTileUrl, MAX_URL_CHARS, "Map tile URL"),
                graphHopperServer = clean(input.graphHopperServer, MAX_URL_CHARS) ?: "",
                customSyncRelay = safeRelay(input.customSyncRelay),
                voiceDownloadProgress = progress,
                voiceVolume = tenth(input.voiceVolume),
                appVersion = cleanRequired(input.appVersion, MAX_VERSION_CHARS, "App version"),
            ),
        )
    }

    fun voiceSpeed(stage: Int): Double {
        require(stage in VOICE_SPEED_STAGES.indices) { "Invalid voice speed stage" }
        return VOICE_SPEED_STAGES[stage]
    }

    private fun safeRelay(value: String?): String? {
        val normalized = clean(value, MAX_URL_CHARS) ?: return null
        return try {
            URI(normalized).takeIf { uri ->
                uri.scheme.equals("wss", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    uri.fragment == null
            }?.toASCIIString()
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanRequired(value: String, limit: Int, field: String): String =
        requireNotNull(clean(value, limit)) { "$field must not be empty" }

    private fun clean(value: String?, limit: Int): String? = value
        ?.replace(CONTROLS, " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(limit)

    private fun tenth(value: Double): Double = round(value * 10.0) / 10.0
}

/**
 * Revision-safe coordinator for a dormant settings surface.
 *
 * Every mutation returns a typed write for an external persistence owner. It
 * never reads Hive/DataStore/Keystore and never receives secret contents.
 */
class NativeSettingsSession(
    initialValues: NativeSettingsInput = NativeSettingsInput(),
) {
    private val lock = Any()
    private val _state = MutableStateFlow(
        NativeSettingsSnapshot.hidden().copy(values = initialValues),
    )
    private var revision = NativeSettingsSnapshot.NO_REVISION
    private var retainedValues = initialValues

    val state: StateFlow<NativeSettingsSnapshot> = _state.asStateFlow()

    fun show(revision: Long, input: NativeSettingsInput): Boolean = synchronized(lock) {
        require(revision >= 0) { "Settings revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        val presented = NativeSettingsPresenter.present(revision, input)
        retainedValues = presented.values
        _state.value = presented
        true
    }

    /** Reopens the panel without rehydrating redacted summaries from hidden UI state. */
    fun reopen(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Settings revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        val presented = NativeSettingsPresenter.present(revision, retainedValues)
        retainedValues = presented.values
        _state.value = presented
        true
    }

    fun refresh(revision: Long, input: NativeSettingsInput): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status != NativeSettingsStatus.Ready) return false
        val presented = NativeSettingsPresenter.present(revision, input)
        retainedValues = presented.values
        _state.value = presented
        true
    }

    fun updateBoolean(
        revision: Long,
        key: NativeSettingsBooleanKey,
        value: Boolean,
    ): NativeSettingsWrite? = mutate(revision) { current ->
        val updated = when (key) {
            NativeSettingsBooleanKey.AutoDark -> current.copy(autoDarkEnabled = value)
            NativeSettingsBooleanKey.DarkMap -> current.copy(darkMapEnabled = value)
            NativeSettingsBooleanKey.ProfilePublic -> current.copy(profilePublic = value)
            NativeSettingsBooleanKey.AvoidUnpavedRoads -> current.copy(avoidUnpavedRoads = value)
            NativeSettingsBooleanKey.KeepScreenOn -> current.copy(keepScreenOn = value)
            NativeSettingsBooleanKey.KeepScreenOnAlways -> current.copy(keepScreenOnAlways = value)
            NativeSettingsBooleanKey.ShowAltitude -> current.copy(showAltitude = value)
            NativeSettingsBooleanKey.ShowCrosswalks -> current.copy(showCrosswalks = value)
            NativeSettingsBooleanKey.ShowTrafficLights -> current.copy(showTrafficLights = value)
            NativeSettingsBooleanKey.AutoCenterOnLaunch -> current.copy(autoCenterOnLaunch = value)
            NativeSettingsBooleanKey.ImperialUnits -> current.copy(imperialUnits = value)
            NativeSettingsBooleanKey.FavoritesSyncAuto -> current.copy(favoritesSyncAutoEnabled = value)
            NativeSettingsBooleanKey.VoiceEnabled -> current.copy(voiceEnabled = value)
            NativeSettingsBooleanKey.OfflineRouting -> current.copy(offlineRoutingEnabled = value)
            NativeSettingsBooleanKey.OfflineOnlineFallback -> current.copy(offlineOnlineFallbackAllowed = value)
        }
        updated to NativeSettingsWrite(
            revision,
            key.storageKey,
            NativeSettingsStoredValue.BooleanValue(value),
        )
    }

    fun updateTheme(revision: Long, value: RoadstrThemeId): NativeSettingsWrite? =
        mutate(revision) { current ->
            current.copy(themeId = value) to NativeSettingsWrite(
                revision,
                "themeId",
                NativeSettingsStoredValue.IntegerValue(value.storedOrdinal),
            )
        }

    fun updateLanguage(revision: Long, value: String?): NativeSettingsWrite? =
        mutate(revision) { current ->
            val normalized = value?.trim()?.lowercase(Locale.ROOT)
            require(normalized == null || normalized in NativeSettingsPresenter.SUPPORTED_LANGUAGE_CODES) {
                "Unsupported settings language"
            }
            current.copy(languageCode = normalized) to NativeSettingsWrite(
                revision,
                "language",
                NativeSettingsStoredValue.StringValue(normalized ?: ""),
            )
        }

    fun updateMapEngine(revision: Long, value: NativeSettingsMapEngine): NativeSettingsWrite? =
        stringMutation(revision, "mapEngine", value.storageValue) { copy(mapEngine = value) }

    fun updateRoutingProvider(
        revision: Long,
        value: NativeSettingsRoutingProvider,
    ): NativeSettingsWrite? = stringMutation(revision, "routingProvider", value.storageValue) {
        copy(routingProvider = value)
    }

    fun updateSpeedometer(
        revision: Long,
        value: NativeSpeedometerStyle,
    ): NativeSettingsWrite? = stringMutation(revision, "speedometerStyle", value.storageValue) {
        copy(speedometerStyle = value)
    }

    fun updateCursorStyle(
        revision: Long,
        value: NativeSettingsCursorStyle,
    ): NativeSettingsWrite? = stringMutation(revision, "movementCursorStyle", value.storageValue) {
        copy(cursorStyle = value)
    }

    fun updateCursorColor(
        revision: Long,
        value: NativeSettingsCursorColor,
    ): NativeSettingsWrite? = stringMutation(revision, "movementCursorColor", value.storageValue) {
        copy(cursorColor = value)
    }

    fun updateSearchEngine(
        revision: Long,
        value: NativeSettingsSearchEngine,
    ): NativeSettingsWrite? = stringMutation(revision, "searchEngine", value.storageValue) {
        copy(searchEngine = value)
    }

    fun updateBrightness(revision: Long, value: Double): NativeSettingsWrite? =
        doubleMutation(revision, "minBrightness", value, 0.0..1.0) {
            copy(minimumBrightness = it)
        }

    fun updateVoiceVolume(revision: Long, value: Double): NativeSettingsWrite? =
        doubleMutation(revision, "kokoroVolume", value, 0.2..1.0) {
            copy(voiceVolume = it)
        }

    fun updateVoiceSpeedStage(revision: Long, stage: Int): NativeSettingsWrite? =
        mutate(revision) { current ->
            require(stage in NativeSettingsPresenter.VOICE_SPEED_STAGES.indices) {
                "Invalid voice speed stage"
            }
            current.copy(voiceSpeedStage = stage) to NativeSettingsWrite(
                revision,
                "kokoroSpeedStage",
                NativeSettingsStoredValue.IntegerValue(stage),
            )
        }

    fun updateVoiceGender(
        revision: Long,
        value: NativeSettingsVoiceGender,
    ): NativeSettingsWrite? = stringMutation(revision, "kokoroVoiceGender", value.storageValue) {
        copy(voiceGender = value)
    }

    fun updateMapTileUrl(revision: Long, value: String): NativeSettingsWrite? =
        textMutation(revision, "mapTileUrl", value, NativeSettingsPresenter.MAX_URL_CHARS) {
            copy(mapTileUrl = it)
        }

    fun updateGraphHopperServer(revision: Long, value: String): NativeSettingsWrite? =
        textMutation(
            revision,
            "graphhopperServer",
            value,
            NativeSettingsPresenter.MAX_URL_CHARS,
            allowEmpty = true,
        ) { copy(graphHopperServer = it) }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        if (revision != this.revision || _state.value.status == NativeSettingsStatus.Hidden) return false
        retainedValues = _state.value.values
        // Operational preferences remain projected while hidden because the
        // map, theme and voice runtime consume them. Account/key-presence and
        // sync summaries do not: clear those from observable UI state while
        // retaining them privately for the next explicit reopen.
        _state.value = NativeSettingsSnapshot.hidden(revision).copy(
            values = retainedValues.copy(
                routingApiKeyConfigured = false,
                nwcConfigured = false,
                favoritesCount = 0,
                favoritePlaces = emptyList(),
                syncIdentityAvailable = false,
                syncBusy = false,
                syncPassphraseConfigured = false,
                customSyncRelay = null,
                lastSyncMillis = null,
                voicePreviewing = false,
            ),
        )
        true
    }

    private fun stringMutation(
        revision: Long,
        key: String,
        value: String,
        transform: NativeSettingsInput.() -> NativeSettingsInput,
    ): NativeSettingsWrite? = mutate(revision) { current ->
        current.transform() to NativeSettingsWrite(
            revision,
            key,
            NativeSettingsStoredValue.StringValue(value),
        )
    }

    private fun doubleMutation(
        revision: Long,
        key: String,
        value: Double,
        range: ClosedFloatingPointRange<Double>,
        transform: NativeSettingsInput.(Double) -> NativeSettingsInput,
    ): NativeSettingsWrite? = mutate(revision) { current ->
        require(value.isFinite() && value in range) { "Invalid numeric setting" }
        val normalized = round(value * 10.0) / 10.0
        current.transform(normalized) to NativeSettingsWrite(
            revision,
            key,
            NativeSettingsStoredValue.DoubleValue(normalized),
        )
    }

    private fun textMutation(
        revision: Long,
        key: String,
        value: String,
        limit: Int,
        allowEmpty: Boolean = false,
        transform: NativeSettingsInput.(String) -> NativeSettingsInput,
    ): NativeSettingsWrite? = mutate(revision) { current ->
        val normalized = value.replace(Regex("[\\u0000-\\u001f]"), " ").trim().take(limit)
        require(allowEmpty || normalized.isNotEmpty()) { "Setting value must not be empty" }
        current.transform(normalized) to NativeSettingsWrite(
            revision,
            key,
            NativeSettingsStoredValue.StringValue(normalized),
        )
    }

    private fun mutate(
        revision: Long,
        block: (NativeSettingsInput) -> Pair<NativeSettingsInput, NativeSettingsWrite>,
    ): NativeSettingsWrite? = synchronized(lock) {
        val current = _state.value
        if (revision != this.revision || current.status != NativeSettingsStatus.Ready) return null
        val (values, write) = block(current.values)
        val presented = NativeSettingsPresenter.present(revision, values)
        retainedValues = presented.values
        _state.value = presented
        write
    }
}
