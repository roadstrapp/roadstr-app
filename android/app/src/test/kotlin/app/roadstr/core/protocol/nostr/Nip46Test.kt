package app.roadstr.core.protocol.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Nip46Test {
    private val signer = "a".repeat(64)

    @Test
    fun `a bunker link carries the signer, its relays and the secret`() {
        val link = NostrBunkerUri.parse(
            "bunker://$signer?relay=wss%3A%2F%2Frelay.example.com&relay=wss://nos.lol&secret=abc123",
        )

        assertNotNull(link)
        assertEquals(signer, link!!.remotePubkeyHex)
        assertEquals(listOf("wss://relay.example.com", "wss://nos.lol"), link.relays)
        assertEquals("abc123", link.secret)
    }

    @Test
    fun `a link without a secret is fine and the signer is lower-cased`() {
        val link = NostrBunkerUri.parse("  BUNKER://${signer.uppercase()}?relay=wss://r.example  ")

        assertEquals(signer, link!!.remotePubkeyHex)
        assertNull(link.secret)
    }

    @Test
    fun `links that cannot be trusted are refused`() {
        assertNull(NostrBunkerUri.parse(""))
        assertNull(NostrBunkerUri.parse("nostr+walletconnect://$signer?relay=wss://r.example"))
        assertNull(NostrBunkerUri.parse("bunker://$signer"))
        assertNull(NostrBunkerUri.parse("bunker://$signer?relay=ws://clear.example"))
        assertNull(NostrBunkerUri.parse("bunker://nothex?relay=wss://r.example"))
        assertNull(NostrBunkerUri.parse("bunker://${signer.take(63)}?relay=wss://r.example"))
        assertNull(NostrBunkerUri.parse("bunker://$signer?relay=wss://r.example&x=" + "y".repeat(3_000)))
    }

    @Test
    fun `no more than four relays are kept`() {
        val query = (1..9).joinToString("&") { "relay=wss://r$it.example" }

        assertEquals(4, NostrBunkerUri.parse("bunker://$signer?$query")!!.relays.size)
    }

    @Test
    fun `requests and answers are plain JSON-RPC`() {
        assertEquals(
            """{"id":"1","method":"sign_event","params":["x"]}""",
            Nip46.requestJson("1", "sign_event", listOf("x")),
        )
        val ok = Nip46.parseResponse("""{"id":"1","result":"ack"}""")!!
        assertEquals("ack", ok.result)
        assertNull(ok.error)
        val refused = Nip46.parseResponse("""{"id":"2","result":null,"error":"denied"}""")!!
        assertEquals("denied", refused.error)
        assertNull(Nip46.parseResponse("""{"result":"ack"}"""))
        assertNull(Nip46.parseResponse("""{"id":"3","result":5}"""))
        assertNull(Nip46.parseResponse("not json"))
    }

    @Test
    fun `an approval request is recognised only when it points to a secure address`() {
        val secure = Nip46.parseResponse("""{"id":"1","result":"auth_url","error":"https://signer.example/approve"}""")!!
        assertEquals("https://signer.example/approve", secure.authUrl)
        assertNull(Nip46.parseResponse("""{"id":"1","result":"auth_url","error":"http://signer.example"}""")!!.authUrl)
        assertNull(Nip46.parseResponse("""{"id":"1","result":"auth_url","error":"javascript:alert(1)"}""")!!.authUrl)
        assertTrue(Nip46.parseResponse("""{"id":"1","result":"ack"}""")!!.authUrl == null)
    }
}
