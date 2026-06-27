package com.example.nostr_nav

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * FlutterFragmentActivity richiesta da amberflutter (NIP-55).
 *
 * Riconoscimento vocale via SpeechRecognizer API (senza ACTION_RECOGNIZE_SPEECH
 * intent, che richiede Google App).
 *
 * Usa createSpeechRecognizer (servizio sistema Samsung) — NON l'on-device
 * recognizer che su Android 14/Samsung causa ERROR_TOO_MANY_REQUESTS (10).
 */
class MainActivity : FlutterFragmentActivity() {

    private val speechChannel = "roadstr/speech"
    private var recognizer: SpeechRecognizer? = null
    private var pendingResult: MethodChannel.Result? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, speechChannel)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "listen" -> startListening(
                        call.argument("lang") ?: "it-IT", result)
                    "stop"   -> { cleanup(); result.success(null) }
                    else     -> result.notImplemented()
                }
            }
    }

    /** Ferma e distrugge il riconoscitore in modo sicuro. */
    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel()  } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        pendingResult?.success("")
        pendingResult = null
    }

    private fun startListening(lang: String, result: MethodChannel.Result) {
        // Cancella pending precedente e distruggi il vecchio riconoscitore
        pendingResult?.success("")
        pendingResult = result
        cleanup()

        // Delay 150 ms: dà tempo al servizio di rilasciare la sessione precedente
        // (evita ERROR_TOO_MANY_REQUESTS = 10 su Samsung Galaxy Android 14)
        handler.postDelayed({
            if (pendingResult == null) return@postDelayed // già annullato

            // Usa il riconoscitore di sistema (Samsung Voice Input sul Galaxy S24)
            // NON createOnDeviceSpeechRecognizer che causa error 10 su Samsung
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            if (recognizer == null) {
                pendingResult?.error(
                    "STT_UNAVAILABLE",
                    "Nessun servizio di riconoscimento vocale disponibile sul dispositivo.",
                    null
                )
                pendingResult = null
                return@postDelayed
            }

            recognizer?.setRecognitionListener(object : RecognitionListener {

                override fun onResults(bundle: Bundle?) {
                    val matches = bundle?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)
                    pendingResult?.success(matches?.firstOrNull() ?: "")
                    pendingResult = null
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        1  -> "Timeout di rete"
                        2  -> "Errore di rete"
                        3  -> "Errore accesso microfono: controlla i permessi"
                        4  -> "Errore server"
                        5  -> "Errore client"
                        6  -> "Timeout: nessun parlato rilevato — riprova"
                        7  -> "Nessun risultato: parla più chiaramente"
                        8  -> "Riconoscitore occupato: riprova tra un momento"
                        9  -> "Permesso RECORD_AUDIO mancante"
                        10 -> "Troppe richieste: riprova tra qualche secondo"
                        11 -> "Server disconnesso"
                        12 -> "Lingua non supportata"
                        13 -> "Lingua non disponibile"
                        else -> "Errore riconoscimento vocale ($error)"
                    }
                    pendingResult?.error("STT_ERROR", msg, null)
                    pendingResult = null
                }

                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(i: Int, b: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // Auto-stop dopo silenzio (ms)
                putExtra(RecognizerIntent
                    .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent
                    .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            try {
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                pendingResult?.error("STT_ERROR", e.message, null)
                pendingResult = null
            }
        }, 150L) // delay 150 ms
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
