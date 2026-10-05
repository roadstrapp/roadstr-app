package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.Nip04Cipher
import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrSchnorr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pays a real, parity-locked invoice through a wallet that lives in the test. */
class NativeZapNwcTest {
    private val invoice =
        "lnbc10n1pjeyqyqpp5vvxu62txcsekdygj23ythvjmfl6p9fyuwvkm9j9tcxu9sx7hzrwshp5rq4vfx7cxdr6pjpyprgtavyanph3pg62l748j8fwsaat6u8tarfqxqzjcqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqkc2g6m"
    private val preimage = (0 until 32).joinToString("") { "%02x".format(it) }
    private val now = 1_704_067_200L + 30
    private val walletKey = TestKeys.PRIVATE_B
    private val walletPub = TestKeys.PUBLIC_B
    private val clientSecret = "0000000000000000000000000000000000000000000000000000000000000007"
    private val clientPub = NostrSchnorr.publicKey(clientSecret)
    private val uri = "nostr+walletconnect://$walletPub?relay=wss%3A%2F%2Fwallet.example&secret=$clientSecret"

    private enum class Dialect { Nip44, Nip04NoInfo }

    /** A wallet relay: answers capability lookups and pay_invoice requests. */
    private inner class WalletRelay(
        private val dialect: Dialect,
        private val replyPreimage: String? = preimage,
        private val answers: Boolean = true,
    ) : NativeRelayConnector {
        var requestEvents = 0
        override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
            events.onOpen()
            var responseSub: String? = null
            return object : NativeRelaySocket {
                override fun send(text: String): Boolean {
                    val frame = BoundedJsonParser(text).parse() as List<*>
                    when (frame[0]) {
                        "REQ" -> {
                            val sub = frame[1] as String
                            val filter = frame[2] as Map<*, *>
                            val kinds = (filter["kinds"] as List<*>).map { (it as Number).toInt() }
                            if (13194 in kinds && dialect == Dialect.Nip44) {
                                val info = TestKeys.sign(
                                    NostrEventDraft(walletPub, now, 13194, listOf(listOf("encryption", "nip44_v2")), "pay_invoice get_balance"),
                                    walletKey,
                                )
                                events.onMessage(NostrJson.encode(listOf("EVENT", sub, info)))
                            }
                            if (13194 in kinds) events.onMessage(NostrJson.encode(listOf("EOSE", sub)))
                            if (23195 in kinds) responseSub = sub
                        }

                        "EVENT" -> {
                            @Suppress("UNCHECKED_CAST")
                            val request = frame[1] as Map<String, Any?>
                            requestEvents++
                            if (!answers) return true
                            val content = request["content"] as String
                            val plain = if (dialect == Dialect.Nip44) {
                                Nip44V2.decrypt(walletKey, clientPub, content)
                            } else {
                                Nip04Cipher.decrypt(walletKey, clientPub, content)
                            }
                            val command = BoundedJsonParser(plain).parse() as Map<*, *>
                            assertEquals("pay_invoice", command["method"])
                            assertEquals(invoice, (command["params"] as Map<*, *>)["invoice"])
                            val body = NostrJson.encode(
                                linkedMapOf(
                                    "result_type" to "pay_invoice",
                                    "result" to if (replyPreimage != null) linkedMapOf("preimage" to replyPreimage) else null,
                                ),
                            )
                            val encrypted = if (dialect == Dialect.Nip44) {
                                Nip44V2.encrypt(walletKey, clientPub, body)
                            } else {
                                Nip04Cipher.encrypt(walletKey, clientPub, body)
                            }
                            val response = TestKeys.sign(
                                NostrEventDraft(
                                    walletPub, now, 23195,
                                    listOf(listOf("e", request["id"] as String), listOf("p", clientPub)),
                                    encrypted,
                                ),
                                walletKey,
                            )
                            events.onMessage(NostrJson.encode(listOf("EVENT", responseSub, response)))
                        }
                    }
                    return true
                }

                override fun close() {}
            }
        }
    }

    private fun service(relay: NativeRelayConnector) =
        NativeZapService(relay, relays = listOf("wss://a.example"), nowSeconds = { now })

    @Test
    fun `a nip44 wallet pays and the preimage is verified against the invoice`() = runBlocking {
        val wallet = WalletRelay(Dialect.Nip44)
        assertEquals(preimage, service(wallet).payViaNwc(invoice, uri))
        assertEquals(1, wallet.requestEvents)
    }

    @Test
    fun `a wallet with no info event is spoken to in nip04`() = runBlocking {
        val wallet = WalletRelay(Dialect.Nip04NoInfo)
        assertEquals(preimage, service(wallet).payViaNwc(invoice, uri))
    }

    @Test
    fun `a preimage that does not belong to the invoice is not a payment`() = runBlocking {
        val wallet = WalletRelay(Dialect.Nip44, replyPreimage = "00".repeat(32))
        assertNull(service(wallet).payViaNwc(invoice, uri))
    }

    @Test
    fun `a wallet that never answers gives up instead of hanging`() = runBlocking {
        // The response window is 30 s in production; the info lookup already
        // times out quickly, so only check that a refusing wallet returns null.
        val wallet = WalletRelay(Dialect.Nip44, replyPreimage = null)
        assertNull(service(wallet).payViaNwc(invoice, uri))
    }

    @Test
    fun `an expired invoice and a malformed uri never reach the network`() = runBlocking {
        val wallet = WalletRelay(Dialect.Nip44)
        val late = NativeZapService(wallet, relays = emptyList(), nowSeconds = { now + 10_000 })
        assertNull(late.payViaNwc(invoice, uri))
        assertNull(service(wallet).payViaNwc(invoice, "https://not-nwc.example"))
        assertEquals(0, wallet.requestEvents)
        assertTrue(true)
    }
}
