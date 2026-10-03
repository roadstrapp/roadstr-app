package app.roadstr.roadtest

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.roadstr.feature.voice.NativeVoiceAssetDownloader
import app.roadstr.feature.voice.NativeVoiceAssetState
import app.roadstr.feature.voice.NativeVoiceAssetStore
import app.roadstr.feature.voice.NativeVoiceAudioFocusController
import app.roadstr.feature.voice.NativeVoiceAudioFocusState
import app.roadstr.feature.voice.NativeVoiceCatalog
import app.roadstr.feature.voice.NativeVoiceDirective
import app.roadstr.feature.voice.NativeVoiceEngine
import app.roadstr.feature.voice.NativeVoiceGateway
import app.roadstr.feature.voice.NativeVoiceGender
import app.roadstr.feature.voice.NativeVoiceGuidanceSession
import app.roadstr.feature.voice.NativeVoiceInferencePolicy
import app.roadstr.feature.voice.NativeVoiceRuntimeSnapshot
import app.roadstr.feature.voice.NativeVoiceRuntimeStatus
import app.roadstr.feature.voice.NativeVoiceSelection
import app.roadstr.feature.voice.OkHttpNativeVoiceDownloadTransport
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Android owner for verified model download, neural inference and PCM playback. */
class NativeRoadTestVoiceGateway(context: Context) : NativeVoiceGateway {
    private val applicationContext = context.applicationContext
    private val documentsDirectory = File(applicationContext.applicationInfo.dataDir, "app_flutter")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val guidance = NativeVoiceGuidanceSession()
    private val engine = NativeRoadTestNeuralVoiceEngine(applicationContext, documentsDirectory)
    private val player = NativeRoadTestPcmPlayer(applicationContext)
    private val _state = MutableStateFlow(
        NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.MissingAssets),
    )
    private val configurationLock = Any()
    private var languageCode = "en"
    private var gender = NativeVoiceGender.Male
    private var speed = NativeVoiceCatalog.speedForStage(NativeVoiceCatalog.DEFAULT_SPEED_STAGE)
    private var volume = 1.0
    private var muted = false
    private var playbackJob: Job? = null
    private var downloadJob: Job? = null

    override val state: StateFlow<NativeVoiceRuntimeSnapshot> = _state.asStateFlow()

    init {
        scope.launch { refreshAssetState() }
    }

    override fun configure(
        languageCode: String,
        gender: NativeVoiceGender,
        speed: Double,
        volume: Double,
    ) {
        val normalizedLanguage = languageCode.trim().lowercase(Locale.ROOT)
        require(normalizedLanguage.length in 2..8)
        require(speed.isFinite() && speed in 0.5..2.0)
        require(volume.isFinite() && volume in 0.0..1.0)
        val changed = synchronized(configurationLock) {
            val value = this.languageCode != normalizedLanguage || this.gender != gender
            this.languageCode = normalizedLanguage
            this.gender = gender
            this.speed = speed
            this.volume = volume
            value
        }
        if (changed) stop()
    }

    override fun downloadAssets() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _state.value = NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.Downloading)
            try {
                runInterruptible(Dispatchers.IO) {
                    documentsDirectory.mkdirs()
                    NativeVoiceAssetDownloader(OkHttpNativeVoiceDownloadTransport()).ensure(
                        documentsDirectory = documentsDirectory,
                        onProgress = { progress ->
                            _state.value = NativeVoiceRuntimeSnapshot(
                                status = NativeVoiceRuntimeStatus.Downloading,
                                downloadFraction = progress.fraction.coerceIn(0.0, 1.0),
                            )
                        },
                    )
                }
                _state.value = NativeVoiceRuntimeSnapshot(
                    NativeVoiceRuntimeStatus.Ready,
                    downloadFraction = 1.0,
                )
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                _state.value = NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.Failed)
            }
        }
    }

    override fun announceStart() = submitPriority(fixedPhrase(start = true))

    override fun announceManeuver(instruction: String, distanceMeters: Int, nowMillis: Long) {
        if (muted) return
        handle(
            guidance.submitManeuver(
                instruction = instruction,
                distanceMetres = distanceMeters,
                languageCode = synchronized(configurationLock) { languageCode },
                imperial = false,
                nowMillis = nowMillis,
            ),
        )
    }

    override fun announceArrival() = submitPriority(fixedPhrase(start = false))

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) stop()
    }

    override fun stop() {
        guidance.stop()
        playbackJob?.cancel()
        playbackJob = null
        player.stop()
        if (_state.value.status == NativeVoiceRuntimeStatus.Speaking) {
            _state.value = NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.Ready, 1.0)
        }
    }

    override fun close() {
        stop()
        downloadJob?.cancel()
        engine.close()
        player.close()
        scope.cancel()
    }

    private fun submitPriority(text: String) {
        if (muted) return
        handle(
            guidance.submitPriority(
                text = text,
                maneuver = false,
                nowMillis = android.os.SystemClock.elapsedRealtime(),
            ),
        )
    }

    private fun handle(directive: NativeVoiceDirective) {
        when (directive) {
            is NativeVoiceDirective.Start -> {
                if (directive.interruptCurrent) playbackJob?.cancel()
                playbackJob = scope.launch {
                    play(directive.utterance.id, directive.utterance.text)
                }
            }
            NativeVoiceDirective.AcquireFocus -> Unit
            NativeVoiceDirective.HoldFocus -> Unit
            NativeVoiceDirective.ReleaseFocus -> player.releaseFocus()
            is NativeVoiceDirective.Dropped,
            is NativeVoiceDirective.Queued,
            NativeVoiceDirective.None,
            -> Unit
        }
    }

    private suspend fun play(utteranceId: Long, text: String) {
        try {
            val configuration = synchronized(configurationLock) {
                VoiceConfiguration(languageCode, gender, speed, volume)
            }
            val selection = NativeVoiceCatalog.selection(
                configuration.languageCode,
                configuration.gender,
            ) ?: return
            _state.value = NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.Speaking, 1.0)
            val audio = withTimeout(NativeVoiceGuidanceSession.MAX_UTTERANCE_WAIT_MILLIS) {
                engine.synthesize(text, selection, configuration.speed)
            }
            player.play(audio, configuration.volume)
        } catch (_: MissingVoiceAssetsException) {
            _state.value = NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.MissingAssets)
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            _state.value = NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.Failed)
        } finally {
            handle(guidance.finish(utteranceId))
            if (
                guidance.snapshot().current == null &&
                _state.value.status == NativeVoiceRuntimeStatus.Speaking
            ) {
                _state.value = NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.Ready, 1.0)
            }
        }
    }

    private suspend fun refreshAssetState() = withContext(Dispatchers.IO) {
        if (_state.value.status == NativeVoiceRuntimeStatus.Downloading) return@withContext
        val ready = NativeVoiceAssetStore(documentsDirectory)
            .inspectCatalogue()
            .all { it.state == NativeVoiceAssetState.Valid }
        _state.value = NativeVoiceRuntimeSnapshot(
            status = if (ready) NativeVoiceRuntimeStatus.Ready else NativeVoiceRuntimeStatus.MissingAssets,
            downloadFraction = if (ready) 1.0 else 0.0,
        )
    }

    private fun fixedPhrase(start: Boolean): String {
        val language = synchronized(configurationLock) { languageCode }
        return if (start) {
            when (language) {
                "it" -> "Partenza"
                "es" -> "¡Vamos!"
                "fr" -> "C'est parti !"
                "ja" -> "出発します！"
                "zh" -> "出发！"
                "pt" -> "Vamos lá!"
                "de" -> "Los geht's!"
                else -> "Let's go!"
            }
        } else {
            when (language) {
                "it" -> "Sei arrivato a destinazione."
                "es" -> "Has llegado a tu destino."
                "fr" -> "Vous êtes arrivé à destination."
                "ja" -> "目的地に到着しました。"
                "zh" -> "您已到达目的地。"
                "pt" -> "Chegou ao seu destino."
                "de" -> "Sie haben Ihr Ziel erreicht."
                else -> "You have arrived at your destination."
            }
        }
    }

    private data class VoiceConfiguration(
        val languageCode: String,
        val gender: NativeVoiceGender,
        val speed: Double,
        val volume: Double,
    )
}

