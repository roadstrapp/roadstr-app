package app.roadstr.service.speech

import app.roadstr.core.speech.SpeechModelSpec
import app.roadstr.core.speech.SpeechOutcome
import app.roadstr.core.speech.SpeechUnavailableReason
import app.roadstr.core.speech.SpokenQuery
import app.roadstr.core.speech.UtteranceEndpointer
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A loaded speech model, as the recognizer sees it. */
interface SpeechEngine : Closeable {
    /** Blocking. Throws [SpeechUnavailable] if the engine cannot decode. */
    fun transcribe(pcm: FloatArray): String
    fun cancel()
}

fun interface SpeechEngineFactory {
    @Throws(SpeechUnavailable::class)
    fun load(modelFile: File, languageHint: String?): SpeechEngine
}

/** The offline engine cannot be used right now; the caller should fall back to another recognizer. */
class SpeechUnavailable(val reason: SpeechUnavailableReason, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** A microphone, or a recording in tests. Reads 16 kHz mono PCM16. */
interface PcmSource : Closeable {
    @Throws(IOException::class)
    fun start()

    /** Blocks until samples are available; returns the count, or a negative number on failure. */
    fun read(buffer: ShortArray): Int
}

enum class SpeechPhase { Listening, Transcribing }

interface SpeechListener {
    fun onPhase(phase: SpeechPhase)
    fun onLevel(level: Float)

    /** True once the user asked to stop listening and transcribe what was heard so far. */
    fun finishRequested(): Boolean
}

/**
 * Keeps at most one engine in memory, loads it on demand and drops it after [idleMillis] of disuse
 * (a loaded model is about a gigabyte). Refuses to load when free memory is short, so that a
 * crowded phone gets the system recognizer instead of an out-of-memory kill.
 */
class SpeechEngineProvider(
    private val factory: SpeechEngineFactory,
    private val scope: CoroutineScope,
    private val availableMemoryBytes: () -> Long,
    private val requiredMemoryBytes: Long,
    private val idleMillis: Long = 60_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private class Loaded(val modelPath: String, val hint: String?, val engine: SpeechEngine)

    private val mutex = Mutex()
    private var loaded: Loaded? = null
    private var idleJob: Job? = null
    private var lastUseMillis = 0L

    @Volatile
    private var running: SpeechEngine? = null

    /** Loads the engine now (idempotent), so that it is ready when the user stops talking. */
    suspend fun preload(modelFile: File, languageHint: String?) {
        mutex.withLock {
            idleJob?.cancel()
            engineFor(modelFile, languageHint)
            markUsed()
        }
    }

    suspend fun <T> use(modelFile: File, languageHint: String?, block: (SpeechEngine) -> T): T =
        mutex.withLock {
            idleJob?.cancel()
            try {
                val engine = engineFor(modelFile, languageHint)
                running = engine
                block(engine)
            } finally {
                running = null
                markUsed()
            }
        }

    /** Aborts the decode in flight, from any thread. */
    fun cancelRunning() {
        running?.cancel()
    }

    suspend fun unload() {
        mutex.withLock { drop() }
    }

    private fun engineFor(modelFile: File, languageHint: String?): SpeechEngine {
        loaded?.takeIf { it.modelPath == modelFile.path && it.hint == languageHint }?.let { return it.engine }
        drop()
        if (availableMemoryBytes() < requiredMemoryBytes) {
            throw SpeechUnavailable(SpeechUnavailableReason.LowMemory, "not enough free memory for the model")
        }
        val engine = factory.load(modelFile, languageHint)
        loaded = Loaded(modelFile.path, languageHint, engine)
        return engine
    }

    private fun markUsed() {
        lastUseMillis = nowMillis()
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleMillis)
            mutex.withLock {
                if (nowMillis() - lastUseMillis >= idleMillis) drop()
            }
        }
    }

    private fun drop() {
        loaded?.engine?.let { runCatching { it.close() } }
        loaded = null
    }
}

/**
 * Last stage of recognition: turns what the model wrote into what a search wants (digits for house
 * numbers, street names as the map spells them). It never fails the recognition: if it throws, the
 * text goes through unchanged.
 */
interface SpeechTextRefiner {
    /**
     * Called when listening starts, to get whatever [refine] will need under way. Must return at once:
     * the user is already talking.
     */
    suspend fun prepare(languageTag: String) = Unit

    suspend fun refine(text: String, languageTag: String): String = text

    companion object {
        val None: SpeechTextRefiner = object : SpeechTextRefiner {}
    }
}

