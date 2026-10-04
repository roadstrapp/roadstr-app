package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrSchnorr

/** A relay connection the test drives by hand. */
class FakeSocket(val url: String, val events: NativeRelayEvents) : NativeRelaySocket {
    val sent = mutableListOf<String>()
    var closed = false
    var failSend = false

    override fun send(text: String): Boolean {
        if (closed || failSend) return false
        sent += text
        return true
    }

    override fun close() {
        closed = true
    }

    fun open() = events.onOpen()

    fun receive(frame: List<Any?>) = events.onMessage(NostrJson.encode(frame))

    fun end() = events.onEnded()
}

class FakeConnector : NativeRelayConnector {
    val sockets = mutableListOf<FakeSocket>()
    var onConnect: (FakeSocket) -> Unit = {}
    var throwOnConnect = false

    override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
        if (throwOnConnect) throw IllegalStateException("no network")
        return FakeSocket(url, events).also {
            sockets += it
            onConnect(it)
        }
    }

    val last: FakeSocket get() = sockets.last()
}

/** Timers advanced explicitly, in order. */
class ManualScheduler : NativeScheduler {
    private data class Task(val at: Long, val block: () -> Unit, var cancelled: Boolean = false)

    private val tasks = mutableListOf<Task>()
    var now = 0L
        private set

    override fun schedule(delayMillis: Long, task: () -> Unit): NativeScheduledTask {
        val entry = Task(now + delayMillis, task)
        tasks += entry
        return NativeScheduledTask { entry.cancelled = true }
    }

    val pendingCount: Int get() = tasks.count { !it.cancelled }

    fun advance(millis: Long) {
        val target = now + millis
        while (true) {
            val next = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
            tasks.remove(next)
            now = next.at
            next.block()
        }
        now = target
    }
}

object TestKeys {
    const val PRIVATE_A = "0000000000000000000000000000000000000000000000000000000000000003"
    const val PRIVATE_B = "0000000000000000000000000000000000000000000000000000000000000005"
    val PUBLIC_A: String = NostrSchnorr.publicKey(PRIVATE_A)
    val PUBLIC_B: String = NostrSchnorr.publicKey(PRIVATE_B)

    fun sign(draft: NostrEventDraft, privateKey: String = PRIVATE_A): Map<String, Any?> =
        NostrSchnorr.signEvent(draft, privateKey)
}

/**
 * A tiny in-memory Nostr network: every URL is a relay that stores replaceable
 * events and answers REQ and EVENT frames immediately.
 */
class FakeRelayNetwork : NativeRelayConnector {
    val stored = mutableMapOf<String, MutableList<Map<String, Any?>>>()
    val down = mutableSetOf<String>()
    val connections = mutableListOf<String>()

    private fun key(event: Map<String, Any?>): String {
        val d = NativeNostrWire.tagValue(event, "d")
        return "${event["pubkey"]}:${event["kind"]}:${d ?: ""}"
    }

    override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
        connections += url
        if (url in down) {
            events.onEnded()
            return object : NativeRelaySocket {
                override fun send(text: String) = false
                override fun close() {}
            }
        }
        val store = stored.getOrPut(url) { mutableListOf() }
        events.onOpen()
        return object : NativeRelaySocket {
            override fun send(text: String): Boolean {
                @Suppress("UNCHECKED_CAST")
                val frame = app.roadstr.core.protocol.nostr.BoundedJsonParser(text).parse() as List<Any?>
                when (frame[0]) {
                    "EVENT" -> {
                        @Suppress("UNCHECKED_CAST")
                        val event = frame[1] as Map<String, Any?>
                        val replaceable = (event["kind"] as Number).toInt() in 30000..39999
                        if (replaceable) {
                            val existing = store.indexOfFirst { key(it) == key(event) }
                            if (existing >= 0) {
                                val older = (store[existing]["created_at"] as Number).toLong() <
                                    (event["created_at"] as Number).toLong()
                                if (older) store[existing] = event
                            } else {
                                store += event
                            }
                        } else {
                            store += event
                        }
                        events.onMessage(NostrJson.encode(listOf("OK", event["id"], true, "")))
                    }

                    "REQ" -> {
                        val sub = frame[1] as String
                        val filter = frame[2] as Map<*, *>
                        val kinds = (filter["kinds"] as? List<*>)?.map { (it as Number).toInt() }
                        val authors = filter["authors"] as? List<*>
                        val dTags = filter["#d"] as? List<*>
                        store.filter { event ->
                            (kinds == null || (event["kind"] as Number).toInt() in kinds) &&
                                (authors == null || event["pubkey"] in authors) &&
                                (dTags == null || NativeNostrWire.tagValue(event, "d") in dTags)
                        }.forEach { events.onMessage(NostrJson.encode(listOf("EVENT", sub, it))) }
                        events.onMessage(NostrJson.encode(listOf("EOSE", sub)))
                    }
                }
                return true
            }

            override fun close() {}
        }
    }
}
