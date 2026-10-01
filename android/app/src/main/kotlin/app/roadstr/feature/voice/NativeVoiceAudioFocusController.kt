package app.roadstr.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NativeVoiceAudioFocusState { Released, Pending, Held, LostTransiently, Denied }

/**
 * Native navigation-guidance focus contract. Calls are intentionally absent
 * from app startup until the voice cutover is explicitly enabled.
 */
class NativeVoiceAudioFocusController(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(NativeVoiceAudioFocusState.Released)
    private var focusRequest: AudioFocusRequest? = null

    val state: StateFlow<NativeVoiceAudioFocusState> = _state.asStateFlow()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        _state.value = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> NativeVoiceAudioFocusState.Held
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> NativeVoiceAudioFocusState.LostTransiently
            AudioManager.AUDIOFOCUS_LOSS -> NativeVoiceAudioFocusState.Released
            else -> _state.value
        }
    }

    @Suppress("DEPRECATION")
    fun request(): NativeVoiceAudioFocusState {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(listener)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
        _state.value = when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> NativeVoiceAudioFocusState.Held
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> NativeVoiceAudioFocusState.Pending
            else -> NativeVoiceAudioFocusState.Denied
        }
        return _state.value
    }

    @Suppress("DEPRECATION")
    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            audioManager.abandonAudioFocus(listener)
        }
        _state.value = NativeVoiceAudioFocusState.Released
    }
}
