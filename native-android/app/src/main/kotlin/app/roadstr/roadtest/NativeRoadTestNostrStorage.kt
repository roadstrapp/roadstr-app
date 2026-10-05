package app.roadstr.roadtest

import android.content.Context
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.FavoritesSyncProtocol
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.feature.home.NativeShellSyncSecrets
import app.roadstr.service.nostr.NativeActivityStore
import app.roadstr.service.nostr.NativeFavoritesSyncStore
import app.roadstr.service.nostr.NativePendingReportStorage

/**
 * Everything favourites sync keeps between launches. The passphrase and the
 * custom relay are protected like the other secrets; the three scalars are
 * not sensitive and stay in plain preferences.
 */
internal class NativeRoadTestSyncStorage(context: Context) : NativeFavoritesSyncStore, NativeShellSyncSecrets {
    private val secrets = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = "roadtest_sync_secrets",
        keyAlias = "app.roadstr.roadtest.sync.v1",
    )
    private val state = context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)

    override var lastCreatedAt: Long?
        get() = state.getLong(LAST_CREATED_AT, -1L).takeIf { it >= 0L }
        set(value) {
            state.edit().apply {
                if (value == null) remove(LAST_CREATED_AT) else putLong(LAST_CREATED_AT, value)
            }.apply()
        }

    override var legacyCleaned: Boolean
        get() = state.getBoolean(LEGACY_CLEANED, false)
        set(value) {
            state.edit().putBoolean(LEGACY_CLEANED, value).apply()
        }

    /** The passphrase itself, for the sync service; the UI only asks [passphraseConfigured]. */
    fun passphrase(): String? = runCatching { secrets.read(PASSPHRASE) }.getOrNull()?.takeIf(String::isNotEmpty)

    override fun passphraseConfigured(): Boolean = passphrase() != null

    override fun setPassphrase(value: String): Boolean =
        if (value.isEmpty()) secrets.remove(PASSPHRASE) else secrets.write(PASSPHRASE, value)

    override fun customRelay(): String? = runCatching { secrets.read(RELAY) }.getOrNull()
        ?.let(FavoritesSyncProtocol::normaliseRelayUrl)

    override fun setCustomRelay(value: String): Boolean {
        if (value.isBlank()) return secrets.remove(RELAY)
        val normalized = FavoritesSyncProtocol.normaliseRelayUrl(value) ?: return false
        return secrets.write(RELAY, normalized)
    }

    override fun lastSyncMillis(): Long? = state.getLong(LAST_SYNC, -1L).takeIf { it >= 0L }

    override fun markSynced(epochMillis: Long) {
        state.edit().putLong(LAST_SYNC, epochMillis).apply()
    }

    private companion object {
        const val STATE = "roadtest_sync_state"
        const val LAST_CREATED_AT = "last_created_at"
        const val LEGACY_CLEANED = "legacy_cleaned"
        const val LAST_SYNC = "last_sync_millis"
        const val PASSPHRASE = "passphrase"
        const val RELAY = "custom_relay"
    }
}

/**
 * Signed reports waiting for a relay. They carry the place the driver was at,
 * so they are kept encrypted like the saved places.
 */
internal class NativeRoadTestPendingReports(context: Context) : NativePendingReportStorage {
    private val storage = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = "roadtest_pending_reports",
        keyAlias = "app.roadstr.roadtest.pending.v1",
    )

    override fun read(): List<String> {
        val raw = runCatching { storage.read(KEY) }.getOrNull() ?: return emptyList()
        val rows = runCatching { BoundedJsonParser(raw).parse() as? List<*> }.getOrNull()
        return rows.orEmpty().filterIsInstance<String>()
    }

    override fun write(rows: List<String>) {
        if (rows.isEmpty()) {
            storage.remove(KEY)
            return
        }
        // The store holds at most 128 KiB: when the queue outgrows it, the
        // oldest reports go first (they are also the closest to expiring).
        var kept = rows
        var encoded = NostrJson.encode(kept)
        while (kept.size > 1 && encoded.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            kept = kept.drop(1)
            encoded = NostrJson.encode(kept)
        }
        storage.write(KEY, encoded)
    }

    private companion object {
        const val KEY = "rows"
        const val MAX_BYTES = 120 * 1024
    }
}

/**
 * The activity inbox says who zapped or confirmed which report, so it is kept
 * encrypted; the two cursors are only timestamps and stay in plain preferences.
 */
internal class NativeRoadTestActivityStore(context: Context) : NativeActivityStore {
    private val inbox = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = "roadtest_activity_inbox",
        keyAlias = "app.roadstr.roadtest.activity.v1",
    )
    private val cursors = context.applicationContext.getSharedPreferences("roadtest_activity_cursors", Context.MODE_PRIVATE)

    override fun readInbox(pubkey: String): String? = runCatching { inbox.read(inboxKey(pubkey)) }.getOrNull()

    override fun writeInbox(pubkey: String, normalized: String) {
        inbox.write(inboxKey(pubkey), normalized)
    }

    override fun readCursor(key: String): String? = cursors.getString(key, null)

    override fun writeCursor(key: String, value: String) {
        cursors.edit().putString(key, value).apply()
    }

    private fun inboxKey(pubkey: String) = "inbox_$pubkey"
}