private data class NativePcmAudio(
    val samples: FloatArray,
    val sampleRateHz: Int,
)

private class MissingVoiceAssetsException : Exception()

private class NativeRoadTestNeuralVoiceEngine(
    context: Context,
    private val documentsDirectory: File,
) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val phonemizer = NativeRoadTestEspeakPhonemizer(context, documentsDirectory)
    private val mutex = Mutex()
    private var session: OrtSession? = null
    private var sessionPath: String? = null
    private var kokoroVocabulary: Map<String, Int>? = null
    private var piperPhonemeIds: Map<String, List<Int>>? = null
    private val validatedAssetPaths = mutableSetOf<String>()
    private val assetStore = NativeVoiceAssetStore(documentsDirectory)
    private val memoryCache = object : LinkedHashMap<String, NativePcmAudio>(32, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, NativePcmAudio>?,
        ): Boolean = size > MAX_MEMORY_CACHE_ENTRIES
    }

    suspend fun synthesize(
        text: String,
        selection: NativeVoiceSelection,
        speed: Double,
    ): NativePcmAudio = mutex.withLock {
        withContext(Dispatchers.IO) {
            val cacheKey = listOf(
                selection.languageCode,
                selection.voiceName,
                speed.toString(),
                text,
            ).joinToString("\u0000")
            memoryCache[cacheKey]?.let { return@withContext it }
            val ipa = NativeVoiceInferencePolicy.correctIpa(
                NativeVoiceInferencePolicy.validatePhonemeOutput(
                    phonemizer.phonemize(
                        NativeVoiceInferencePolicy.boundPhonemizerText(text),
                        selection.languageCode,
                    ),
                ),
                selection.languageCode,
            )
            val audio = when (selection.engine) {
                NativeVoiceEngine.Kokoro -> synthesizeKokoro(ipa, selection, speed)
                NativeVoiceEngine.Piper -> synthesizePiper(ipa, selection, speed)
            }
            memoryCache[cacheKey] = audio
            audio
        }
    }

    private fun synthesizeKokoro(
        ipa: String,
        selection: NativeVoiceSelection,
        speed: Double,
    ): NativePcmAudio {
        val model = requiredFile("kokoro/model_q8f16.onnx")
        val tokenizer = requiredFile("kokoro/tokenizer.json")
        val voice = requiredFile("kokoro/${selection.voiceName}.bin")
        val vocabulary = kokoroVocabulary ?: parseKokoroVocabulary(tokenizer).also {
            kokoroVocabulary = it
        }
        val voiceBytes = voice.readBytes()
        require(voiceBytes.size % Float.SIZE_BYTES == 0)
        val inputs = NativeVoiceInferencePolicy.kokoroInputs(
            ipa = ipa,
            vocabulary = vocabulary,
            voiceFloatCount = voiceBytes.size / Float.SIZE_BYTES,
            speed = speed,
        )
        val styleOffset = inputs.styleRow * NativeVoiceInferencePolicy.KOKORO_STYLE_WIDTH
        val allStyles = ByteBuffer.wrap(voiceBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val style = FloatArray(NativeVoiceInferencePolicy.KOKORO_STYLE_WIDTH)
        allStyles.position(styleOffset)
        allStyles.get(style)
        val activeSession = session(model)
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(inputs.tokenIds),
            longArrayOf(1, inputs.tokenIds.size.toLong()),
        ).use { tokenTensor ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(style),
                longArrayOf(1, style.size.toLong()),
            ).use { styleTensor ->
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(floatArrayOf(inputs.speed)),
                    longArrayOf(1),
                ).use { speedTensor ->
                    activeSession.run(
                        mapOf(
                            "input_ids" to tokenTensor,
                            "style" to styleTensor,
                            "speed" to speedTensor,
                        ),
                    ).use { output ->
                        return NativePcmAudio(output.samples(), selection.sampleRateHz)
                    }
                }
            }
        }
    }

    private fun synthesizePiper(
        ipa: String,
        selection: NativeVoiceSelection,
        speed: Double,
    ): NativePcmAudio {
        val model = requiredFile("piper/${selection.voiceName}.onnx")
        val config = requiredFile("piper/${selection.voiceName}.onnx.json")
        val idMap = piperPhonemeIds ?: parsePiperPhonemeIds(config).also {
            piperPhonemeIds = it
        }
        val inputs = NativeVoiceInferencePolicy.piperInputs(ipa, idMap, speed)
        val activeSession = session(model)
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(inputs.phonemeIds),
            longArrayOf(1, inputs.phonemeIds.size.toLong()),
        ).use { inputTensor ->
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(longArrayOf(inputs.phonemeIds.size.toLong())),
                longArrayOf(1),
            ).use { lengthTensor ->
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(inputs.scales),
                    longArrayOf(3),
                ).use { scalesTensor ->
                    activeSession.run(
                        mapOf(
                            "input" to inputTensor,
                            "input_lengths" to lengthTensor,
                            "scales" to scalesTensor,
                        ),
                    ).use { output ->
                        return NativePcmAudio(output.samples(), selection.sampleRateHz)
                    }
                }
            }
        }
    }

    private fun session(model: File): OrtSession {
        if (sessionPath == model.path) return requireNotNull(session)
        session?.close()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(4)
            options.addXnnpack(mapOf("intra_op_num_threads" to "4"))
            return environment.createSession(model.path, options).also {
                session = it
                sessionPath = model.path
            }
        } finally {
            options.close()
        }
    }

    private fun requiredFile(relativePath: String): File {
        val asset = NativeVoiceCatalog.downloadAssets.firstOrNull {
            it.relativePath == relativePath
        } ?: error("Unknown voice asset")
        if (relativePath !in validatedAssetPaths) {
            if (assetStore.inspect(asset).state != NativeVoiceAssetState.Valid) {
                throw MissingVoiceAssetsException()
            }
            validatedAssetPaths += relativePath
        }
        return assetStore.resolve(asset)
    }

    private fun parseKokoroVocabulary(file: File): Map<String, Int> {
        val root = JSONObject(file.readText())
        val source = root.optJSONObject("model")?.optJSONObject("vocab") ?: root
        return source.keys().asSequence().associateWith(source::getInt)
    }

    private fun parsePiperPhonemeIds(file: File): Map<String, List<Int>> {
        val source = JSONObject(file.readText()).getJSONObject("phoneme_id_map")
        return source.keys().asSequence().associateWith { key ->
            val values = source.getJSONArray(key)
            List(values.length()) { index -> values.getInt(index) }
        }
    }

    private fun OrtSession.Result.samples(): FloatArray {
        val tensor = get(0) as? OnnxTensor ?: error("Voice model returned a non-tensor output")
        val buffer = tensor.floatBuffer
        val output = FloatArray(buffer.remaining())
        buffer.get(output)
        return output
    }

    override fun close() {
        session?.close()
        session = null
        sessionPath = null
        phonemizer.close()
        memoryCache.clear()
    }

    private companion object {
        const val MAX_MEMORY_CACHE_ENTRIES = 24
    }
}

