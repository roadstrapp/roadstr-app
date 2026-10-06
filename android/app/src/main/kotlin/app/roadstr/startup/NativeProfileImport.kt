package app.roadstr.startup

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.feature.activity.NativeActivityInboxProtocol
import app.roadstr.feature.profile.NativeProfileIdentityFlavor
import app.roadstr.feature.saved.NativeParkingPosition
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.migration.LegacyStorageContract
import app.roadstr.migration.LegacyStorageSnapshot
import app.roadstr.migration.MigrationMarker
import app.roadstr.migration.NativeSnapshotWriter
import app.roadstr.storage.NativePendingReportRows
import app.roadstr.storage.NativePreferenceRecord
import app.roadstr.storage.NativePreferenceSchema

/** Why a legacy snapshot could not be turned into a profile; never carries a value. */
class ProfileImportException(val part: String) : IllegalStateException("Profile import failed: $part")

data class ImportedIdentity(
    val publicKeyHex: String,
    val flavor: NativeProfileIdentityFlavor,
    val privateKeyHex: String?,
    val name: String?,
    val pictureUrl: String?,
) {
    // The generated toString would print the private key into any log line that interpolates this.
    override fun toString(): String = "ImportedIdentity(<redacted>)"
}

data class ImportedSync(
    val passphrase: String?,
    val customRelay: String?,
    val lastCreatedAt: Long?,
    val legacyCleaned: Boolean,
    val lastSyncMillis: Long?,
) {
    override fun toString(): String = "ImportedSync(<redacted>)"
}

/**
 * Everything the old app kept for the user, in the shapes the Kotlin app reads. Secrets are in
 * here, so the text form shows nothing.
 */
data class ImportedProfile(
    val settings: NativeSettingsInput,
    val onboardingCompleted: Boolean,
    val reportPrivacyAcknowledged: Boolean,
    val favorites: List<NativeSavedPlace>,
    val parking: NativeParkingPosition?,
    val identity: ImportedIdentity?,
    val nwcUri: String?,
    val routingApiKey: String?,
    val sync: ImportedSync,
    val pendingReports: List<String>,
    val activityInboxes: Map<String, String>,
    val activityCursors: Map<String, String>,
    val searchHistory: List<SearchHistoryEntry>,
) {
    override fun toString(): String = "ImportedProfile(<redacted>)"
}

/** Turns the bounded snapshot the legacy reader returns into an [ImportedProfile]. */
object NativeProfileMapper {
    private const val TRUE = "true"

    fun map(snapshot: LegacyStorageSnapshot): ImportedProfile {
        val ordinary = snapshot.ordinaryValues
        val secure = snapshot.secureValues
        val activity = activityValues(ordinary)
        return ImportedProfile(
            settings = settings(ordinary),
            // The gate is the privacy disclosure: a person who never accepted it sees it again.
            onboardingCompleted = ordinary[PRIVACY_DISCLOSURE] == TRUE,
            reportPrivacyAcknowledged = ordinary[REPORT_PRIVACY_ACK] == TRUE,
            favorites = guarded("favorites") { NativeSavedPlacesProtocol.decodeStoredFavorites(ordinary["favorites"]) },
            parking = guarded("parking") { NativeSavedPlacesProtocol.decodeParking(ordinary["parking_position"]) },
            identity = identity(snapshot),
            nwcUri = secure["nwc_uri"].orPlaintext(ordinary["nwcUri"]),
            routingApiKey = secure["routing_api_key"].orPlaintext(ordinary["graphhopperApiKey"]),
            sync = ImportedSync(
                passphrase = secure["favorites_sync_passphrase"].orPlaintext(ordinary["fav_sync_pass"]),
                customRelay = ordinary["fav_sync_custom_relay"]?.takeIf(String::isNotBlank),
                lastCreatedAt = ordinary["fav_sync_last_ts"]?.toLongOrNull()?.takeIf { it >= 0L },
                legacyCleaned = ordinary["fav_sync_legacy_cleaned"] == TRUE,
                lastSyncMillis = ordinary["favoritesSyncLastAt"]?.toLongOrNull()?.takeIf { it >= 0L },
            ),
            pendingReports = guarded("pending reports") { NativePendingReportRows.fromLegacy(ordinary["pending_road_reports"]) },
            activityInboxes = activity.first,
            activityCursors = activity.second,
            searchHistory = searchHistory(ordinary["searchHistory"]),
        )
    }

