package app.roadstr.service.location

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLocationServiceTest {
    private val services = mutableListOf<NativeLocationService>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = runBlocking {
        services.forEach { service -> service.dispose() }
        scopes.forEach { scope -> scope.cancel() }
    }

    @Test
    fun `policy converts speed and marks fixes under thirty metres reliable`() {
        val fix = NativeLocationPolicy.normalize(
            raw(
                speedMetersPerSecond = 10.0,
                accuracyMeters = 29.99,
                bearingDegrees = 91.0,
            ),
        )

        assertEquals(36.0, fix!!.speedKilometresPerHour, 0.0)
        assertEquals(91.0, fix.headingDegrees!!, 0.0)
        assertTrue(fix.isReliable)
    }

    @Test
    fun `policy rejects invalid coordinates and sanitises non-finite measurements`() {
        assertNull(NativeLocationPolicy.normalize(raw(latitude = Double.NaN)))
        assertNull(NativeLocationPolicy.normalize(raw(longitude = 181.0)))

        val fix = NativeLocationPolicy.normalize(
            raw(
                speedMetersPerSecond = Double.NaN,
                accuracyMeters = Double.NaN,
                bearingDegrees = -1.0,
                altitudeMeters = Double.POSITIVE_INFINITY,
            ),
        )!!

        assertEquals(0.0, fix.speedKilometresPerHour, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, fix.accuracyMeters, 0.0)
        assertNull(fix.headingDegrees)
        assertEquals(0.0, fix.altitudeMeters, 0.0)
        assertFalse(fix.isReliable)
    }

    @Test
    fun `start refuses disabled source without opening a listener`() = runBlocking {
        val source = FakeLocationSource(enabled = false)
        val service = service(source)

        assertFalse(service.start())
        assertFalse(service.isRunning)
        assertEquals(0, source.startCalls)
    }

    @Test
    fun `start is idempotent and stream fixes reach the callback`() = runBlocking {
        val source = FakeLocationSource()
        val fixes = mutableListOf<NativeLocationFix>()
        val service = service(source, fixes::add)

        assertTrue(service.start())
        assertTrue(service.start())
        source.emit(raw(speedMetersPerSecond = 5.0))

        assertEquals(1, source.startCalls)
        assertEquals(1, fixes.size)
        assertEquals(18.0, fixes.single().speedKilometresPerHour, 0.0)
        assertTrue(service.isRunning)
    }

    @Test
    fun `last known fix is safe and never reports stale speed`() = runBlocking {
        val source = FakeLocationSource(
            lastKnownValue = raw(
                speedMetersPerSecond = 20.0,
                bearingDegrees = -1.0,
                altitudeMeters = Double.NaN,
            ),
        )
        val service = service(source)

        val fix = service.lastKnown()

        assertEquals(0.0, fix!!.speedKilometresPerHour, 0.0)
        assertNull(fix.headingDegrees)
        assertEquals(0.0, fix.altitudeMeters, 0.0)
    }

    @Test
    fun `dead stream restarts once after the configured stale threshold`() = runBlocking {
        val source = FakeLocationSource()
        val clock = MutableLocationClock(0L)
        val service = service(source, clock = clock)
        assertTrue(service.start())

        clock.now = NativeLocationService.STALE_AFTER_MILLIS
        assertTrue(service.checkWatchdogNow())

        assertEquals(2, source.startCalls)
        assertEquals(1, source.stopCalls)
        assertTrue(service.isRunning)
    }

    @Test
    fun `fresh callback prevents watchdog restart`() = runBlocking {
        val source = FakeLocationSource()
        val clock = MutableLocationClock(0L)
        val service = service(source, clock = clock)
        assertTrue(service.start())

        clock.now = 30_000L
        source.emit(raw())
        clock.now = 30_000L + NativeLocationService.STALE_AFTER_MILLIS - 1

        assertFalse(service.checkWatchdogNow())
        assertEquals(1, source.startCalls)
    }

    @Test
    fun `watchdog does not restart while location service is disabled`() = runBlocking {
        val source = FakeLocationSource()
        val clock = MutableLocationClock(0L)
        val service = service(source, clock = clock)
        assertTrue(service.start())

        source.enabled = false
        clock.now = NativeLocationService.STALE_AFTER_MILLIS

        assertFalse(service.checkWatchdogNow())
        assertEquals(1, source.startCalls)
        assertTrue(service.isRunning)
    }

    @Test
    fun `callback failure does not kill the active source`() = runBlocking {
        val source = FakeLocationSource()
        val delivered = AtomicInteger()
        val service = service(
            source,
            onFix = {
                delivered.incrementAndGet()
                error("stale callback")
            },
        )
        assertTrue(service.start())

        source.emit(raw())
        source.emit(raw(latitude = 45.1))

        assertEquals(2, delivered.get())
        assertTrue(service.isRunning)
        assertEquals(1, source.startCalls)
    }

    @Test
    fun `stop detaches callbacks and dispose makes restart impossible`() = runBlocking {
        val source = FakeLocationSource()
        val fixes = mutableListOf<NativeLocationFix>()
        val service = service(source, fixes::add)
        assertTrue(service.start())
        source.emit(raw())

        service.stop()
        source.emit(raw(latitude = 45.2))
        assertFalse(service.isRunning)
        assertEquals(1, fixes.size)
        assertEquals(1, source.stopCalls)

        service.dispose()
        assertFalse(service.start())
        assertEquals(1, source.stopCalls)
    }

    @Test
    fun `cancelling a source start resets ownership and propagates cancellation`() = runBlocking {
        val source = BlockingStartLocationSource()
        val service = service(source)
        val started = CompletableDeferred<Unit>()
        val job = launch {
            started.complete(Unit)
            service.start()
        }
        started.await()
        source.startEntered.await()

        job.cancelAndJoin()

        assertFalse(service.isRunning)
        assertEquals(1, source.startCalls)
    }

    private fun service(
        source: NativeLocationSource,
        onFix: (NativeLocationFix) -> Unit = {},
        clock: NativeLocationClock = MutableLocationClock(0L),
    ): NativeLocationService = NativeLocationService(
        source = source,
        onFix = onFix,
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()).also(scopes::add),
        clock = clock,
        watchdogIntervalMillis = Long.MAX_VALUE,
    ).also(services::add)

    private fun raw(
        latitude: Double = 45.0,
        longitude: Double = 9.0,
        speedMetersPerSecond: Double = 0.0,
        accuracyMeters: Double = 5.0,
        bearingDegrees: Double = 90.0,
        altitudeMeters: Double = 100.0,
        timestampMillis: Long = 1_700_000_000_000L,
    ): NativeRawLocation = NativeRawLocation(
        latitude = latitude,
        longitude = longitude,
        speedMetersPerSecond = speedMetersPerSecond,
        accuracyMeters = accuracyMeters,
        bearingDegrees = bearingDegrees,
        altitudeMeters = altitudeMeters,
        timestampMillis = timestampMillis,
    )

    private class MutableLocationClock(var now: Long) : NativeLocationClock {
        override fun nowMillis(): Long = now
    }

    private class FakeLocationSource(
        var enabled: Boolean = true,
        private val startResult: Boolean = true,
        var lastKnownValue: NativeRawLocation? = null,
    ) : NativeLocationSource {
        var startCalls = 0
        var stopCalls = 0
        private var listener: ((NativeRawLocation) -> Unit)? = null

        override suspend fun isLocationEnabled(): Boolean = enabled

        override suspend fun start(onLocation: (NativeRawLocation) -> Unit): Boolean {
            startCalls++
            listener = onLocation
            return startResult
        }

        override suspend fun stop() {
            stopCalls++
            listener = null
        }

        override suspend fun lastKnown(): NativeRawLocation? = lastKnownValue

        fun emit(value: NativeRawLocation) {
            listener?.invoke(value)
        }
    }

    private class BlockingStartLocationSource : NativeLocationSource {
        val startEntered = CompletableDeferred<Unit>()
        var startCalls = 0

        override suspend fun isLocationEnabled(): Boolean = true

        override suspend fun start(onLocation: (NativeRawLocation) -> Unit): Boolean {
            startCalls++
            startEntered.complete(Unit)
            awaitCancellation()
        }

        override suspend fun stop() = Unit

        override suspend fun lastKnown(): NativeRawLocation? = null
    }
}