private class NativeRoadTestEspeakPhonemizer(
    private val context: Context,
    private val documentsDirectory: File,
) : Closeable {
    private val mutex = Mutex()
    private var initialized = false

    suspend fun phonemize(text: String, languageCode: String): String = mutex.withLock {
        if (!initialized) {
            ensureDataExtracted()
            check(NativeEspeakBridge.initialize(documentsDirectory.absolutePath) >= 0) {
                "eSpeak initialization failed"
            }
            initialized = true
        }
        NativeEspeakBridge.phonemize(
            text = text,
            voice = NativeVoiceCatalog.eSpeakVoice(languageCode),
        )
    }

    private fun ensureDataExtracted() {
        val sentinel = File(documentsDirectory, NativeVoiceCatalog.ESPEAK_SENTINEL_PATH)
        if (sentinel.isFile) return
        documentsDirectory.mkdirs()
        // AAPT expands .gz assets at package time and exposes this entry as a
        // raw tar. The source gzip is checksum-verified by Gradle before that
        // deterministic transform.
        context.assets.open("espeak-ng-data.tar").use { archive ->
            extractTar(archive, documentsDirectory)
        }
        sentinel.parentFile?.mkdirs()
        FileOutputStream(sentinel).use { output ->
            output.write(byteArrayOf(1))
            output.fd.sync()
        }
    }

    private fun extractTar(input: InputStream, root: File) {
        val canonicalRoot = root.canonicalFile
        val header = ByteArray(TAR_BLOCK_BYTES)
        while (readBlock(input, header)) {
            if (header.all { it == 0.toByte() }) return
            val name = tarString(header, 0, 100)
            val prefix = tarString(header, 345, 155)
            val relative = if (prefix.isEmpty()) name else "$prefix/$name"
            require(relative.isNotEmpty() && !relative.startsWith('/'))
            val size = tarOctal(header, 124, 12)
            val target = File(canonicalRoot, relative).canonicalFile
            require(target.path.startsWith(canonicalRoot.path + File.separator)) {
                "eSpeak archive entry escaped its extraction root"
            }
            when (header[156].toInt().toChar()) {
                '0', '\u0000' -> {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> copyExactly(input, output, size) }
                }
                '5' -> {
                    target.mkdirs()
                    skipExactly(input, size)
                }
                else -> error("Unsupported eSpeak archive entry")
            }
            val padding = (TAR_BLOCK_BYTES - (size % TAR_BLOCK_BYTES)) % TAR_BLOCK_BYTES
            skipExactly(input, padding)
        }
    }

    private fun readBlock(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) {
                require(offset == 0) { "Truncated eSpeak archive header" }
                return false
            }
            offset += count
        }
        return true
    }

    private fun copyExactly(input: InputStream, output: FileOutputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(count > 0) { "Truncated eSpeak archive file" }
            output.write(buffer, 0, count)
            remaining -= count
        }
        output.fd.sync()
    }

    private fun skipExactly(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                require(input.read() >= 0) { "Truncated eSpeak archive padding" }
                remaining--
            }
        }
    }

    private fun tarString(bytes: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { bytes[it] == 0.toByte() }
            ?: offset + length
        return bytes.copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun tarOctal(bytes: ByteArray, offset: Int, length: Int): Long {
        val value = tarString(bytes, offset, length).trim()
        return if (value.isEmpty()) 0L else value.toLong(8)
    }

    override fun close() = Unit

    private companion object {
        const val TAR_BLOCK_BYTES = 512
    }
}

