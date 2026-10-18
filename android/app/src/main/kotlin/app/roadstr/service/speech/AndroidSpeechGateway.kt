package app.roadstr.service.speech

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.roadstr.core.speech.SpeechModelCatalog
import app.roadstr.core.speech.SpeechModelSpec
import app.roadstr.core.speech.SpeechOutcome
import app.roadstr.core.speech.SpeechUnavailableReason
import app.roadstr.feature.speech.NativeSpeechGateway
import app.roadstr.feature.speech.NativeSpeechModelStatus
import app.roadstr.feature.speech.NativeSpeechPhase
import app.roadstr.feature.speech.NativeSpeechProblem
import app.roadstr.feature.speech.NativeSpeechSnapshot
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the offline speech model (download, verification, deletion), the engine in memory and one
 * recognition at a time. Created with the Activity and closed with it.
 */
class AndroidSpeechGateway(
    context: Context,
    private val spec: SpeechModelSpec = SpeechModelCatalog.default,
    private val transport: SpeechModelTransport = OkHttpSpeechModelTransport(),
) : NativeSpeechGateway {
    private val appContext = context.applicationContext
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = SpeechModelStore(File(appContext.noBackupFilesDir, "speech-model"), spec)
    private val engines = SpeechEngineProvider(
        factory = OfflineSttSpeechEngineFactory,
        scope = background,
        availableMemoryBytes = ::availableMemory,
        requiredMemoryBytes = spec.requiredAvailableMemoryBytes,
    )
    private val runner = SpeechRecognitionRunner(store, engines, ::AudioRecordPcmSource)

    private val mutableState = MutableStateFlow(
        NativeSpeechSnapshot(
            model = if (deviceEligible()) NativeSpeechModelStatus.Checking else NativeSpeechModelStatus.Unsupported,
        ),
    )
    override val state: StateFlow<NativeSpeechSnapshot> = mutableState.asStateFlow()

    private var downloading: Job? = null
    private var listening: Job? = null
    private val finishRequested = AtomicBoolean(false)

    init {
        if (mutableState.value.model == NativeSpeechModelStatus.Checking) {
            background.launch { refreshModelState() }
        }
    }

    override fun canRecognize(languageTag: String): Boolean =
        mutableState.value.model == NativeSpeechModelStatus.Ready && spec.supportsLanguage(languageTag)

    override fun downloadModel() {
        if (mutableState.value.model != NativeSpeechModelStatus.NotDownloaded) return
        mutableState.update {
            it.copy(model = NativeSpeechModelStatus.Downloading, downloadFraction = 0.0, problem = null)
        }
        downloading = background.launch { runDownload() }
    }

    override fun cancelDownload() {
        downloading?.cancel()
    }

    override fun deleteModel() {
        if (mutableState.value.model == NativeSpeechModelStatus.Downloading) return
        cancelListening()
        background.launch {
            engines.unload()
            store.delete()
            mutableState.update { it.copy(model = NativeSpeechModelStatus.NotDownloaded, problem = null) }
        }
    }

    override fun listen(languageTag: String, onOutcome: (SpeechOutcome) -> Unit) {
        if (listening?.isActive == true) return
        finishRequested.set(false)
        listening = main.launch {
            val outcome = recognize(languageTag)
            mutableState.update { it.copy(phase = NativeSpeechPhase.Idle, level = 0f) }
            noteEngineProblem(outcome)
            onOutcome(outcome)
        }
    }

    override fun finishListening() {
        finishRequested.set(true)
    }

    override fun cancelListening() {
        listening?.cancel()
    }

    override fun trimMemory() {
        if (listening?.isActive == true) return
        background.launch { engines.unload() }
    }

    override fun close() {
        cancelListening()
        downloading?.cancel()
        engines.cancelRunning()
        main.cancel()
        // The decode in flight, if any, returns first; then the model is freed.
        CoroutineScope(Dispatchers.IO).launch { engines.unload() }
        background.cancel()
    }

    private suspend fun recognize(languageTag: String): SpeechOutcome = try {
        runner.recognize(languageTag, listener)
    } catch (cancelled: CancellationException) {
        SpeechOutcome.Cancelled
    }

    private val listener = object : SpeechListener {
        override fun onPhase(phase: SpeechPhase) {
            mutableState.update {
                it.copy(
                    phase = if (phase == SpeechPhase.Listening) {
                        NativeSpeechPhase.Listening
                    } else {
                        NativeSpeechPhase.Transcribing
                    },
                )
            }
        }

        override fun onLevel(level: Float) {
            mutableState.update { it.copy(level = level) }
        }

        override fun finishRequested(): Boolean = finishRequested.get()
    }

    /** A model the engine cannot use on this device stays marked as failed instead of failing every tap. */
    private fun noteEngineProblem(outcome: SpeechOutcome) {
        val reason = (outcome as? SpeechOutcome.Unavailable)?.reason ?: return
        val permanent = reason == SpeechUnavailableReason.UnsupportedDevice ||
            reason == SpeechUnavailableReason.ModelRejected
        if (!permanent) return
        mutableState.update {
            it.copy(model = NativeSpeechModelStatus.Failed, problem = NativeSpeechProblem.EngineUnavailable)
        }
    }

    private fun refreshModelState() {
        val ready = store.state() == SpeechModelFileState.Ready
        mutableState.update {
            it.copy(model = if (ready) NativeSpeechModelStatus.Ready else NativeSpeechModelStatus.NotDownloaded)
        }
    }

    private fun CoroutineScope.runDownload() {
        var lastPermille = -1
        try {
            store.download(
                transport,
                onProgress = { done, total ->
                    val permille = (done * 1000 / total).toInt()
                    if (permille != lastPermille) {
                        lastPermille = permille
                        mutableState.update { it.copy(downloadFraction = permille / 1000.0) }
                    }
                },
                isCancelled = { !isActive },
            )
            refreshModelState()
        } catch (error: SpeechModelException) {
            val cancelled = error is SpeechModelException.Cancelled
            mutableState.update {
                if (cancelled) {
                    it.copy(model = NativeSpeechModelStatus.NotDownloaded, downloadFraction = 0.0)
                } else {
                    it.copy(model = NativeSpeechModelStatus.NotDownloaded, problem = problemFor(error))
                }
            }
        }
    }

    private fun problemFor(error: SpeechModelException) = when (error) {
        is SpeechModelException.InsufficientStorage -> NativeSpeechProblem.Storage
        is SpeechModelException.Integrity -> NativeSpeechProblem.Integrity
        else -> NativeSpeechProblem.Network
    }

    private fun memoryInfo() = ActivityManager.MemoryInfo().also {
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
    }

    private fun availableMemory(): Long = memoryInfo().availMem

    /** Offered only where the engine can run: 64-bit Arm with enough RAM for the model. */
    private fun deviceEligible(): Boolean =
        Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a") && memoryInfo().totalMem >= spec.minimumTotalMemoryBytes
}
