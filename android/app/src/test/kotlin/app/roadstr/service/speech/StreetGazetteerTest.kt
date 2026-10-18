package app.roadstr.service.speech

import app.roadstr.core.geo.GeoPoint
import app.roadstr.service.hazards.NativeOverpassClient
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.File
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreetGazetteerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val here = GeoPoint(44.4949, 11.3426)
    private var now = 1_000_000_000_000L

    private class Server(vararg replies: (String) -> Pair<Int, String>) {
        val answers = replies.toList()
        val calls = mutableListOf<Pair<String, String>>()
        val transport = NativeSearchHttpTransport { request, _ ->
            val query = URLDecoder.decode(request.body.orEmpty().removePrefix("data="), "UTF-8")
            calls += request.uri to query
            val (status, body) = (answers.getOrNull(calls.size - 1) ?: answers.last())(request.uri)
            NativeHttpResponse(status, "", emptyMap(), body.toByteArray())
        }
    }

    private fun gazetteer(server: Server, mirrors: List<String> = listOf("https://a.test/api", "https://b.test/api"), directory: File = File(folder.root, "names")) =
        StreetGazetteer(NativeOverpassClient(server.transport, mirrors), directory, clock = { now })

    private val csv = "Via Darsena\nVia Chiavica Romea\nVia Darsena\nVia Alberoni\n"

    @Test
    fun parsesOneNamePerLineWithoutDuplicatesOrRubbish() {
        val names = StreetGazetteer.parse("Via Roma\nVia Roma\n\nA\nVia\tIgnored\u0000\nViale Italia\nVia con\u0007controllo\n  Via Garibaldi  \n" + "x".repeat(200))
        assertEquals(listOf("Via Roma", "Via", "Viale Italia", "Via Garibaldi").filter { it.length >= 4 }, names)
    }

    @Test
    fun theRequestNamesTheCentreOfTheCellNeverTheExactPosition() = runBlocking {
        val server = Server({ 200 to csv })
        gazetteer(server).load(here)
        val query = server.calls.single().second
        assertFalse(query.contains("44.4949"))
        assertFalse(query.contains("11.3426"))
        assertTrue(query, query.contains("(around:7000,44.4750000,11.3250000)"))
        assertTrue(query.startsWith("[out:csv(\"name\";false)]"))
        assertTrue(query.contains("[\"highway\"][\"name\"]"))
    }

    @Test
    fun theCellCoversEverythingWithinAFewKilometres() {
        val g = gazetteer(Server({ 200 to csv }))
        val cell = g.cellOf(here)
        val centre = cell.centre
        assertEquals(889, cell.latitudeIndex)
        assertEquals(226, cell.longitudeIndex)
        // Any point of the cell is within 4.5 km of its centre, so inside the 7 km radius with 2.5 km to spare.
        val corner = GeoPoint(cell.latitudeIndex * StreetGazetteer.CELL_DEGREES, cell.longitudeIndex * StreetGazetteer.CELL_DEGREES)
        val metres = app.roadstr.core.geo.GeoMath.distanceMeters(centre, corner)
        assertTrue("corner is $metres m from the centre", metres < 4_500)
    }

    @Test
    fun anAreaIsFetchedOnceAndThenServedFromMemoryAndDisk() = runBlocking {
        val server = Server({ 200 to csv })
        val first = gazetteer(server)
        val index = first.load(here)
        assertNotNull(index)
        assertEquals(3, index!!.size)
        assertSame(index, first.load(GeoPoint(44.48, 11.33)))
        assertEquals(1, server.calls.size)

        // A new process: nothing in memory, the file on disk answers, and the network is not touched.
        val dead = Server({ 500 to "" })
        val second = gazetteer(dead)
        assertNotNull(second.cached(here))
        assertEquals(3, second.load(here)!!.size)
        assertTrue(dead.calls.isEmpty())
    }

    private fun assertSame(a: Any?, b: Any?) = assertTrue(a === b)

    @Test
    fun aMonthOldAnswerIsFetchedAgain() = runBlocking {
        val server = Server({ 200 to csv })
        val g = gazetteer(server)
        g.load(here)
        now += 31L * 24 * 60 * 60 * 1000
        val fresh = gazetteer(server)
        assertNull(fresh.cached(here))
        assertNotNull(fresh.load(here))
        assertEquals(2, server.calls.size)
    }

    @Test
    fun differentAreasAreDifferentFiles() = runBlocking {
        val server = Server({ 200 to csv })
        val g = gazetteer(server)
        g.load(here)
        g.load(GeoPoint(45.5, 9.2))
        assertEquals(2, server.calls.size)
        assertEquals(2, File(folder.root, "names").listFiles()!!.size)
    }

    @Test
    fun aDeadMirrorIsSkippedAndTheNextOneAnswers() = runBlocking {
        val server = Server({ 500 to "<html>" }, { 200 to csv })
        val index = gazetteer(server).load(here)
        assertEquals(3, index!!.size)
        assertEquals(listOf("https://a.test/api", "https://b.test/api"), server.calls.map { it.first })
    }

    @Test
    fun whenEveryMirrorFailsThereIsNoIndexAndNoHammering() = runBlocking {
        val server = Server({ 503 to "" })
        val g = gazetteer(server)
        assertNull(g.load(here))
        val afterFirst = server.calls.size
        assertEquals(2, afterFirst)
        // Asking again at once does not go back to the servers that just said no.
        assertNull(g.load(here))
        assertEquals(afterFirst, server.calls.size)
        now += 10 * 60 * 1000L
        assertNull(g.load(here))
        assertTrue(server.calls.size > afterFirst)
    }

    @Test
    fun anEmptyAnswerIsNotKeptAsIfItWereTheAreaWithoutStreets() = runBlocking {
        val server = Server({ 200 to "" })
        val g = gazetteer(server)
        assertEquals(0, g.load(here)!!.size)
        assertFalse(File(folder.root, "names").exists() && File(folder.root, "names").listFiles()!!.isNotEmpty())
    }

    @Test
    fun oldFilesArePrunedSoTheCacheStaysSmall() = runBlocking {
        val server = Server({ 200 to csv })
        val g = gazetteer(server)
        for (step in 0 until 15) {
            g.load(GeoPoint(40.0 + step * 0.1, 10.0))
            File(folder.root, "names").listFiles()!!.maxByOrNull { it.lastModified() }!!.setLastModified(1_000L + step * 1_000L)
        }
        assertTrue(File(folder.root, "names").listFiles()!!.size <= 12)
    }
}