private object NativeEspeakBridge {
    init {
        System.loadLibrary("roadstr_voice_jni")
    }

    fun initialize(dataParent: String): Int = nativeInitialize(dataParent.toByteArray(Charsets.UTF_8))

    fun phonemize(text: String, voice: String): String {
        val bytes = nativePhonemize(
            text.toByteArray(Charsets.UTF_8),
            voice.toByteArray(Charsets.UTF_8),
        )
        return bytes.toString(Charsets.UTF_8).trim()
    }

    private external fun nativeInitialize(dataParent: ByteArray): Int
    private external fun nativePhonemize(text: ByteArray, voice: ByteArray): ByteArray
}

private class NativeRoadTestPcmPlayer(context: Context) : Closeable {
    private val focus = NativeVoiceAudioFocusController(context)
    private val playbackLock = Mutex()
    private val trackLock = Any()
    private var track: AudioTrack? = null

    suspend fun play(audio: NativePcmAudio, volume: Double) = playbackLock.withLock {
        val focusState = focus.request()
        val focusReady = when (focusState) {
            NativeVoiceAudioFocusState.Held -> true
            NativeVoiceAudioFocusState.Pending -> withTimeoutOrNull(FOCUS_WAIT_MILLIS) {
                focus.state.first {
                    it == NativeVoiceAudioFocusState.Held ||
                        it == NativeVoiceAudioFocusState.Denied ||
                        it == NativeVoiceAudioFocusState.Released
                }
            } == NativeVoiceAudioFocusState.Held
            else -> false
        }
        if (!focusReady) return
        val samples = ShortArray(audio.samples.size) { index ->
            (audio.samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            audio.sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0)
        val activeTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuffer, PLAYBACK_CHUNK_SAMPLES * Short.SIZE_BYTES))
            .build()
        synchronized(trackLock) {
            track = activeTrack
        }
        try {
            activeTrack.setVolume(volume.toFloat())
            activeTrack.play()
            withContext(Dispatchers.IO) {
                var offset = 0
                while (offset < samples.size) {
                    ensureActive()
                    val count = activeTrack.write(
                        samples,
                        offset,
                        minOf(PLAYBACK_CHUNK_SAMPLES, samples.size - offset),
                        AudioTrack.WRITE_BLOCKING,
                    )
                    ensureActive()
                    check(count > 0) { "AudioTrack rejected voice PCM" }
                    offset += count
                }
            }
            while (activeTrack.playbackHeadPosition.toLong() < samples.size.toLong()) {
                when (focus.state.value) {
                    NativeVoiceAudioFocusState.LostTransiently -> activeTrack.pause()
                    NativeVoiceAudioFocusState.Held -> if (activeTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        activeTrack.play()
                    }
                    NativeVoiceAudioFocusState.Denied,
                    NativeVoiceAudioFocusState.Released,
                    -> return
                    NativeVoiceAudioFocusState.Pending -> Unit
                }
                delay(PLAYBACK_POLL_MILLIS)
            }
        } finally {
            synchronized(trackLock) {
                if (track === activeTrack) track = null
            }
            runCatching { activeTrack.stop() }
            activeTrack.release()
            focus.release()
        }
    }

    fun stop() {
        val active = synchronized(trackLock) { track.also { track = null } }
        active?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
        }
        focus.release()
    }

    fun releaseFocus() = focus.release()

    override fun close() = stop()

    private companion object {
        const val FOCUS_WAIT_MILLIS = 3_000L
        const val PLAYBACK_CHUNK_SAMPLES = 8_192
        const val PLAYBACK_POLL_MILLIS = 20L
    }
}
