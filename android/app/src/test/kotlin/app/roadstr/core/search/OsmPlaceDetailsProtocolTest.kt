package app.roadstr.core.search

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmPlaceDetailsProtocolTest {
    @Test
    fun `extracts useful bounded POI information`() {
        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "amenity" to "restaurant",
                    "name" to "Trattoria Test",
                    "description" to "Cucina locale\ncon terrazza",
                    "cuisine" to "italian;pizza",
                    "operator" to "Cooperativa Test",
                    "wheelchair" to "limited",
                    "contact:phone" to "+39 0123 456789",
                    "contact:email" to "info@example.test",
                    "contact:website" to "https://example.test/menu",
                    "addr:street" to "Via Roma",
                    "addr:housenumber" to "7",
                    "addr:postcode" to "00100",
                    "addr:city" to "Roma",
                ),
            ),
        )

        assertEquals("Restaurant", details.category)
        assertEquals("Cucina locale con terrazza", details.description)
        assertEquals("Italian, Pizza", details.cuisine)
        assertEquals("limited", details.wheelchair)
        assertEquals("Via Roma 7, 00100 Roma", details.address)
        assertEquals(URI("https://example.test/menu"), details.website)
    }

    @Test
    fun `rejects unsafe contact data and non POI tag sets`() {
        assertNull(OsmPlaceDetailsProtocol.parse(mapOf("name" to "Just a name")))

        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "shop" to "books",
                    "website" to "http://example.test/",
                    "email" to "not an email",
                    "wheelchair" to "sometimes",
                ),
            ),
        )
        assertNull(details.website)
        assertNull(details.email)
        assertNull(details.wheelchair)
    }

    @Test
    fun `prefers localized names and caps untrusted text`() {
        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "tourism" to "attraction",
                    "name" to "English name",
                    "name:it" to "Nome italiano",
                    "description" to "x".repeat(700),
                ),
                languageCode = "it",
            ),
        )

        assertEquals("Nome italiano", details.name)
        assertEquals(501, details.description?.length)
        assertTrue(requireNotNull(details.description).endsWith("…"))
    }

    @Test
    fun `extracts payments and restricted access`() {
        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "shop" to "bakery",
                    "payment:bitcoin" to "yes",
                    "payment:lightning" to "accepted",
                    "access" to "customers",
                ),
            ),
        )

        assertTrue(details.acceptsBitcoin)
        assertTrue(details.acceptsLightning)
        assertEquals("customers", details.access)
    }

    @Test
    fun `extracts parking details only in parking context`() {
        val parking = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "amenity" to "parking",
                    "parking" to "underground",
                    "fee" to "yes",
                    "charge" to "2 EUR/hour",
                    "capacity" to "240",
                    "maxstay" to "3 hours",
                ),
            ),
        )
        assertEquals(OsmPlaceKind.Parking, parking.kind)
        assertEquals("underground", parking.parkingType)
        assertEquals("yes", parking.fee)
        assertEquals("2 EUR/hour", parking.charge)
        assertEquals(240, parking.capacity)
        assertEquals("3 hours", parking.maxStay)

        val shop = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "shop" to "mall",
                    "fee" to "yes",
                    "capacity" to "999",
                    "parking" to "surface",
                ),
            ),
        )
        assertEquals(OsmPlaceKind.Other, shop.kind)
        assertNull(shop.fee)
        assertNull(shop.capacity)
        assertNull(shop.parkingType)
    }

    @Test
    fun `extracts EV connector counts and output safely`() {
        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "amenity" to "charging_station",
                    "socket:type2" to "4",
                    "socket:type2:output" to "22 kW",
                    "socket:chademo" to "yes",
                    "socket:chademo:output" to "50 kW",
                    "socket:type2_combo" to "no",
                    "capacity" to "6",
                    "fee" to "no",
                ),
            ),
        )

        assertEquals(OsmPlaceKind.ChargingStation, details.kind)
        assertEquals(6, details.capacity)
        assertEquals(2, details.evConnectors.size)
        assertEquals(OsmEvConnector("type2", 4, "22 kW"), details.evConnectors.first())
        assertEquals(OsmEvConnector("chademo", null, "50 kW"), details.evConnectors.last())
    }

    @Test
    fun `extracts contextual fuel lodging and food amenities`() {
        val fuel = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf("amenity" to "fuel", "fuel:diesel" to "yes", "fuel:octane_95" to "yes"),
            ),
        )
        assertEquals(setOf("diesel", "octane_95"), fuel.fuels)

        val hotel = requireNotNull(
            OsmPlaceDetailsProtocol.parse(mapOf("tourism" to "hotel", "stars" to "4s")),
        )
        assertEquals(OsmPlaceKind.Lodging, hotel.kind)
        assertEquals("4S", hotel.stars)

        val cafe = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "amenity" to "cafe",
                    "smoking" to "outside",
                    "outdoor_seating" to "yes",
                    "takeaway" to "only",
                ),
            ),
        )
        assertEquals(OsmPlaceKind.FoodAndDrink, cafe.kind)
        assertEquals("outside", cafe.smoking)
        assertEquals("yes", cafe.outdoorSeating)
        assertEquals("only", cafe.takeaway)
    }

    @Test
    fun `rejects malformed counts unknown access and unsupported sockets`() {
        val details = requireNotNull(
            OsmPlaceDetailsProtocol.parse(
                mapOf(
                    "amenity" to "charging_station",
                    "access" to "definitely_not_a_real_value",
                    "capacity" to "-4",
                    "socket:type2" to "many",
                    "socket:chademo" to "0",
                    "socket:type2_combo" to "000",
                ),
            ),
        )

        assertNull(details.access)
        assertNull(details.capacity)
        assertTrue(details.evConnectors.isEmpty())
        assertFalse(details.acceptsBitcoin)
    }
}
