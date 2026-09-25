package app.roadstr.core.protocol.nostr

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/nostr_protocol_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `all Roadstr categories match the shared Dart fixture`() {
        val expected = RoadCategoryWire.entries.associate { category ->
            category.dartName to (category.wireKey to category.ttlSeconds)
        }
        val actual = rows
            .filter { fields -> fields[0] == "category" }
            .associate { fields -> fields[1] to (fields[2] to fields[3].toInt()) }

        assertEquals(expected, actual)
        assertEquals(14, actual.size)
    }

    @Test
    fun `native Roadstr event factories match every Dart vector`() {
        var executed = 0
        for (fields in rows) {
            val draft = when (fields[0]) {
                "report" -> RoadstrNostrEvents.report(
                    pubkey = fields[2],
                    createdAt = fields[3].toLong(),
                    latitude = fields[4].toDouble(),
                    longitude = fields[5].toDouble(),
                    category = fields[6],
                    expiresAt = fields[7].toLong(),
                    speedLimit = fields[8].takeUnless { it == "-" }?.toInt(),
                    content = unb64(fields[9]),
                ).also { assertDraft(fields, it, 10) }

                "vote" -> RoadstrNostrEvents.vote(
                    pubkey = fields[2],
                    createdAt = fields[3].toLong(),
                    eventId = fields[4],
                    stillThere = fields[5].toBooleanStrict(),
                ).also { assertDraft(fields, it, 6) }

                "update" -> RoadstrNostrEvents.update(
                    ownerPubkey = fields[2],
                    createdAt = fields[3].toLong(),
                    eventId = fields[4],
                    speedLimit = fields[5].toInt(),
                    latitude = fields[6].toDouble(),
                    longitude = fields[7].toDouble(),
                    requestId = fields[8].takeUnless { it == "-" },
                    content = unb64(fields[9]),
                ).also { assertDraft(fields, it, 10) }

                "edit" -> RoadstrNostrEvents.editRequest(
                    requesterPubkey = fields[2],
                    ownerPubkey = fields[3],
                    createdAt = fields[4].toLong(),
                    eventId = fields[5],
                    speedLimit = fields[6].toInt(),
                    latitude = fields[7].toDouble(),
                    longitude = fields[8].toDouble(),
                ).also { assertDraft(fields, it, 9) }

                "profile" -> RoadstrNostrEvents.profileVisibility(
                    pubkey = fields[2],
                    createdAt = fields[3].toLong(),
                    isPublic = fields[4].toBooleanStrict(),
                ).also { assertDraft(fields, it, 5) }

                else -> null
            }
            if (draft != null) executed++
        }

        assertEquals(21, executed)
    }

    @Test
    fun `canonical JSON escaping and SHA-256 match Dart byte for byte`() {
        val fields = rows.single { row -> row[0] == "event" }
        val draft = NostrEventDraft(
            pubkey = fields[2],
            createdAt = fields[3].toLong(),
            kind = fields[4].toInt(),
            tags = decodeTags(fields[5]),
            content = unb64(fields[6]),
        )

        assertDraft(fields, draft, 7)
        assertTrue(draft.canonicalJson().contains("café"))
        assertTrue(draft.canonicalJson().contains("\\u0000\\u001f"))
    }

    @Test
    fun `EVENT REQ and CLOSE frames match exact Dart wire bytes`() {
        val report = rows.single { row ->
            row[0] == "report" && row[1] == "report-speedCamera"
        }
        val draft = RoadstrNostrEvents.report(
            pubkey = report[2],
            createdAt = report[3].toLong(),
            latitude = report[4].toDouble(),
            longitude = report[5].toDouble(),
            category = report[6],
            expiresAt = report[7].toLong(),
            speedLimit = report[8].toInt(),
            content = unb64(report[9]),
        )
        val publish = rows.single { row -> row[0] == "wire-publish" }
        assertEquals(
            unb64(publish[4]),
            NostrRelayWire.encode(
                NostrRelayWire.publish(draft.toWireMap(signature = publish[3])),
            ),
        )

        val area = rows.single { row -> row[0] == "wire-area" }
        assertEquals(
            unb64(area[5]),
            NostrRelayWire.encode(
                NostrRelayWire.areaRequest(
                    subscriptionId = area[2],
                    now = area[3].toLong(),
                    geohashes = area[4].split(','),
                ),
            ),
        )

        val confirmations = rows.single { row -> row[0] == "wire-confirmations" }
        assertEquals(
            unb64(confirmations[5]),
            NostrRelayWire.encode(
                NostrRelayWire.confirmationRequest(
                    subscriptionId = confirmations[2],
                    now = confirmations[3].toLong(),
                    eventIds = confirmations[4].split(','),
                ),
            ),
        )

        val close = rows.single { row -> row[0] == "wire-close" }
        assertEquals(
            unb64(close[3]),
            NostrRelayWire.encode(NostrRelayWire.close(close[2])),
        )
    }

    @Test
    fun `event and filter constructors copy caller-owned collections`() {
        val mutableTag = mutableListOf("t", "hazard")
        val mutableTags = mutableListOf<List<String>>(mutableTag)
        val draft = NostrEventDraft(
            pubkey = "1".repeat(64),
            createdAt = 1_700_000_000,
            kind = 1,
            tags = mutableTags,
            content = "",
        )
        val mutableGeohashes = mutableListOf("sr2y")
        val request = NostrRelayWire.areaRequest("sub", mutableGeohashes, 1_700_000_000)

        mutableTag[1] = "changed"
        mutableTags.clear()
        mutableGeohashes[0] = "changed"

        assertEquals(listOf(listOf("t", "hazard")), draft.tags)
        @Suppress("UNCHECKED_CAST")
        val filter = request[2] as Map<String, Any?>
        assertEquals(listOf("sr2y"), filter["#g"])
    }

    @Test
    fun `deterministic JSON rejects floating point and non-string object keys`() {
        assertThrows(IllegalArgumentException::class.java) { NostrJson.encode(1.5) }
        assertThrows(IllegalArgumentException::class.java) {
            NostrJson.encode(linkedMapOf<Any, Any>(1 to "value"))
        }
    }

    @Test
    fun `Roadstr update speed limits retain exact boundaries`() {
        fun build(speed: Int) = RoadstrNostrEvents.update(
            ownerPubkey = "3".repeat(64),
            createdAt = 1_700_000_000,
            eventId = "2".repeat(64),
            speedLimit = speed,
            latitude = 0.0,
            longitude = 0.0,
            content = "",
        )

        assertThrows(IllegalArgumentException::class.java) { build(4) }
        assertThrows(IllegalArgumentException::class.java) { build(301) }
        assertEquals(listOf("maxspeed", "5"), build(5).tags.last())
        assertEquals(listOf("maxspeed", "300"), build(300).tags.last())
    }

    private fun assertDraft(fields: List<String>, draft: NostrEventDraft, suffixAt: Int) {
        assertEquals(fields[1], unb64(fields[suffixAt]), draft.canonicalJson())
        assertEquals(fields[1], fields[suffixAt + 1], draft.id())
    }

    private fun decodeTags(value: String): List<List<String>> = value.split(',').map { tag ->
        tag.split('.').map(::unb64)
    }

    private fun unb64(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
