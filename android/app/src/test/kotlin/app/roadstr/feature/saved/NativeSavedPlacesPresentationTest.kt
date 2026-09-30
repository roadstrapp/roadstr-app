package app.roadstr.feature.saved

import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeMapPointOverlayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSavedPlacesPresentationTest {
    @Test
    fun `legacy favorite storage round trips the Flutter list-of-strings shape`() {
        val value = favorite(" Casa 😀 ", " Via \"Roma\" ", 45.46, 9.19)

        val encoded = NativeSavedPlacesProtocol.encodeStoredFavorites(listOf(value))
        val decoded = NativeSavedPlacesProtocol.decodeStoredFavorites(encoded)

        assertTrue(encoded.startsWith("[\"{\\\"label\\\":"))
        assertEquals("Casa 😀", decoded.single().label)
        assertEquals("Via \"Roma\"", decoded.single().address)
        assertEquals(45.46, decoded.single().point.latitude, 0.0)
    }

    @Test
    fun `legacy decoder skips malformed entries and enforces Flutter bounds`() {
        val valid = "{\"label\":\"Home\",\"address\":\"Street\",\"lat\":45.0,\"lon\":7.0}"
        val invalidPoint = "{\"label\":\"Bad\",\"lat\":91.0,\"lon\":7.0}"
        val oversized = "{\"label\":\"${"x".repeat(201)}\",\"lat\":1.0,\"lon\":1.0}"
        val raw = listOf(valid, invalidPoint, oversized, "not-json").joinToString(
            prefix = "[\"",
            postfix = "\"]",
            separator = "\",\"",
        ) { it.replace("\\", "\\\\").replace("\"", "\\\"") }

        val decoded = NativeSavedPlacesProtocol.decodeStoredFavorites(raw)

        assertEquals(listOf("Home"), decoded.map { it.label })
        assertTrue(NativeSavedPlacesProtocol.decodeStoredFavorites(null).isEmpty())
    }

    @Test
    fun `stored and import work are capped at one thousand items`() {
        val values = List(NativeSavedPlacesProtocol.MAX_STORED_ITEMS + 5) { index ->
            favorite("Place $index", latitude = 40.0, longitude = 12.0)
        }

        val stored = NativeSavedPlacesProtocol.decodeStoredFavorites(
            NativeSavedPlacesProtocol.encodeStoredFavorites(values),
        )
        val imported = NativeSavedPlacesProtocol.decodeImportPlaintext(
            NativeSavedPlacesProtocol.encodeImportPlaintext(values),
        )

        assertEquals(NativeSavedPlacesProtocol.MAX_STORED_ITEMS, stored.size)
        assertEquals(NativeSavedPlacesProtocol.MAX_STORED_ITEMS, imported.size)
    }

    @Test
    fun `import plaintext accepts direct maps and rejects invalid content`() {
        val raw = """[
            {"label":" Home ","address":" Main road ","lat":45,"lon":7},
            {"label":"","address":"Bad","lat":1,"lon":1},
            "wrong-shape"
        ]""".trimIndent()

        val decoded = NativeSavedPlacesProtocol.decodeImportPlaintext(raw)

        assertEquals(1, decoded.size)
        assertEquals("Home", decoded.single().label)
        assertTrue(NativeSavedPlacesProtocol.decodeImportPlaintext("{}").isEmpty())
        assertTrue(
            NativeSavedPlacesProtocol.decodeImportPlaintext(
                "x".repeat(NativeSavedPlacesProtocol.MAX_IMPORT_BYTES + 1),
            ).isEmpty(),
        )
    }

    @Test
    fun `import envelope distinguishes plaintext and bounded encrypted payloads`() {
        val plain = NativeSavedPlacesProtocol.decodeImportEnvelope(
            """{"v":1,"encrypted":false,"data":"[]"}""",
        )
        val encrypted = NativeSavedPlacesProtocol.decodeImportEnvelope(
            """{"v":1,"encrypted":true,"iterations":600000,"salt":"AA==","iv":"AA==","ciphertext":"AA=="}""",
        )

        assertEquals("[]", plain?.plaintext)
        assertFalse(requireNotNull(plain).encrypted)
        assertTrue(requireNotNull(encrypted).encrypted)
        assertEquals(600_000L, encrypted.encryptedFields?.get("iterations"))
        assertNull(
            NativeSavedPlacesProtocol.decodeImportEnvelope(
                """{"encrypted":true,"iterations":2000000,"salt":"AA==","iv":"AA==","ciphertext":"AA=="}""",
            ),
        )
    }

    @Test
    fun `merge replaces exact labels appends new labels and respects the cap`() {
        val current = listOf(favorite("Home", "Old"), favorite("home", "Lower"))
        val incoming = listOf(favorite("Home", "New"), favorite("Office", "Work"))

        val merged = NativeSavedPlacesProtocol.mergeByLabel(current, incoming)

        assertEquals(listOf("Home", "home", "Office"), merged.map { it.label })
        assertEquals("New", merged.first().address)
        val full = List(NativeSavedPlacesProtocol.MAX_STORED_ITEMS) { favorite("P$it") }
        assertEquals(
            NativeSavedPlacesProtocol.MAX_STORED_ITEMS,
            NativeSavedPlacesProtocol.mergeByLabel(full, listOf(favorite("Extra"))).size,
        )
    }

    @Test
    fun `parking storage preserves coordinates and optional timestamp`() {
        val parking = NativeParkingPosition(NativeMapPoint(45.0703, 7.6869), 1_700_000_000_000)

        val encoded = NativeSavedPlacesProtocol.encodeParking(parking)
        val decoded = NativeSavedPlacesProtocol.decodeParking(encoded)

        assertEquals(parking, decoded)
        assertEquals(
            NativeParkingPosition(NativeMapPoint(1.0, 2.0), null),
            NativeSavedPlacesProtocol.decodeParking("""{"lat":1,"lon":2}"""),
        )
        assertNull(NativeSavedPlacesProtocol.decodeParking("""{"lat":91,"lon":2}"""))
    }

    @Test
    fun `saved parking projects to the existing native parking marker`() {
        val parking = NativeParkingPosition(NativeMapPoint(45.0, 7.0), 10)

        val marker = NativeSavedPlacesProtocol.parkingMarker(parking)

        assertEquals(NativeSavedPlacesProtocol.PARKING_MARKER_ID, marker.id)
        assertEquals(NativeMapPointOverlayKind.Parking, marker.kind)
        assertEquals(parking.point, marker.point)
    }

    @Test
    fun `session show validates bounds preserves duplicates and fences revisions`() {
        val session = NativeSavedPlacesSession()
        val duplicates = listOf(favorite("Home"), favorite("Home", "Second"))

        assertTrue(session.show(3, duplicates, null))
        assertEquals(2, session.state.value.favorites.size)
        assertFalse(session.show(3, emptyList(), null))
        assertFalse(session.show(2, emptyList(), null))
        assertEquals(NativeSavedPlacesStatus.Ready, session.state.value.status)
    }

    @Test
    fun `session add edit and delete stay within the active revision`() {
        val session = NativeSavedPlacesSession()
        assertTrue(session.show(1, listOf(favorite("Home")), null))

        assertTrue(session.upsert(1, favorite("Office")))
        assertTrue(session.upsert(1, favorite("Office", "Updated"), index = 1))
        assertEquals("Updated", session.state.value.favorites[1].address)
        assertTrue(session.delete(1, 0))
        assertEquals(listOf("Office"), session.state.value.favorites.map { it.label })
        assertFalse(session.delete(0, 0))
        assertFalse(session.upsert(1, favorite("Bad"), index = 9))
    }

    @Test
    fun `session import reports valid count and follows Flutter label merge`() {
        val session = NativeSavedPlacesSession()
        assertTrue(session.show(7, listOf(favorite("Home", "Old")), null))

        assertTrue(
            session.mergeImported(
                7,
                listOf(favorite("Home", "New"), favorite("Office", "Work")),
            ),
        )

        assertEquals(2, session.state.value.lastImportedCount)
        assertEquals(listOf("New", "Work"), session.state.value.favorites.map { it.address })
        assertFalse(session.mergeImported(6, listOf(favorite("Late"))))
    }

    @Test
    fun `session parking and hide clear data and reject stale callbacks`() {
        val session = NativeSavedPlacesSession()
        val parking = NativeParkingPosition(NativeMapPoint(45.0, 7.0), 123)
        assertTrue(session.show(5, emptyList(), null))

        assertTrue(session.setParking(5, parking))
        assertEquals(parking, session.state.value.parking)
        assertTrue(session.clearParking(5))
        assertFalse(session.clearParking(5))
        assertTrue(session.hide(5))
        assertEquals(NativeSavedPlacesStatus.Hidden, session.state.value.status)
        assertFalse(session.upsert(5, favorite("Late")))
        assertFalse(session.hide(4))
    }

    private fun favorite(
        label: String,
        address: String = "Address",
        latitude: Double = 45.0,
        longitude: Double = 7.0,
    ) = NativeSavedPlace(label, address, NativeMapPoint(latitude, longitude))
}