    /** History is the other lenient part: an unreadable one starts empty rather than blocking the update. */
    private fun searchHistory(raw: String?): List<SearchHistoryEntry> = try {
        raw?.let { SearchHistoryProtocol.decodeStored(BoundedJsonParser(it).parse()) }
            .orEmpty()
            .take(SearchHistoryProtocol.MAX_STORED_ITEMS)
    } catch (_: RuntimeException) {
        emptyList()
    }

    /** Settings are the one part that can fall back to defaults: a bad value must not lock anyone out. */
    private fun settings(ordinary: Map<String, String>): NativeSettingsInput = try {
        val typed = NativePreferenceSchema.importLegacy(ordinary)
        NativePreferenceSchema.projectSettings(
            NativePreferenceRecord(NativePreferenceRecord.CURRENT_SCHEMA_VERSION, 0, typed),
        )
    } catch (_: RuntimeException) {
        NativeSettingsInput()
    }

    private fun identity(snapshot: LegacyStorageSnapshot): ImportedIdentity? {
        val legacy = snapshot.identity
        val publicKey = legacy.publicKeyHex ?: return null
        val flavor = NativeProfileIdentityFlavor.fromWire(legacy.flavor) ?: throw ProfileImportException("identity")
        return ImportedIdentity(
            publicKeyHex = publicKey.lowercase(),
            flavor = flavor,
            privateKeyHex = legacy.privateKeyHex?.lowercase(),
            name = snapshot.secureValues["nostr_name"]?.takeIf(String::isNotBlank),
            pictureUrl = snapshot.secureValues["nostr_picture"]?.takeIf(String::isNotBlank),
        )
    }

    /** Inbox rows and the two cursors per account, under the names the old app used. */
    private fun activityValues(ordinary: Map<String, String>): Pair<Map<String, String>, Map<String, String>> {
        val inboxes = linkedMapOf<String, String>()
        val cursors = linkedMapOf<String, String>()
        for ((key, value) in ordinary) {
            if (!LegacyStorageContract.isDynamicKey(key)) continue
            if (key.startsWith(INBOX_PREFIX)) {
                // Re-encoded, so a damaged inbox becomes an empty one instead of being carried over as it was.
                val canonical = guarded("activity") {
                    NativeActivityInboxProtocol.encodeNormalized(NativeActivityInboxProtocol.decodeNormalized(value))
                }
                inboxes[key.removePrefix(INBOX_PREFIX).lowercase()] = canonical
            } else {
                if (value.toLongOrNull()?.let { it >= 0L } != true) throw ProfileImportException("activity")
                cursors[key] = value
            }
        }
        return inboxes to cursors
    }

    private fun String?.orPlaintext(plaintext: String?): String? =
        (this ?: plaintext)?.takeIf(String::isNotBlank)

    private inline fun <T> guarded(part: String, block: () -> T): T = try {
        block()
    } catch (_: RuntimeException) {
        throw ProfileImportException(part)
    }

    private const val PRIVACY_DISCLOSURE = "privacy_disclosure_v2"
    private const val REPORT_PRIVACY_ACK = "road_report_privacy_ack"
    private const val INBOX_PREFIX = "activity_inbox_"
}

/** Where an imported profile lands: the stores the Kotlin app reads while it runs. */
interface ProfileImportTargets {
    fun write(profile: ImportedProfile)

    /** Reads every part back and names the parts that differ; never a value. */
    fun mismatches(profile: ImportedProfile): List<String>
}

/**
 * The transactional migration's writer over the live stores: [stage] maps and validates,
 * [commit] writes, [verify] reads back. The legacy files are never touched.
 */
class LiveProfileSnapshotWriter(private val targets: ProfileImportTargets) : NativeSnapshotWriter {
    private var staged: ImportedProfile? = null

    override fun stage(snapshot: LegacyStorageSnapshot) {
        staged = NativeProfileMapper.map(snapshot)
    }

    override fun commit() {
        val profile = checkNotNull(staged) { "Nothing was staged" }
        targets.write(profile)
    }

    override fun verify(snapshot: LegacyStorageSnapshot) {
        val profile = checkNotNull(staged) { "Nothing was staged" }
        val different = targets.mismatches(profile)
        if (different.isNotEmpty()) throw ProfileImportException(different.joinToString())
    }
}

/** Completion is recorded last, so a run that stopped half way is repeated rather than trusted. */
interface ProfileImportFlag {
    fun isSet(): Boolean

    fun set(): Boolean
}

class LiveMigrationMarker(private val flag: ProfileImportFlag) : MigrationMarker {
    override fun isComplete(): Boolean = flag.isSet()

    override fun markComplete() {
        check(flag.set()) { "The completion marker could not be written" }
    }
}
