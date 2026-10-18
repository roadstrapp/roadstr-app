package app.roadstr.service.speech

import app.roadstr.core.speech.SpeechModelCatalog
import app.roadstr.core.speech.SpeechOutcome
import app.roadstr.core.speech.SpeechUnavailableReason
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechRecognitionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // ── fakes ─────────────────────────────────────────────────────────────────

    private fun noise(millis: Int, seed: Long = 7): ShortArray {
        var state = seed
        return ShortArray(16 * millis) {
            state = state * 6364136223846793005L + 1442695040888963407L
            ((((state ushr 33).toInt() % 2001) / 1000.0 - 1.0) * 0.004 * 32767).toInt().toShort()
        }
    }

    private fun speech(millis: Int) = ShortArray(16 * millis) {
        val t = it / 16000.0
        ((sin(2 * PI * 180 * t) * 0.6 + sin(2 * PI * 540 * t) * 0.4) * 0.25 * 32767).toInt().toShort()
    }

    private class FakeSource(
        private val audio: ShortArray,
        private val startFailure: Exception? = null,
        private val endlessSilence: Boolean = false,
    ) : PcmSource {
        val closed = AtomicBoolean(false)
        private var at = 0

        override fun start() {
            startFailure?.let { throw it }
        }

        override fun read(buffer: ShortArray): Int {
            if (at >= audio.size) {
                if (!endlessSilence) return -1
                Thread.sleep(2)
                buffer.fill(0)
                return buffer.size
            }
            val count = minOf(buffer.size, audio.size - at)
            System.arraycopy(audio, at, buffer, 0, count)
            at += count
            return count
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class FakeEngine(val text: String, val onTranscribe: (FloatArray) -> Unit = {}) : SpeechEngine {
        val closed = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        override fun transcribe(pcm: FloatArray): String {
            onTranscribe(pcm)
            return text
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class FakeFactory(
        private val text: String = "Via Giuseppe Garibaldi, Bologna.",
        private val failure: SpeechUnavailable? = null,
        private val delayMillis: Long = 0,
    ) : SpeechEngineFactory {
        val loads = AtomicInteger()
        val hints = mutableListOf<String?>()
        val engines = mutableListOf<FakeEngine>()
        var onTranscribe: (FloatArray) -> Unit = {}

        override fun load(modelFile: File, languageHint: String?): SpeechEngine {
            loads.incrementAndGet()
            hints += languageHint
            if (delayMillis > 0) Thread.sleep(delayMillis)
            failure?.let { throw it }
            return FakeEngine(text, onTranscribe).also { engines += it }
        }
    }

    private class RecordingListener(var finish: Boolean = false) : SpeechListener {
        val phases = mutableListOf<SpeechPhase>()
        val levels = mutableListOf<Float>()
        override fun onPhase(phase: SpeechPhase) {
            phases += phase
        }

        override fun onLevel(level: Float) {
            levels += level
        }

        override fun finishRequested() = finish
    }

    private val payload = ByteArray(50_000) { it.toByte() }
    private val spec = SpeechModelCatalog.default.copy(
        fileName = "t.gguf",
        byteSize = payload.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) },
    )

    private fun readyStore(): SpeechModelStore {
        val store = SpeechModelStore(folder.newFolder(), spec)
        store.modelFile.writeBytes(payload)
        return store
    }

    private fun provider(factory: SpeechEngineFactory, memory: Long = Long.MAX_VALUE, idle: Long = 60_000) =
        SpeechEngineProvider(factory, scope, availableMemoryBytes = { memory }, requiredMemoryBytes = 1_000, idleMillis = idle)

    private fun runner(store: SpeechModelStore, provider: SpeechEngineProvider, source: () -> PcmSource) =
        SpeechRecognitionRunner(store, provider, source)

    private fun utterance() = noise(600) + speech(1_200) + noise(2_500, seed = 3)

    // ── runner ────────────────────────────────────────────────────────────────

    @Test
    fun anUtteranceBecomesACleanSearchQuery() = runBlocking {
        val factory = FakeFactory()
        var received = 0
        factory.onTranscribe = { received = it.size }
        val source = FakeSource(utterance())
        val listener = RecordingListener()

        val outcome = runner(readyStore(), provider(factory)) { source }.recognize("it-IT", listener)

        assertEquals("Via Giuseppe Garibaldi, Bologna", (outcome as SpeechOutcome.Text).text)
        assertEquals(listOf(SpeechPhase.Listening, SpeechPhase.Transcribing), listener.phases)
        assertEquals(listOf("it"), factory.hints)
        assertTrue("the engine got ${received / 16} ms", received in 1_000 * 16..2_000 * 16)
        assertTrue(source.closed.get())
        assertTrue(listener.levels.isNotEmpty() && listener.levels.max() > 0.3f)
    }

    @Test
    fun silenceIsReportedAsNothingHeardWithoutDecoding() = runBlocking {
        val factory = FakeFactory()
        factory.onTranscribe = { throw AssertionError("must not decode silence") }
        val source = FakeSource(noise(9_000))

        val outcome = runner(readyStore(), provider(factory)) { source }.recognize("it", RecordingListener())

        assertEquals(SpeechOutcome.NothingHeard, outcome)
        assertTrue(source.closed.get())
    }

    @Test
    fun aModelThatWritesOnlyPunctuationCountsAsNothingHeard() = runBlocking {
        val outcome = runner(readyStore(), provider(FakeFactory(text = " . "))) { FakeSource(utterance()) }
            .recognize("it", RecordingListener())
        assertEquals(SpeechOutcome.NothingHeard, outcome)
    }

    @Test
    fun theUserCanEndTheUtteranceByHand() = runBlocking {
        val source = FakeSource(noise(500) + speech(1_500), endlessSilence = true)
        val listener = RecordingListener()
        val job = scope.launch { delay(300); listener.finish = true }

        val outcome = withTimeout(10_000) {
            runner(readyStore(), provider(FakeFactory())) { source }.recognize("it", listener)
        }

        job.cancelAndJoin()
        assertTrue(outcome is SpeechOutcome.Text)
    }

    @Test
    fun unsupportedLanguageAndMissingModelFallBackWithoutTouchingTheMicrophone() = runBlocking {
        val opened = AtomicInteger()
        val source = { opened.incrementAndGet(); FakeSource(utterance()) }

        assertEquals(
            SpeechOutcome.Unavailable(SpeechUnavailableReason.UnsupportedLanguage),
            runner(readyStore(), provider(FakeFactory()), source).recognize("ja-JP", RecordingListener()),
        )
        val empty = SpeechModelStore(folder.newFolder(), spec)
        assertEquals(
            SpeechOutcome.Unavailable(SpeechUnavailableReason.ModelMissing),
            runner(empty, provider(FakeFactory()), source).recognize("it", RecordingListener()),
        )
        assertEquals(0, opened.get())
    }

    @Test
    fun aMicrophoneThatCannotOpenFallsBack() = runBlocking {
        for (failure in listOf(IOException("busy"), SecurityException("denied"))) {
            val source = FakeSource(utterance(), startFailure = failure)
            val outcome = runner(readyStore(), provider(FakeFactory())) { source }.recognize("it", RecordingListener())
            assertEquals(SpeechOutcome.Unavailable(SpeechUnavailableReason.Microphone), outcome)
            assertTrue(source.closed.get())
        }
    }

    @Test
    fun anEngineThatFailsToLoadIsNoticedWhileTheUserIsStillTalking() = runBlocking {
        val factory = FakeFactory(failure = SpeechUnavailable(SpeechUnavailableReason.ModelRejected, "bad gguf"))
        val source = FakeSource(noise(300), endlessSilence = true)

        val outcome = withTimeout(10_000) {
            runner(readyStore(), provider(factory)) { source }.recognize("it", RecordingListener())
        }

        assertEquals(SpeechOutcome.Unavailable(SpeechUnavailableReason.ModelRejected), outcome)
        assertTrue(source.closed.get())
    }

    @Test
    fun lowMemoryFallsBackInsteadOfLoadingTheModel() = runBlocking {
        val factory = FakeFactory()
        val outcome = runner(readyStore(), provider(factory, memory = 10)) { FakeSource(noise(300), endlessSilence = true) }
            .recognize("it", RecordingListener())
        assertEquals(SpeechOutcome.Unavailable(SpeechUnavailableReason.LowMemory), outcome)
        assertEquals(0, factory.loads.get())
    }

    @Test
    fun anUnexpectedEngineFailureBecomesUnavailableNotACrash() = runBlocking {
        val factory = object : SpeechEngineFactory {
            override fun load(modelFile: File, languageHint: String?): SpeechEngine =
                throw IllegalStateException("boom")
        }
        val outcome = runner(readyStore(), provider(factory)) { FakeSource(noise(300), endlessSilence = true) }
            .recognize("it", RecordingListener())
        assertEquals(SpeechOutcome.Unavailable(SpeechUnavailableReason.Failed), outcome)
    }

    @Test
    fun cancellingTheCoroutineReleasesTheMicrophone() = runBlocking {
        val source = FakeSource(noise(300), endlessSilence = true)
        val job = scope.launch { runner(readyStore(), provider(FakeFactory())) { source }.recognize("it", RecordingListener()) }
        delay(200)
        job.cancelAndJoin()
        assertTrue(source.closed.get())
    }

    // ── provider ──────────────────────────────────────────────────────────────

    @Test
    fun theEngineIsLoadedOnceAndReusedForTheSameLanguage() = runBlocking {
        val factory = FakeFactory()
        val provider = provider(factory)
        val file = File("m.gguf")
        val first = provider.use(file, "it") { it }
        val second = provider.use(file, "it") { it }
        assertSame(first, second)
        assertEquals(1, factory.loads.get())

        provider.use(file, "de") { }
        assertEquals(2, factory.loads.get())
        assertTrue("the old engine is freed before the new one is used", factory.engines[0].closed.get())
    }

    @Test
    fun anIdleEngineIsUnloadedAndUseKeepsItAlive() = runBlocking {
        val factory = FakeFactory()
        val provider = provider(factory, idle = 500)
        provider.use(File("m.gguf"), "it") { }
        assertFalse(factory.engines[0].closed.get())
        delay(200)
        provider.use(File("m.gguf"), "it") { }
        delay(300)
        assertFalse("used again 300 ms ago, idle limit 500 ms", factory.engines[0].closed.get())
        delay(900)
        assertTrue(factory.engines[0].closed.get())
        provider.use(File("m.gguf"), "it") { }
        assertEquals(2, factory.loads.get())
    }

    @Test
    fun cancelRunningReachesTheEngineWhileItDecodes() = runBlocking {
        val factory = FakeFactory()
        val provider = provider(factory)
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val job = scope.launch {
            provider.use(File("m.gguf"), "it") { engine ->
                inside.countDown()
                release.await(5, TimeUnit.SECONDS)
                engine
            }
        }
        assertTrue(inside.await(5, TimeUnit.SECONDS))
        provider.cancelRunning()
        release.countDown()
        job.join()
        assertTrue(factory.engines[0].cancelled.get())
        provider.cancelRunning()
    }

    @Test
    fun unloadFreesTheEngine() = runBlocking {
        val factory = FakeFactory()
        val provider = provider(factory)
        provider.use(File("m.gguf"), "it") { }
        provider.unload()
        assertTrue(factory.engines[0].closed.get())
        assertThrows(SpeechUnavailable::class.java) {
            runBlocking { provider(FakeFactory(), memory = 0).preload(File("m"), "it") }
        }
        Unit
    }
}
