package app.roadstr.migration

/**
 * Names that form the on-device compatibility boundary with the Flutter app.
 *
 * This object deliberately contains names and formats only. It must not read
 * Android files or secrets itself: the legacy reader will be supplied either
 * by a deterministic native adapter or by the temporary headless Flutter
 * bridge described in STORAGE_COMPATIBILITY.md.
 */
object LegacyStorageContract {
    const val settingsBoxName = "settings"
    const val settingsFileName = "settings.hive"
    const val settingsMigrationBackupFileName = "settings.hive.migration-backup"
    const val settingsEncryptionKey = "hive_settings_key"

    const val modelDirectoryKokoro = "kokoro"
    const val modelDirectoryPiper = "piper"
    const val espeakDirectory = "espeak-ng-data"

    val secureKeys: Set<String> = setOf(
        settingsEncryptionKey,
        "nostr_priv_hex",
        "nostr_pub_hex",
        "nostr_flavor",
        "nostr_picture",
        "nostr_name",
        "routing_api_key",
        "nwc_uri",
        "favorites_sync_passphrase",
    )

    /**
     * Fixed Hive keys. Per-identity inbox and cursor keys are represented by
     * [dynamicKeyPrefixes] below and must not be silently dropped by readers.
     */
    val hiveKeys: Set<String> = setOf(
        "autoDark",
        "autoCenterOnLaunch",
        "avoidUnpavedRoads",
        "disclaimer_accepted",
        "fav_sync_custom_relay",
        "fav_sync_last_ts",
        "fav_sync_legacy_cleaned",
        "fav_sync_pass",
        "favorites",
        "favoritesSyncAutoEnabled",
        "favoritesSyncLastAt",
        "graphhopperApiKey",
        "graphhopperServer",
        "imperialUnits",
        "keepScreenOn",
        "keepScreenOnAlways",
        "kokoroSpeedStage",
        "kokoroVoiceGender",
        "kokoroVolume",
        "language",
        "mapEngine",
        "mapTileUrl",
        "minBrightness",
        "movementCursorColor",
        "movementCursorStyle",
        "nwcUri",
        "onboarding_v1",
        "parking_position",
        "pending_road_reports",
        "privacy_disclosure_v2",
        "road_report_privacy_ack",
        "roadstr_profile_public",
        "routingProvider",
        "searchEngine",
        "searchHistory",
        "showAltitude",
        "showCrosswalks",
        "showTrafficLights",
        "speedometerStyle",
        "themeId",
        "voiceEnabled",
        "voice_unsupported_notice_shown",
    )

    val dynamicKeyPrefixes: Set<String> = setOf(
        "activity_inbox_",
        "activity_zap_cursor_",
        "activity_confirmation_cursor_",
    )

    private val publicKeyHex = Regex("^[0-9a-fA-F]{64}$")

    fun isDynamicKey(key: String): Boolean = dynamicKeyPrefixes.any { prefix ->
        key.startsWith(prefix) && publicKeyHex.matches(key.removePrefix(prefix))
    }
}
