package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.OsmElementType
import app.roadstr.core.discovery.OsmRef
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.web.WebResult
import app.roadstr.core.geo.GeoPoint
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaceEntityResolverTest {
    private val center = GeoPoint(45.44, 10.99)
    private val context = ResolveContext(
        center = center,
        radiusMeters = 5_000.0,
        locality = "Verona",
        languageCode = "it",
        categories = setOf(PlaceCategory.RESTAURANT),
    )

    private fun place(
        id: Long,
        name: String,
        website: String? = null,
        phone: String? = null,
        tags: Map<String, String> = emptyMap(),
        category: PlaceCategory? = PlaceCategory.RESTAURANT,
        position: GeoPoint = GeoPoint(45.441, 10.991),
        sources: Set<PlaceSource> = setOf(PlaceSource.OPEN_STREET_MAP),
    ): RoadstrPlace {
        val osm = OsmRef(OsmElementType.NODE, id)
        return RoadstrPlace(
            id = RoadstrPlace.idFor(osm, name, position), osm = osm, name = name, category = category,
            position = position, address = null, distanceMeters = null, openingHours = null,
            phone = phone, website = website?.let(::URI), cuisine = null, tags = tags + ("name" to name),
            sources = sources,
        )
    }

    private fun result(
        rank: Int,
        title: String,
        url: String,
        snippet: String = "",
    ) = WebResult(
        title = title, url = URI(url), host = URI(url).host, snippet = snippet, engines = listOf("duckduckgo"), rank = rank,
    )

    private fun resolve(
        results: List<WebResult>,
        known: List<RoadstrPlace>,
        lookup: PlaceLookup? = null,
    ): List<ResolvedWebResult> = runBlocking { PlaceEntityResolver(lookup).resolve(results, known, context) }

    private val verde = place(1, "Trattoria Verde", website = "https://www.trattoriaverde.it")

    @Test
    fun `a result on the place's own website is linked and keeps all its sources`() {
        val row = resolve(
            listOf(result(1, "Trattoria Verde - Menu", "https://trattoriaverde.it/menu")),
            listOf(verde),
        ).single()
        assertEquals(MatchClass.LINKED, row.matchClass)
        val match = row.match!!
        assertEquals(verde, match.place)
        assertFalse(match.isNew)
        assertEquals(setOf(PlaceSource.OPEN_STREET_MAP, PlaceSource.SEARXNG, PlaceSource.WEBSITE), match.provenance)
        assertTrue(match.evidence.any { it.kind == EvidenceKind.WEBSITE_HOST })
    }

    @Test
    fun `the website alone is enough when the title says nothing about the name`() {
        val row = resolve(listOf(result(1, "Menu del giorno", "https://www.trattoriaverde.it/menu")), listOf(verde)).single()
        assertEquals(MatchClass.LINKED, row.matchClass)
    }

    @Test
    fun `a name alone is only a candidate and never merged`() {
        val row = resolve(listOf(result(1, "Trattoria Verde, Verona - Recensioni", "https://blog.example.org/post")), listOf(verde)).single()
        assertEquals(MatchClass.CANDIDATE, row.matchClass)
        assertEquals(verde, row.match!!.place)
        assertEquals(setOf(PlaceSource.OPEN_STREET_MAP, PlaceSource.SEARXNG), row.match!!.provenance)
    }

    @Test
    fun `a listing site with the right name is a candidate, never linked by its address`() {
        val row = resolve(
            listOf(result(1, "Trattoria Verde - Verona - Tripadvisor", "https://www.tripadvisor.it/Restaurant_Review-g1")),
            listOf(verde.copy(website = URI("https://www.tripadvisor.it/Restaurant_Review-g1"))),
        ).single()
        assertEquals(MatchClass.CANDIDATE, row.matchClass)
        assertTrue(row.match!!.evidence.none { it.kind == EvidenceKind.WEBSITE_HOST })
    }

    @Test
    fun `a chain with several branches is not linked by its brand or its shared site`() {
        val branches = listOf(
            place(10, "Pizza Roma", website = "https://www.pizzaroma.it", position = GeoPoint(45.441, 10.991)),
            place(11, "Pizza Roma", website = "https://www.pizzaroma.it", position = GeoPoint(45.46, 11.01)),
        )
        val row = resolve(listOf(result(1, "Pizza Roma - Menu", "https://www.pizzaroma.it/menu")), branches).single()
        assertEquals(MatchClass.WEB_ONLY, row.matchClass)
        assertNull(row.match)
    }

    @Test
    fun `a chain result is linked once the phone tells which branch`() {
        val branches = listOf(
            place(10, "Pizza Roma", website = "https://www.pizzaroma.it", phone = "+39 045 1112233"),
            place(11, "Pizza Roma", website = "https://www.pizzaroma.it", phone = "+39 045 9998877", position = GeoPoint(45.46, 11.01)),
        )
        val row = resolve(
            listOf(result(1, "Pizza Roma - Menu", "https://www.pizzaroma.it/menu", "Chiamaci allo 045 999 8877")),
            branches,
        ).single()
        assertEquals(MatchClass.LINKED, row.matchClass)
        assertEquals(11L, row.match!!.place.osm!!.id)
    }

    @Test
    fun `a phone number in the snippet links the result`() {
        val other = place(2, "Osteria Blu", phone = "+39 045 7654321")
        val row = resolve(
            listOf(result(1, "Osteria Blu", "https://blog.example.org/blu", "Prenota allo 045 765 4321")),
            listOf(other),
        ).single()
        assertEquals(MatchClass.LINKED, row.matchClass)
    }

    @Test
    fun `a street address in the snippet links the result`() {
        val place = place(3, "Bar Centrale", tags = mapOf("addr:street" to "Via Mazzini", "addr:housenumber" to "12"))
        val row = resolve(
            listOf(result(1, "Bar Centrale", "https://blog.example.org/bar", "Bar Centrale, Via Mazzini 12, Verona")),
            listOf(place),
        ).single()
        assertEquals(MatchClass.LINKED, row.matchClass)
    }

    @Test
    fun `an openstreetmap link is certain`() {
        val row = resolve(
            listOf(result(1, "Qualcosa", "https://www.openstreetmap.org/node/1")),
            listOf(verde),
        ).single()
        assertEquals(1.0, row.match!!.confidence, 0.0)
        assertEquals(MatchClass.LINKED, row.matchClass)
    }

    @Test
    fun `results about nothing known stay web results`() {
        val rows = resolve(
            listOf(result(1, "Ricetta della pizza margherita", "https://ricette.example.org/pizza")),
            listOf(verde),
        )
        assertEquals(MatchClass.WEB_ONLY, rows.single().matchClass)
    }

    @Test
    fun `order and count are preserved`() {
        val results = (1..4).map { result(it, "Pagina $it", "https://site$it.example.org/") }
        assertEquals(results, resolve(results, listOf(verde)).map { it.result })
    }

    // --- lookups -------------------------------------------------------------------------

    private class Finder(val answer: (String) -> List<RoadstrPlace> = { emptyList() }) : PlaceLookup {
        val names = mutableListOf<String>()
        var failWith: Exception? = null

        override suspend fun find(name: String, locality: String?, near: GeoPoint, languageCode: String): List<RoadstrPlace> {
            names += name
            failWith?.let { throw it }
            return answer(name)
        }
    }

    private val found = place(
        99, "Osteria Blu", website = "https://www.osteriablu.it",
        sources = setOf(PlaceSource.GEOCODER, PlaceSource.OPEN_STREET_MAP),
    )

    @Test
    fun `an unmatched result is looked up and linked when the website agrees`() {
        val finder = Finder { listOf(found) }
        val row = resolve(listOf(result(1, "Osteria Blu - Menu | Verona", "https://osteriablu.it/menu")), emptyList(), finder).single()
        assertEquals(listOf("Osteria Blu"), finder.names)
        assertEquals(MatchClass.LINKED, row.matchClass)
        assertTrue(row.match!!.isNew)
        assertTrue(PlaceSource.GEOCODER in row.match!!.provenance)
    }

    @Test
    fun `a looked up place that only agrees on category is a candidate`() {
        val finder = Finder { listOf(found.copy(website = null)) }
        val row = resolve(listOf(result(1, "Osteria Blu - Menu", "https://blog.example.org/blu")), emptyList(), finder).single()
        assertEquals(MatchClass.CANDIDATE, row.matchClass)
    }

    @Test
    fun `a looked up place that agrees on neither category nor website is refused`() {
        val finder = Finder { listOf(found.copy(website = null, category = PlaceCategory.PHARMACY)) }
        val row = resolve(listOf(result(1, "Osteria Blu - Menu", "https://blog.example.org/blu")), emptyList(), finder).single()
        assertEquals(MatchClass.WEB_ONLY, row.matchClass)
    }

    @Test
    fun `a similar name, a far place or a different name is refused`() {
        val far = found.copy(position = GeoPoint(46.5, 12.0))
        assertNull(resolve(listOf(result(1, "Osteria Blu", "https://osteriablu.it")), emptyList(), Finder { listOf(far) }).single().match)
        val other = found.copy(name = "Pizzeria Rossa")
        assertNull(resolve(listOf(result(1, "Osteria Blu", "https://osteriablu.it")), emptyList(), Finder { listOf(other) }).single().match)
    }

    @Test
    fun `at most three lookups are made and the same name only once`() {
        val finder = Finder()
        val results = (1..6).map { result(it, "Locale Numero $it - Menu", "https://l$it.example.org/") } +
            result(7, "Locale Numero 1 - Recensioni", "https://altro.example.org/")
        resolve(results, emptyList(), finder)
        assertEquals(3, finder.names.size)
        assertEquals(3, finder.names.toSet().size)
    }

    @Test
    fun `results already matched or without a name are not looked up`() {
        val finder = Finder()
        resolve(
            listOf(
                result(1, "Trattoria Verde - Menu", "https://trattoriaverde.it/menu"),
                result(2, "Menu - Home", "https://example.org/"),
            ),
            listOf(verde),
            finder,
        )
        assertTrue(finder.names.isEmpty())
    }

    @Test
    fun `a failing lookup leaves a web result and a cancelled one is not swallowed`() {
        val failing = Finder().also { it.failWith = IOException("down") }
        assertEquals(MatchClass.WEB_ONLY, resolve(listOf(result(1, "Osteria Blu", "https://x.example.org/")), emptyList(), failing).single().matchClass)

        val cancelled = Finder().also { it.failWith = CancellationException("stop") }
        try {
            resolve(listOf(result(1, "Osteria Blu", "https://x.example.org/")), emptyList(), cancelled)
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `without a lookup nothing is added`() {
        val row = resolve(listOf(result(1, "Osteria Blu - Menu", "https://x.example.org/")), emptyList()).single()
        assertNull(row.match)
        assertNotNull(row.result)
    }

    @Test
    fun `values never print what was searched`() {
        assertEquals("ResolveContext", context.toString())
        val match = resolve(listOf(result(1, "Trattoria Verde", "https://trattoriaverde.it")), listOf(verde)).single().match!!
        assertFalse(match.toString().contains("Verde"))
    }
}