class NativeLocationCachedFixTest {
    private fun fix(
        time: Long,
        accuracy: Double,
        provider: String?,
    ) = NativeRawLocation(
        latitude = 38.7,
        longitude = -9.1,
        speedMetersPerSecond = 0.0,
        accuracyMeters = accuracy,
        bearingDegrees = -1.0,
        altitudeMeters = 0.0,
        timestampMillis = time,
        provider = provider,
    )

    @Test
    fun `any fix beats nothing`() {
        org.junit.Assert.assertTrue(
            NativeLocationPolicy.isBetterCachedFix(fix(1_000, 900.0, "network"), null),
        )
    }

    @Test
    fun `a fix over two minutes newer wins even when much less accurate`() {
        val gps = fix(0, 5.0, "gps")
        val network = fix(3 * 60_000L, 900.0, "network")
        org.junit.Assert.assertTrue(NativeLocationPolicy.isBetterCachedFix(network, gps))
        org.junit.Assert.assertFalse(NativeLocationPolicy.isBetterCachedFix(gps, network))
    }

    @Test
    fun `within two minutes the more accurate fix wins`() {
        val gps = fix(0, 8.0, "gps")
        val network = fix(30_000L, 800.0, "network")
        org.junit.Assert.assertFalse(NativeLocationPolicy.isBetterCachedFix(network, gps))
        org.junit.Assert.assertTrue(
            NativeLocationPolicy.isBetterCachedFix(fix(0, 8.0, "gps"), network),
        )
    }

    @Test
    fun `a newer fix from the same provider survives modest accuracy loss`() {
        val older = fix(0, 20.0, "gps")
        org.junit.Assert.assertTrue(
            NativeLocationPolicy.isBetterCachedFix(fix(10_000L, 150.0, "gps"), older),
        )
        org.junit.Assert.assertFalse(
            NativeLocationPolicy.isBetterCachedFix(fix(10_000L, 150.0, "network"), older),
        )
    }
}
