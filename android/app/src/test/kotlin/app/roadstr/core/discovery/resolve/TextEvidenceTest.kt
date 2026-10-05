package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.OsmElementType
import app.roadstr.core.discovery.OsmRef
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEvidenceTest {
    @Test
    fun `a phone number is found with or without the prefix`() {
        val phone = "+39 045 1234567"
        assertTrue(TextEvidence.phoneIn(phone, "Chiama 045 123 4567 per prenotare"))
        assertTrue(TextEvidence.phoneIn(phone, "Tel. +39 045-1234567"))
        assertTrue(TextEvidence.phoneIn(phone, "solo 123 4567"))
        assertFalse(TextEvidence.phoneIn(phone, "Chiama 045 765 4321"))
        assertFalse(TextEvidence.phoneIn(phone, "nessun numero"))
    }

    @Test
    fun `short or missing numbers prove nothing`() {
        assertFalse(TextEvidence.phoneIn(null, "123 4567"))
        assertFalse(TextEvidence.phoneIn("112", "chiama 112 subito"))
        assertFalse(TextEvidence.phoneIn("+39 045 1234567", "anno 2024 - 1234"))
    }

    @Test
    fun `an address needs the street and the number together`() {
        val tags = mapOf("addr:street" to "Via Mazzini", "addr:housenumber" to "12")
        assertTrue(TextEvidence.addressIn(tags, "Trattoria Verde, Via Mazzini 12, Verona"))
        assertTrue(TextEvidence.addressIn(tags, "12 Via Mazzini"))
        assertFalse(TextEvidence.addressIn(tags, "Via Mazzini 120"))
        assertFalse(TextEvidence.addressIn(tags, "Via Mazzini, Verona"))
        assertFalse(TextEvidence.addressIn(emptyMap(), "Via Mazzini 12"))
    }

    @Test
    fun `an openstreetmap link names its element`() {
        assertEquals(OsmRef(OsmElementType.NODE, 123), TextEvidence.osmRefOf(URI("https://www.openstreetmap.org/node/123")))
        assertEquals(OsmRef(OsmElementType.WAY, 9), TextEvidence.osmRefOf(URI("https://openstreetmap.org/way/9/history")))
        assertNull(TextEvidence.osmRefOf(URI("https://www.openstreetmap.org/user/someone")))
        assertNull(TextEvidence.osmRefOf(URI("https://example.org/node/123")))
        assertNull(TextEvidence.osmRefOf(URI("https://www.openstreetmap.org/node/abc")))
    }
}
