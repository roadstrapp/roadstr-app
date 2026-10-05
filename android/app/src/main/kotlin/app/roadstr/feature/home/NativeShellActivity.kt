package app.roadstr.feature.home

import app.roadstr.feature.activity.NativeActivityInboxProtocol
import app.roadstr.feature.activity.NativeActivityInboxWrite
import app.roadstr.feature.activity.NativeActivityNotification

/**
 * Keeps the silent activity inbox up to date: loads it for the signed-in
 * account, polls the relays for zaps and votes on the account's own reports,
 * records what is new and tells the shell the unread count.
 *
 * It is also the only writer of the inbox, so what the inbox panel shows and
 * what the badge counts can never disagree.
 */
class NativeShellActivityController(
    private val nostr: NativeShellNostr,
    private val onChanged: (unread: Int) -> Unit,
) {
    private var items: List<NativeActivityNotification> = emptyList()
    private var loadedFor: String? = null

    /** Reads the stored inbox of [pubkey], or clears it when nobody is signed in. */
    fun load(pubkey: String?) {
        loadedFor = pubkey
        items = if (pubkey == null) {
            emptyList()
        } else {
            NativeActivityInboxProtocol.decodeNormalized(nostr.activityStore?.readInbox(pubkey))
        }
        onChanged(NativeActivityInboxProtocol.unreadCount(items))
    }

    /** One check of the relays; safe to call whenever, and a no-op when signed out. */
    suspend fun poll() {
        val pubkey = nostr.signer.pubkeyHex ?: return
        val service = nostr.activity ?: return
        if (loadedFor != pubkey) load(pubkey)
        var recorded = false
        for (notification in service.poll(pubkey)) {
            val write = NativeActivityInboxProtocol.record(pubkey, items, notification) ?: continue
            nostr.activityStore?.writeInbox(pubkey, write.normalizedValue)
            items = write.items
            recorded = true
        }
        if (recorded) onChanged(NativeActivityInboxProtocol.unreadCount(items))
    }

    /** What the inbox panel starts from. */
    fun stored(pubkey: String): String? = nostr.activityStore?.readInbox(pubkey)

    /** The inbox panel marked everything read. */
    fun persist(write: NativeActivityInboxWrite) {
        val pubkey = nostr.signer.pubkeyHex ?: return
        nostr.activityStore?.writeInbox(pubkey, write.normalizedValue)
        items = write.items
        onChanged(NativeActivityInboxProtocol.unreadCount(items))
    }
}