/** Records one utterance from [newSource], ends it by itself, and has the engine transcribe it. */
class SpeechRecognitionRunner(
    private val store: SpeechModelStore,
    private val engines: SpeechEngineProvider,
    private val newSource: () -> PcmSource,
    private val endpointerConfig: UtteranceEndpointer.Config = UtteranceEndpointer.Config(),
    private val refiner: SpeechTextRefiner = SpeechTextRefiner.None,
) {
    private val spec: SpeechModelSpec get() = store.spec

    private sealed interface Captured {
        class Audio(val pcm: FloatArray) : Captured
        data object NothingHeard : Captured
        data object Cancelled : Captured
        class Unavailable(val reason: SpeechUnavailableReason) : Captured
    }

    suspend fun recognize(languageTag: String, listener: SpeechListener): SpeechOutcome {
        val hint = spec.languageHint(languageTag)
            ?: return SpeechOutcome.Unavailable(SpeechUnavailableReason.UnsupportedLanguage)
        // Checking the file can mean hashing half a gigabyte the first time; never on the caller's thread.
        val state = withContext(Dispatchers.IO) { store.state() }
        if (state != SpeechModelFileState.Ready) {
            return SpeechOutcome.Unavailable(SpeechUnavailableReason.ModelMissing)
        }
        return coroutineScope {
            // The model loads while the user speaks, so there is no wait after they stop.
            val loadState = LoadState()
            val loading = async(Dispatchers.Default) { preload(hint, loadState) }
            listener.onPhase(SpeechPhase.Listening)
            prepareRefiner(languageTag)
            when (val captured = capture(listener, loadState)) {
                is Captured.Audio -> transcribe(captured.pcm, hint, loading, loadState, listener, languageTag)
                Captured.NothingHeard -> SpeechOutcome.NothingHeard
                Captured.Cancelled -> SpeechOutcome.Cancelled
                is Captured.Unavailable -> SpeechOutcome.Unavailable(captured.reason)
            }
        }
    }

    /** Written by the loading coroutine, read by the capture loop on another thread. */
    private class LoadState {
        @Volatile
        var failure: SpeechUnavailable? = null
    }

    private suspend fun preload(hint: String?, state: LoadState) {
        try {
            engines.preload(store.modelFile, hint)
        } catch (error: SpeechUnavailable) {
            state.failure = error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state.failure = SpeechUnavailable(SpeechUnavailableReason.Failed, "the engine failed to load", error)
        }
    }

    private suspend fun capture(listener: SpeechListener, loadState: LoadState): Captured =
        withContext(Dispatchers.IO) {
            val source = newSource()
            try {
                source.start()
            } catch (error: IOException) {
                source.close()
                return@withContext Captured.Unavailable(SpeechUnavailableReason.Microphone)
            } catch (error: SecurityException) {
                source.close()
                return@withContext Captured.Unavailable(SpeechUnavailableReason.Microphone)
            }
            source.use { readUtterance(it, listener, loadState) }
        }

    private suspend fun readUtterance(
        source: PcmSource,
        listener: SpeechListener,
        loadState: LoadState,
    ): Captured {
        val endpointer = UtteranceEndpointer(endpointerConfig)
        val buffer = ShortArray(CHUNK_SAMPLES)
        while (currentCoroutineContext().isActive) {
            loadState.failure?.let { return Captured.Unavailable(it.reason) }
            val count = source.read(buffer)
            if (count < 0) return Captured.Unavailable(SpeechUnavailableReason.Microphone)
            endpointer.feed(buffer, count)
            listener.onLevel(endpointer.level)
            if (listener.finishRequested()) endpointer.finishNow()
            when (endpointer.status) {
                UtteranceEndpointer.Status.Finished -> return Captured.Audio(endpointer.utterance())
                UtteranceEndpointer.Status.NoSpeech -> return Captured.NothingHeard
                else -> Unit
            }
        }
        return Captured.Cancelled
    }

    private suspend fun transcribe(
        pcm: FloatArray,
        hint: String?,
        loading: Deferred<Unit>,
        loadState: LoadState,
        listener: SpeechListener,
        languageTag: String,
    ): SpeechOutcome {
        listener.onPhase(SpeechPhase.Transcribing)
        loading.await()
        loadState.failure?.let { return SpeechOutcome.Unavailable(it.reason) }
        val started = System.nanoTime()
        val text = try {
            decode(pcm, hint)
        } catch (error: SpeechUnavailable) {
            return SpeechOutcome.Unavailable(error.reason)
        }
        val query = refined(SpokenQuery.normalize(text), languageTag)
        if (query.isEmpty()) return SpeechOutcome.NothingHeard
        return SpeechOutcome.Text(query, (System.nanoTime() - started) / 1_000_000L)
    }

    private suspend fun prepareRefiner(languageTag: String) {
        try {
            refiner.prepare(languageTag)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Refining is a nicety; recognition goes on without it.
        }
    }

    private suspend fun refined(query: String, languageTag: String): String {
        if (query.isEmpty()) return query
        return try {
            refiner.refine(query, languageTag).trim().ifEmpty { query }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            query
        }
    }

    /** Native decoding cannot be interrupted by a coroutine, so cancellation is relayed to the engine. */
    private suspend fun decode(pcm: FloatArray, hint: String?): String = coroutineScope {
        val decoding = async(Dispatchers.Default) {
            engines.use(store.modelFile, hint) { it.transcribe(pcm) }
        }
        try {
            decoding.await()
        } catch (cancelled: CancellationException) {
            engines.cancelRunning()
            throw cancelled
        }
    }

    private companion object {
        /** 40 ms: responsive level meter, cheap to feed. */
        const val CHUNK_SAMPLES = 640
    }
}
