package app.roadstr.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeExternalDestinationTest {
    @Test
    fun `Tankful destination contract is accepted and normalized`() {
        val destination = NativeExternalDestinationProtocol.decode(
            action = NativeExternalDestinationProtocol.ACTION_SEND,
            mimeType = "application/json",
            payload = """{"label":"  Fuel Torino  ","lat":45.0703,"lon":7.6869}""",
        )

        assertEquals(
            NativeExternalDestination("Fuel Torino", 45.0703, 7.6869),
            destination,
        )
    }

    @Test
    fun `wrong action MIME malformed and open JSON objects are rejected`() {
        val valid = """{"label":"Fuel","lat":45.0,"lon":7.0}"""
        assertNull(NativeExternalDestinationProtocol.decode("android.intent.action.VIEW", "application/json", valid))
        assertNull(NativeExternalDestinationProtocol.decode(NativeExternalDestinationProtocol.ACTION_SEND, "text/plain", valid))
        assertNull(NativeExternalDestinationProtocol.decodePayload("not-json"))
        assertNull(NativeExternalDestinationProtocol.decodePayload("""{"label":"Fuel","lat":45.0,"lon":7.0,"url":"https://example.com"}"""))
        assertNull(NativeExternalDestinationProtocol.decodePayload("""{"label":"Fuel","label":"Other","lat":45.0,"lon":7.0}"""))
    }

    @Test
    fun `coordinates labels numeric types and payload size are bounded`() {
        assertNull(NativeExternalDestinationProtocol.decodePayload("""{"label":"Fuel","lat":91,"lon":7}"""))
        assertNull(NativeExternalDestinationProtocol.decodePayload("""{"label":"Fuel","lat":45,"lon":-181}"""))
        assertNull(NativeExternalDestinationProtocol.decodePayload("""{"label":"Fuel","lat":"45","lon":7}"""))
        assertNull(NativeExternalDestinationProtocol.decodePayload("""{"label":"","lat":45,"lon":7}"""))
        assertNull(
            NativeExternalDestinationProtocol.decodePayload(
                """{"label":"${"x".repeat(NativeExternalDestinationProtocol.MAX_LABEL_CHARS + 1)}","lat":45,"lon":7}""",
            ),
        )
        assertNull(
            NativeExternalDestinationProtocol.decodePayload(
                " ".repeat(NativeExternalDestinationProtocol.MAX_PAYLOAD_BYTES + 1),
            ),
        )
    }

    @Test
    fun `inbox supports cold start singleTop replacement and exact consumption`() {
        val inbox = NativeExternalDestinationInbox()
        assertTrue(inbox.offer(
            NativeExternalDestinationProtocol.ACTION_SEND,
            NativeExternalDestinationProtocol.MIME_JSON,
            """{"label":"First","lat":45,"lon":7}""",
        ))
        val first = inbox.requests.value!!
        assertFalse(inbox.consume(first.revision + 1))
        assertEquals(first, inbox.requests.value)

        assertTrue(inbox.offer(
            NativeExternalDestinationProtocol.ACTION_SEND,
            "application/json; charset=utf-8",
            """{"label":"Second","lat":46,"lon":8}""",
        ))
        val second = inbox.requests.value!!
        assertTrue(second.revision > first.revision)
        assertEquals("Second", second.destination.label)
        assertTrue(inbox.consume(second.revision))
        assertNull(inbox.requests.value)
    }
}
