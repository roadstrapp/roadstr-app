package app.roadstr.startup

import android.content.Context
import app.roadstr.core.protocol.nostr.CustomRelayPolicy
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.roadtest.NativeLiveStoreNames
import app.roadstr.roadtest.NativeRoadTestActivityStore
import app.roadstr.roadtest.NativeRoadTestFavoritesStore
import app.roadstr.roadtest.NativeRoadTestIdentityGateway
import app.roadstr.roadtest.NativeRoadTestPendingReports
import app.roadstr.roadtest.NativeRoadTestProtectedPreferences
import app.roadstr.roadtest.NativeRoadTestSearchHistoryStore
import app.roadstr.roadtest.NativeRoadTestSyncStorage
import app.roadstr.roadtest.NativeRoadTestUiPreferences

/**
 * The live stores of the Kotlin app, written from an imported profile and read back to prove it.
 * Failure messages name the part that failed and never a value.
 */
internal class AndroidProfileImportTargets(
    context: Context,
    names: NativeLiveStoreNames,
) : ProfileImportTargets {
    private val identity = NativeRoadTestIdentityGateway(context, names)
    private val ui = NativeRoadTestUiPreferences(context, names)
    private val onboarding = context.applicationContext.getSharedPreferences(names.prefs("onboarding"), Context.MODE_PRIVATE)
    private val favorites = NativeRoadTestFavoritesStore(context, names)
    private val parking = protectedStore(context, names, "parking", "parking")
    private val nwc = protectedStore(context, names, "nwc", "nwc")
    private val routing = protectedStore(context, names, "routing", "routing")
    private val sync = NativeRoadTestSyncStorage(context, names)
    private val pending = NativeRoadTestPendingReports(context, names)
    private val activity = NativeRoadTestActivityStore(context, names)
    private val history = NativeRoadTestSearchHistoryStore(context, names)

    override fun write(profile: ImportedProfile) {
        check(ui.saveNow(profile.settings)) { "settings" }
        val flags = onboarding.edit()
            .putBoolean(ONBOARDING_COMPLETED, profile.onboardingCompleted)
            .putBoolean(REPORT_PRIVACY_ACK, profile.reportPrivacyAcknowledged)
        check(flags.commit()) { "onboarding" }
        check(favorites.save(profile.favorites)) { "favorites" }
        writeSecretOrRemove(parking, PARKING_KEY, profile.parking?.let(NativeSavedPlacesProtocol::encodeParking), "parking")
        writeSecretOrRemove(nwc, NWC_KEY, profile.nwcUri, "nwc")
        writeSecretOrRemove(routing, ROUTING_KEY, profile.routingApiKey, "routing")
        writeSync(profile.sync)
        pending.write(profile.pendingReports)
        writeActivity(profile)
        check(history.write(profile.searchHistory)) { "history" }
        writeIdentity(profile.identity)
        if (profile.loginDropped) identity.noteLoginReset()
    }

    override fun mismatches(profile: ImportedProfile): List<String> = buildList {
        if (fingerprint(ui.load()) != fingerprint(profile.settings)) add("settings")
        if (onboarding.getBoolean(ONBOARDING_COMPLETED, false) != profile.onboardingCompleted ||
            onboarding.getBoolean(REPORT_PRIVACY_ACK, false) != profile.reportPrivacyAcknowledged
        ) {
            add("onboarding")
        }
        if (favorites.load() != profile.favorites) add("favorites")
        if (!parkingMatches(profile)) add("parking")
        if (read(nwc, NWC_KEY) != profile.nwcUri) add("nwc")
        if (read(routing, ROUTING_KEY) != profile.routingApiKey) add("routing")
        if (!syncMatches(profile.sync)) add("sync")
        if (!pendingMatches(profile.pendingReports)) add("pending reports")
        if (!activityMatches(profile)) add("activity")
        if (runCatching(history::read).getOrNull() != profile.searchHistory) add("history")
        if (!identityMatches(profile.identity)) add("identity")
    }

    private fun writeIdentity(imported: ImportedIdentity?) {
        if (imported == null) return
        val stored = identity.importIdentity(
            imported.publicKeyHex,
            imported.flavor,
            imported.name,
            imported.pictureUrl,
        )
        check(stored) { "identity" }
    }

    private fun identityMatches(imported: ImportedIdentity?): Boolean =
        imported == null || identity.holds(imported.publicKeyHex, imported.flavor)

    private fun writeSync(value: ImportedSync) {
        check(sync.setPassphrase(value.passphrase.orEmpty())) { "sync" }
        // A relay the policy refuses is dropped rather than carried over: it could not have been saved.
        val relay = value.customRelay?.let(CustomRelayPolicy::normalise)
        check(sync.setCustomRelay(relay.orEmpty())) { "sync" }
        check(sync.importState(value.lastCreatedAt, value.legacyCleaned, value.lastSyncMillis)) { "sync" }
    }

    private fun syncMatches(value: ImportedSync): Boolean =
        sync.passphrase() == value.passphrase &&
            sync.customRelay() == value.customRelay?.let(CustomRelayPolicy::normalise) &&
            sync.lastCreatedAt == value.lastCreatedAt &&
            sync.legacyCleaned == value.legacyCleaned &&
            sync.lastSyncMillis() == value.lastSyncMillis

    private fun writeActivity(profile: ImportedProfile) {
        for ((pubkey, inbox) in profile.activityInboxes) {
            check(activity.importInbox(pubkey, inbox)) { "activity" }
        }
        for ((key, cursor) in profile.activityCursors) {
            check(activity.importCursor(key, cursor)) { "activity" }
        }
    }

    private fun activityMatches(profile: ImportedProfile): Boolean =
        profile.activityInboxes.all { (pubkey, inbox) -> activity.readInbox(pubkey) == inbox } &&
            profile.activityCursors.all { (key, cursor) -> activity.readCursor(key) == cursor }

    private fun parkingMatches(profile: ImportedProfile): Boolean {
        val stored = runCatching { read(parking, PARKING_KEY)?.let(NativeSavedPlacesProtocol::decodeParking) }
        return stored.isSuccess && stored.getOrNull() == profile.parking
    }

    /** The queue keeps the newest rows when it outgrows its size limit, so the stored ones are a tail. */
    private fun pendingMatches(expected: List<String>): Boolean {
        val stored = pending.read()
        if (expected.isNotEmpty() && stored.isEmpty()) return false
        return stored == expected.takeLast(stored.size)
    }

    private fun writeSecretOrRemove(
        store: NativeRoadTestProtectedPreferences,
        key: String,
        value: String?,
        part: String,
    ) {
        val done = if (value == null) store.remove(key) else store.write(key, value)
        check(done) { part }
    }

    private fun read(store: NativeRoadTestProtectedPreferences, key: String): String? =
        runCatching { store.read(key) }.getOrNull()

    /** Only the values `NativeRoadTestUiPreferences.save` writes; the rest are computed while the app runs. */
    private fun fingerprint(value: NativeSettingsInput): List<Any?> = listOf(
        value.themeId, value.autoDarkEnabled, value.darkMapEnabled, value.languageCode, value.keepScreenOn,
        value.keepScreenOnAlways, value.minimumBrightness.toFloat(), value.autoCenterOnLaunch, value.mapTileUrl,
        value.routingProvider, value.graphHopperServer, value.searchEngine, value.voiceEnabled, value.voiceGender,
        value.voiceSpeedStage, value.voiceVolume.toFloat(), value.profilePublic, value.avoidUnpavedRoads,
        value.mapEngine, value.showAltitude, value.showCrosswalks, value.showTrafficLights,
        value.favoritesSyncAutoEnabled, value.imperialUnits, value.speedometerStyle, value.cursorStyle,
        value.cursorColor,
    )

    private fun protectedStore(
        context: Context,
        names: NativeLiveStoreNames,
        prefs: String,
        alias: String,
    ) = NativeRoadTestProtectedPreferences(context, names.prefs(prefs), names.alias(alias))

    private companion object {
        const val ONBOARDING_COMPLETED = "completed"
        const val REPORT_PRIVACY_ACK = "report_privacy_ack"
        const val PARKING_KEY = "parking"
        const val NWC_KEY = "uri"
        const val ROUTING_KEY = "api_key"
    }
}
