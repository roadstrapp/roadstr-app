package app.roadstr.roadtest

import android.content.Context
import android.content.SharedPreferences
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.service.meshcore.MeshCoreSavedDevice
import app.roadstr.service.meshcore.NativeMeshStorage
import app.roadstr.service.nostr.NativePendingReportStorage

/**
 * What the MeshCore support keeps. The radio's Bluetooth address identifies a device, so it is kept
 * encrypted under a Keystore key like the other personal data; the consent is only a flag.
 */
internal class NativeRoadTestMeshStorage(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativeMeshStorage {
    private val protected = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("mesh_device"),
        keyAlias = names.alias("meshdevice"),
    )
    private val flags: SharedPreferences =
        context.applicationContext.getSharedPreferences(names.prefs("mesh_state"), Context.MODE_PRIVATE)

    override fun device(): MeshCoreSavedDevice? {
        val raw = runCatching { protected.read(DEVICE) }.getOrNull() ?: return null
        val map = runCatching { BoundedJsonParser(raw).parse() as? Map<*, *> }.getOrNull() ?: return null
        val address = (map["address"] as? String)?.takeIf(ADDRESS::matches) ?: return null
        val name = (map["name"] as? String).orEmpty().filter { it.code >= 0x20 }.take(MAX_NAME).ifBlank { address }
        return MeshCoreSavedDevice(name, address)
    }

    override fun saveDevice(device: MeshCoreSavedDevice?) {
        if (device == null || !ADDRESS.matches(device.address)) {
            protected.remove(DEVICE)
            return
        }
        protected.write(DEVICE, NostrJson.encode(linkedMapOf("name" to device.name.take(MAX_NAME), "address" to device.address)))
    }

    override fun channelName(): String =
        flags.getString(CHANNEL, null)?.takeIf { it.startsWith("#") && it.length in 2..32 } ?: DEFAULT_CHANNEL

    override fun transmitConsent(): Boolean = flags.getBoolean(CONSENT, false)

    override fun setTransmitConsent(accepted: Boolean) {
        flags.edit().putBoolean(CONSENT, accepted).apply()
    }

    override fun localApiCursor(): String? = flags.getString(LOCAL_API_CURSOR, null)

    override fun setLocalApiCursor(receivedAt: String) {
        flags.edit().putString(LOCAL_API_CURSOR, receivedAt).apply()
    }

    private companion object {
        const val DEVICE = "device"
        const val CHANNEL = "channel_name"
        const val CONSENT = "transmit_consent"
        const val LOCAL_API_CURSOR = "local_api_received_at"
        const val DEFAULT_CHANNEL = "#roadstr"
        const val MAX_NAME = 64
        val ADDRESS = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    }
}

/**
 * Radio events waiting for a relay. They carry where somebody was, so they are kept encrypted like
 * the app's own pending reports, in their own store so that they can never crowd those out.
 */
internal class NativeRoadTestMeshRepublishQueue(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativePendingReportStorage {
    private val storage = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("mesh_republish"),
        keyAlias = names.alias("meshrepublish"),
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
        // The store is capped (128 KiB): when the queue outgrows it the oldest entries go first.
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

/** The radio-related part of the duplicate ledger: event IDs and two small masks, nothing about places. */
internal class NativeRoadTestMeshLedgerStorage(
    context: Context,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativePendingReportStorage {
    private val storage = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = names.prefs("mesh_ledger"),
        keyAlias = names.alias("meshledger"),
    )

    override fun read(): List<String> {
        val raw = runCatching { storage.read(KEY) }.getOrNull() ?: return emptyList()
        val rows = runCatching { BoundedJsonParser(raw).parse() as? List<*> }.getOrNull()
        return rows.orEmpty().filterIsInstance<String>()
    }

    override fun write(rows: List<String>) {
        if (rows.isEmpty()) storage.remove(KEY) else storage.write(KEY, NostrJson.encode(rows))
    }

    private companion object {
        const val KEY = "rows"
    }
}
