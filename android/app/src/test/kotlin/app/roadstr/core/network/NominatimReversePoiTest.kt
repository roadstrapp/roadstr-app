package app.roadstr.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NominatimReversePoiTest {
    @Test
    fun `a plain address has a wiki query from its neighbourhood but no place name`() {
        val detail = SearchResponseProtocol.parseNominatimReverse(
            """{"display_name":"3, Rua Augusta, Baixa, Lisboa","name":"",
                "address":{"house_number":"3","road":"Rua Augusta","suburb":"Baixa","city":"Lisboa"}}""",
        )

        assertNotNull(detail)
        assertEquals("Baixa Lisboa", detail!!.wikiQuery)
        assertNull(detail.poiName)
    }

    @Test
    fun `a named place is reported as a place`() {
        val detail = SearchResponseProtocol.parseNominatimReverse(
            """{"display_name":"Castelo de São Jorge, Lisboa","name":"Castelo de São Jorge",
                "address":{"historic":"Castelo de São Jorge","suburb":"Santa Maria Maior","city":"Lisboa"},
                "extratags":{"opening_hours":"Mo-Su 09:00-21:00"}}""",
        )

        assertEquals("Castelo de São Jorge", detail!!.poiName)
        assertEquals("Castelo de São Jorge Lisboa", detail.wikiQuery)
        assertEquals("Mo-Su 09:00-21:00", detail.openingHours)
    }

    @Test
    fun `a numeric name is not a place`() {
        val detail = SearchResponseProtocol.parseNominatimReverse(
            """{"display_name":"12, Rua Augusta, Lisboa","name":"12","address":{"road":"Rua Augusta","city":"Lisboa"}}""",
        )

        assertNull(detail!!.poiName)
    }
}
