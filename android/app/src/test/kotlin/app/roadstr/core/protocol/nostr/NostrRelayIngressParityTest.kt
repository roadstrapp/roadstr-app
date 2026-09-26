package app.roadstr.core.protocol.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NostrRelayIngressParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/nostr_ingress_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native admission policy reproduces every stateful Dart transcript`() {
        assertEquals(35, rows.size)
        val policies = mutableMapOf<String, NostrRelayIngress>()
        for (fields in rows) {
            val scenario = fields[0]
            val policy = policies.getOrPut(scenario) { policy(scenario) }
            val decision = policy.inspect(
                subscriptionId = fields[2],
                claimedKind = parseKind(fields[3]),
            )

            assertEquals(fields[1], fields[4], decision.verdict.wireName)
            assertEquals(fields[1], fields[5], decision.route?.wireName ?: "-")
            assertEquals(fields[1], fields[6], decision.ruleName ?: "-")
            assertEquals(fields[1], fields[7].toInt(), decision.observedEvents)
        }
    }

    @Test
    fun `integral JSON longs use the same routes as Dart ints`() {
        val ingress = NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "events",
                    subscriptionId = "sub",
                    routes = mapOf(1315 to NostrIngressRoute.ROAD_EVENT),
                ),
            ),
        )

        val decision = ingress.inspect("sub", 1315L)
        assertEquals(NostrIngressVerdict.VERIFY, decision.verdict)
        assertEquals(NostrIngressRoute.ROAD_EVENT, decision.route)

        val fallbackCollision = NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "fallback",
                    subscriptionId = "same",
                    fallbackRoute = NostrIngressRoute.PROFILE_METADATA,
                ),
                NostrIngressRule(
                    name = "exact",
                    subscriptionId = "same",
                    routes = mapOf(1315 to NostrIngressRoute.ROAD_EVENT),
                ),
            ),
        )
        assertEquals(
            NostrIngressRoute.ROAD_EVENT,
            fallbackCollision.inspect("same", 1315L).route,
        )
    }

    @Test
    fun `rules validate configuration and isolate caller maps`() {
        assertThrows(IllegalArgumentException::class.java) {
            NostrIngressRule(name = "", subscriptionId = "sub")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NostrIngressRule(name = "empty", subscriptionId = "sub")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NostrIngressRule(
                name = "zero",
                subscriptionId = "sub",
                fallbackRoute = NostrIngressRoute.PROFILE_METADATA,
                maxEvents = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NostrRelayIngress(
                listOf(
                    NostrIngressRule(
                        name = "same",
                        subscriptionId = "one",
                        fallbackRoute = NostrIngressRoute.PROFILE_METADATA,
                    ),
                    NostrIngressRule(
                        name = "same",
                        subscriptionId = "two",
                        fallbackRoute = NostrIngressRoute.PROFILE_METADATA,
                    ),
                ),
            )
        }

        val routes = mutableMapOf(1315 to NostrIngressRoute.ROAD_EVENT)
        val ingress = NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "isolated",
                    subscriptionId = "sub",
                    routes = routes,
                ),
            ),
        )
        routes[1315] = NostrIngressRoute.ROAD_UPDATE
        assertEquals(
            NostrIngressRoute.ROAD_EVENT,
            ingress.inspect("sub", 1315).route,
        )
    }

    private fun policy(scenario: String): NostrRelayIngress = when (scenario) {
        "live" -> NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "events",
                    subscriptionId = "events",
                    routes = mapOf(
                        1315 to NostrIngressRoute.ROAD_EVENT,
                        1317 to NostrIngressRoute.ROAD_UPDATE,
                    ),
                ),
                NostrIngressRule(
                    name = "confirmations",
                    subscriptionId = "confirmations",
                    routes = mapOf(1316 to NostrIngressRoute.CONFIRMATION),
                ),
                NostrIngressRule(
                    name = "mine",
                    subscriptionId = "mine",
                    routes = mapOf(1316 to NostrIngressRoute.OWN_CONFIRMATION),
                ),
                NostrIngressRule(
                    name = "zaps",
                    subscriptionId = "zaps",
                    routes = mapOf(9735 to NostrIngressRoute.ZAP_RECEIPT),
                ),
            ),
        )

        "live-collision" -> NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "events",
                    subscriptionId = "same",
                    routes = mapOf(
                        1315 to NostrIngressRoute.ROAD_EVENT,
                        1317 to NostrIngressRoute.ROAD_UPDATE,
                    ),
                ),
                NostrIngressRule(
                    name = "confirmations",
                    subscriptionId = "same",
                    routes = mapOf(1316 to NostrIngressRoute.CONFIRMATION),
                ),
                NostrIngressRule(
                    name = "mine",
                    subscriptionId = "same",
                    routes = mapOf(1316 to NostrIngressRoute.OWN_CONFIRMATION),
                ),
                NostrIngressRule(
                    name = "zaps",
                    subscriptionId = "same",
                    routes = mapOf(9735 to NostrIngressRoute.ZAP_RECEIPT),
                ),
            ),
        )

        "history" -> historyPolicy("events", "votes")
        "history-collision" -> historyPolicy("same", "same")
        "edit" -> NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "edit",
                    subscriptionId = "edit",
                    routes = mapOf(1318 to NostrIngressRoute.EDIT_REQUEST),
                    maxEvents = 2,
                ),
            ),
        )

        "fallback" -> NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "profile",
                    subscriptionId = "profile",
                    fallbackRoute = NostrIngressRoute.PROFILE_METADATA,
                    maxEvents = 2,
                ),
            ),
        )

        "visibility" -> NostrRelayIngress(
            listOf(
                NostrIngressRule(
                    name = "visibility",
                    subscriptionId = "visibility",
                    routes = mapOf(
                        30078 to NostrIngressRoute.PROFILE_VISIBILITY,
                    ),
                    maxEvents = 2,
                ),
            ),
        )

        else -> error("Unknown fixture scenario: $scenario")
    }

    private fun historyPolicy(
        eventSubscription: String,
        voteSubscription: String,
    ): NostrRelayIngress = NostrRelayIngress(
        listOf(
            NostrIngressRule(
                name = "reports",
                subscriptionId = eventSubscription,
                routes = mapOf(
                    1315 to NostrIngressRoute.USER_REPORT,
                    1317 to NostrIngressRoute.USER_UPDATE,
                ),
                maxEvents = 2,
            ),
            NostrIngressRule(
                name = "votes",
                subscriptionId = voteSubscription,
                routes = mapOf(1316 to NostrIngressRoute.USER_VOTE),
                maxEvents = 1,
            ),
        ),
    )

    private fun parseKind(encoded: String): Any? {
        if (encoded == "null") return null
        val (type, value) = encoded.split(':', limit = 2)
        return when (type) {
            "int" -> value.toInt()
            "string" -> value
            else -> error("Unknown fixture kind: $encoded")
        }
    }
}
