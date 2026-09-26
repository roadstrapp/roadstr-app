package app.roadstr.core.protocol.nostr

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrPendingReportQueueParityTest {
    private val queueRows: List<List<String>> by lazy {
        resourceRows("/parity/nostr_pending_queue_v1.tsv")
    }
    private val nostrRows: List<List<String>> by lazy {
        resourceRows("/parity/nostr_protocol_v1.tsv")
    }

    @Test
    fun `native FIFO policy matches every Dart flush transcript`() {
        var executed = 0
        for (fields in queueRows.filter { row -> row[0] == "flush" }) {
            val specs = fields[3].takeUnless { it == "-" }
                ?.split(';')
                ?.map { encoded -> encoded.split(',') }
                ?: emptyList()
            val byId = specs.associateBy { spec -> spec[0] }
            val entries = specs.map { spec ->
                PendingRoadReport(
                    event = linkedMapOf("id" to spec[0]),
                    expiresAt = spec[1].toLong(),
                )
            }
            val result = NostrPendingReportQueue.flush(
                pending = entries,
                now = fields[2].toLong(),
                verify = { event ->
                    byId.getValue(event["id"] as String)[2].toBooleanStrict()
                },
                publish = { event ->
                    when (byId.getValue(event["id"] as String)[3]) {
                        "success" -> true
                        "failure" -> false
                        "throws" -> error("transport failed")
                        else -> error("unknown fixture publish outcome")
                    }
                },
            )

            assertEquals(fields[1], csv(fields[4]), result.attemptedIds)
            assertEquals(fields[1], csv(fields[5]), result.remaining.map { it.id })
            assertEquals(
                fields[1],
                csv(fields[6]),
                result.decisions.map { decision ->
                    "${decision.id}=${decision.disposition.wireName}"
                },
            )
            executed++
        }
        assertEquals(6, executed)
    }

    @Test
    fun `signed storage JSON matches the exact Dart Hive string`() {
        val fields = queueRows.single { row -> row[0] == "storage" }
        val report = nostrRows.single { row ->
            row[0] == "report" && row[1] == fields[2]
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
        val entry = PendingRoadReport(
            event = draft.toWireMap(signature = fields[3]),
            expiresAt = fields[4].toLong(),
        )

        assertEquals(unb64(fields[5]), entry.storageJson())
    }

    @Test
    fun `expiration is checked before verification and publication`() {
        var verifications = 0
        var publications = 0
        val result = NostrPendingReportQueue.flush(
            pending = listOf(
                PendingRoadReport(mapOf("id" to "past"), 99),
                PendingRoadReport(mapOf("id" to "boundary"), 100),
            ),
            now = 100,
            verify = {
                verifications++
                true
            },
            publish = {
                publications++
                true
            },
        )

        assertEquals(0, verifications)
        assertEquals(0, publications)
        assertEquals(
            listOf(PendingReportDisposition.EXPIRED, PendingReportDisposition.EXPIRED),
            result.decisions.map { it.disposition },
        )
    }

    @Test
    fun `missing expiration and event fail closed`() {
        var verifications = 0
        val result = NostrPendingReportQueue.flush(
            pending = listOf(
                PendingRoadReport(mapOf("id" to "missing-expiration"), null),
                PendingRoadReport(null, 200),
            ),
            now = 100,
            verify = {
                verifications++
                true
            },
            publish = { true },
        )

        assertEquals(0, verifications)
        assertEquals(
            listOf(PendingReportDisposition.EXPIRED, PendingReportDisposition.INVALID),
            result.decisions.map { it.disposition },
        )
        assertTrue(result.remaining.isEmpty())
    }

    @Test
    fun `queue entries deeply copy caller-owned JSON`() {
        val tag = mutableListOf("t", "hazard")
        val tags = mutableListOf<List<String>>(tag)
        val event = linkedMapOf<String, Any?>(
            "id" to "event-id",
            "tags" to tags,
        )
        val entry = PendingRoadReport(event, 200)

        event["id"] = "changed"
        tag[1] = "changed"
        tags.clear()

        assertEquals("event-id", entry.id)
        assertEquals(
            "{\"event\":{\"id\":\"event-id\",\"tags\":[[\"t\",\"hazard\"]]},\"expiresAt\":200}",
            entry.storageJson(),
        )
    }

    @Test
    fun `a verifier exception aborts instead of rewriting an uncertain queue`() {
        val entry = PendingRoadReport(mapOf("id" to "event-id"), 200)

        assertThrows(IllegalStateException::class.java) {
            NostrPendingReportQueue.flush(
                pending = listOf(entry),
                now = 100,
                verify = { error("verification unavailable") },
                publish = { true },
            )
        }
    }

    @Test
    fun `storage serializer rejects incomplete records`() {
        assertThrows(IllegalArgumentException::class.java) {
            PendingRoadReport(null, 200).storageJson()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PendingRoadReport(mapOf("id" to "event-id"), null).storageJson()
        }
    }

    private fun resourceRows(path: String): List<List<String>> {
        val resource = requireNotNull(javaClass.getResourceAsStream(path))
        return resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    private fun csv(value: String): List<String> =
        value.takeUnless { it == "-" }?.split(',') ?: emptyList()

    private fun unb64(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
