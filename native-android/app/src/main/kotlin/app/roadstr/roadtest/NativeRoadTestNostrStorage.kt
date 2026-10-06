package app.roadstr.roadtest

import android.content.Context
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.CustomRelayPolicy
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
internal class NativeRoadTestSyncStorage(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativeFavoritesSyncStore, NativeShellSyncSecrets {
    private val secrets = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("sync_secrets"),
        keyAlias = names.alias("sync"),
    )
    private val state = context.applicationContext.getSharedPreferences(names.prefs("sync_state"), Context.MODE_PRIVATE)

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
        ?.let(CustomRelayPolicy::normalise)

    override fun setCustomRelay(value: String): Boolean {
        if (value.isBlank()) return secrets.remove(RELAY)
        val normalized = CustomRelayPolicy.normalise(value) ?: return false
        return secrets.write(RELAY, normalized)
    }

    override fun lastSyncMillis(): Long? = state.getLong(LAST_SYNC, -1L).takeIf { it >= 0L }

    /** The three scalars, written together and returned only once they are on disk. */
    fun importState(lastCreatedAt: Long?, legacyCleaned: Boolean, lastSyncMillis: Long?): Boolean =
        state.edit().apply {
            if (lastCreatedAt == null) remove(LAST_CREATED_AT) else putLong(LAST_CREATED_AT, lastCreatedAt)
            putBoolean(LEGACY_CLEANED, legacyCleaned)
            if (lastSyncMillis == null) remove(LAST_SYNC) else putLong(LAST_SYNC, lastSyncMillis)
        }.commit()

    override fun markSynced(epochMillis: Long) {
        state.edit().putLong(LAST_SYNC, epochMillis).apply()
    }

    private companion object {
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
internal class NativeRoadTestPendingReports(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativePendingReportStorage {
    private val storage = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("pending_reports"),
        keyAlias = names.alias("pending"),
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
internal class NativeRoadTestActivityStore(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativeActivityStore {
    private val inbox = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("activity_inbox"),
        keyAlias = names.alias("activity"),
    )
    private val cursors = context.applicationContext.getSharedPreferences(names.prefs("activity_cursors"), Context.MODE_PRIVATE)

    override fun readInbox(pubkey: String): String? = runCatching { inbox.read(inboxKey(pubkey)) }.getOrNull()

    override fun writeInbox(pubkey: String, normalized: String) {
        inbox.write(inboxKey(pubkey), normalized)
    }

    /** Like [writeInbox], but says whether the value was stored. */
    fun importInbox(pubkey: String, normalized: String): Boolean = inbox.write(inboxKey(pubkey), normalized)

    fun importCursor(key: String, value: String): Boolean = cursors.edit().putString(key, value).commit()

    override fun readCursor(key: String): String? = cursors.getString(key, null)

    override fun writeCursor(key: String, value: String) {
        cursors.edit().putString(key, value).apply()
    }

    private fun inboxKey(pubkey: String) = "inbox_$pubkey"
}
