package app.roadstr.service.speech

import app.roadstr.core.geo.GeoPoint
import app.roadstr.service.hazards.NativeOverpassClient
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddressSpeechRefinerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val here = GeoPoint(44.4949, 11.3426)
    private val csv = "Via Darsena\nVia Chiavica Romea\nVia Alberoni\n"

    @After
    fun tearDown() = scope.cancel()

    private fun refiner(delayMillis: Long = 0, point: GeoPoint? = here, waitMillis: Long = 2_000, calls: AtomicInteger = AtomicInteger()): AddressSpeechRefiner {
        val transport = NativeSearchHttpTransport { _, _ ->
            calls.incrementAndGet()
            if (delayMillis > 0) delay(delayMillis)
            NativeHttpResponse(200, "", emptyMap(), csv.toByteArray())
        }
        val gazetteer = StreetGazetteer(NativeOverpassClient(transport, listOf("https://a.test/api")), File(folder.root, "names"))
        return AddressSpeechRefiner(gazetteer, { point }, scope, waitMillis)
    }

    @Test
    fun theStreetNamesArrivedByTheTimeTheUserStoppedTalking() = runBlocking {
        val refiner = refiner()
        refiner.prepare("it-IT")
        assertEquals("Via Chiavica Romea 5", refiner.refine("via chiavi caromea cinque", "it-IT"))
        assertEquals("Via Darsena 17", refiner.refine("via d'assena diciassette", "it-IT"))
    }

    @Test
    fun aSlowServerCostsTheStreetNamesButNotTheHouseNumber() = runBlocking {
        val calls = AtomicInteger()
        val refiner = refiner(delayMillis = 1_500, waitMillis = 100, calls = calls)
        refiner.prepare("it-IT")
        val started = System.nanoTime()
        assertEquals("via d'assena 17", refiner.refine("via d'assena diciassette", "it-IT"))
        assertTrue("waited ${(System.nanoTime() - started) / 1_000_000} ms", (System.nanoTime() - started) < 1_000_000_000L)
        // The fetch carries on, so the next search has the names.
        delay(2_000)
        refiner.prepare("it-IT")
        assertEquals("Via Darsena 17", refiner.refine("via d'assena diciassette", "it-IT"))
        assertEquals(1, calls.get())
    }

    @Test
    fun withoutAPositionOnlyTheNumbersAreFixed() = runBlocking {
        val calls = AtomicInteger()
        val refiner = refiner(point = null, calls = calls)
        refiner.prepare("it")
        assertEquals("via d'assena 17", refiner.refine("via d'assena diciassette", "it"))
        assertEquals(0, calls.get())
    }

    @Test
    fun otherLanguagesKeepTheirNumbers() = runBlocking {
        val refiner = refiner()
        refiner.prepare("de-DE")
        assertEquals("Via Darsena diciassette", refiner.refine("via d'arsena diciassette", "de-DE"))
    }

    @Test
    fun refiningWithoutPreparingStillWorks() = runBlocking {
        assertEquals("via roma 3", refiner().refine("via roma tre", "it"))
    }
}
