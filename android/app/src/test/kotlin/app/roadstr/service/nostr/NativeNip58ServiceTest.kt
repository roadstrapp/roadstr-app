package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.Nip58BadgeReference
import app.roadstr.core.protocol.nostr.Nip58Protocol
import app.roadstr.core.protocol.nostr.NostrEventDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNip58ServiceTest {
    private val network = FakeRelayNetwork()
    private val relays = listOf("wss://relay.one", "wss://relay.two")
    private val signer = NativeLocalKeySigner { TestKeys.PRIVATE_A }
    private val reference = Nip58BadgeReference(TestKeys.PUBLIC_B, "roadstr.journey.ignition.v1")

    private fun service() = NativeNip58Service(
        signer,
        NativeRelayFetcher(network),
        NativeRelayPublisher(network, relays),
        relays,
        { 2_000L },
    )

    @Test
    fun `verified issuer award can be found and added without dropping prior badges`() = runBlocking {
        val definitionEvent = TestKeys.sign(
            Nip58Protocol.definitionDraft(
                TestKeys.PUBLIC_B,
                900,
                reference.identifier,
                "Ignition",
                "Every journey starts somewhere.",
                "https://assets.roadstr.example/ignition.png",
            ),
            TestKeys.PRIVATE_B,
        )
        val awardEvent = TestKeys.sign(
            Nip58Protocol.awardDraft(TestKeys.PUBLIC_B, 1_000, reference, listOf(TestKeys.PUBLIC_A)),
            TestKeys.PRIVATE_B,
        )
        val priorTags = listOf(
            listOf("a", "30009:${TestKeys.PUBLIC_B}:some-other-badge"),
            listOf("e", "aa".repeat(32)),
        )
        val priorProfile = TestKeys.sign(
            NostrEventDraft(TestKeys.PUBLIC_A, 1_500, Nip58Protocol.PROFILE_BADGES_KIND, priorTags, ""),
        )
        relays.forEach { relay ->
            network.stored.getOrPut(relay) { mutableListOf() }.apply {
                add(definitionEvent)
                add(awardEvent)
                add(priorProfile)
            }
        }

        val award = service().findAward(reference, TestKeys.PUBLIC_A)
        assertNotNull(award)
        assertTrue(service().addToProfile(award!!))
        val newest = network.stored.getValue(relays.first()).maxBy { (it["created_at"] as Number).toLong() }
        val tags = NativeNostrWire.tags(newest["tags"]).orEmpty()
        assertEquals(priorTags, tags.take(priorTags.size))
        assertTrue(tags.any { it == listOf("a", reference.address) })
        assertTrue(NativeNostrWire.verify(newest))
    }

    @Test
    fun `an award without its signed definition is not exposed`() = runBlocking {
        val awardEvent = TestKeys.sign(
            Nip58Protocol.awardDraft(TestKeys.PUBLIC_B, 1_000, reference, listOf(TestKeys.PUBLIC_A)),
            TestKeys.PRIVATE_B,
        )
        relays.forEach { relay -> network.stored.getOrPut(relay) { mutableListOf() }.add(awardEvent) }

        assertEquals(null, service().findAward(reference, TestKeys.PUBLIC_A))
    }

    @Test
    fun `award for another account cannot be accepted`() = runBlocking {
        val event = TestKeys.sign(
            Nip58Protocol.awardDraft(TestKeys.PUBLIC_B, 1_000, reference, listOf("cc".repeat(32))),
            TestKeys.PRIVATE_B,
        )
        val award = Nip58Protocol.parseAward(event) { NativeNostrWire.verify(event) }!!
        assertFalse(service().addToProfile(award))
    }
}
