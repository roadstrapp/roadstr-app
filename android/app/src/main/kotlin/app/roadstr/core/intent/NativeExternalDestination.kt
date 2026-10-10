package app.roadstr.core.intent

import com.squareup.moshi.JsonReader
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.Buffer

/** A destination supplied explicitly by another app, never by an implicit share target. */
data class NativeExternalDestination(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

/** Monotonic identity lets Compose consume the same coordinates more than once on separate sends. */
data class NativeExternalDestinationRequest(
    val revision: Long,
    val destination: NativeExternalDestination,
)

/**
 * The small Android inter-app contract used by Tankful.
 *
 * Only an explicit ACTION_SEND carrying application/json in EXTRA_TEXT is accepted. The JSON is a
 * closed object: {"label","lat","lon"}. Keeping Android's Intent type outside this parser makes
 * the untrusted boundary deterministic and directly unit-testable on the JVM.
 */
object NativeExternalDestinationProtocol {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val MIME_JSON = "application/json"
    const val MAX_PAYLOAD_BYTES = 4_096
    const val MAX_LABEL_CHARS = 300

    private val names = JsonReader.Options.of("label", "lat", "lon")

    fun decode(action: String?, mimeType: String?, payload: String?): NativeExternalDestination? {
        if (action != ACTION_SEND) return null
        val mediaType = mimeType?.substringBefore(';')?.trim()
        if (!mediaType.equals(MIME_JSON, ignoreCase = true)) return null
        return decodePayload(payload)
    }

    fun decodePayload(payload: String?): NativeExternalDestination? {
        if (payload.isNullOrBlank()) return null
        if (payload.toByteArray(StandardCharsets.UTF_8).size > MAX_PAYLOAD_BYTES) return null
        return runCatching {
            JsonReader.of(Buffer().writeUtf8(payload)).use { reader ->
                reader.isLenient = false
                if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return@use null
                reader.beginObject()
                var label: String? = null
                var latitude: Double? = null
                var longitude: Double? = null
                var seenLabel = false
                var seenLatitude = false
                var seenLongitude = false
                while (reader.hasNext()) {
                    when (reader.selectName(names)) {
                        0 -> {
                            if (seenLabel || reader.peek() != JsonReader.Token.STRING) return@use null
                            seenLabel = true
                            label = reader.nextString()
                        }
                        1 -> {
                            if (seenLatitude || reader.peek() != JsonReader.Token.NUMBER) return@use null
                            seenLatitude = true
                            latitude = reader.nextDouble()
                        }
                        2 -> {
                            if (seenLongitude || reader.peek() != JsonReader.Token.NUMBER) return@use null
                            seenLongitude = true
                            longitude = reader.nextDouble()
                        }
                        else -> return@use null
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonReader.Token.END_DOCUMENT) return@use null
                val normalizedLabel = label?.trim()?.takeIf {
                    it.isNotEmpty() &&
                        it.length <= MAX_LABEL_CHARS &&
                        it.none { character -> Character.isISOControl(character) }
                } ?: return@use null
                val lat = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return@use null
                val lon = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return@use null
                NativeExternalDestination(normalizedLabel, lat, lon)
            }
        }.getOrNull()
    }
}

/** A one-item, latest-wins inbox for cold-start and singleTop Intent delivery. */
class NativeExternalDestinationInbox {
    private val lock = Any()
    private val mutableRequests = MutableStateFlow<NativeExternalDestinationRequest?>(null)
    private var revision = 0L

    val requests: StateFlow<NativeExternalDestinationRequest?> = mutableRequests.asStateFlow()

    fun offer(action: String?, mimeType: String?, payload: String?): Boolean {
        val destination = NativeExternalDestinationProtocol.decode(action, mimeType, payload) ?: return false
        synchronized(lock) {
            revision = if (revision == Long.MAX_VALUE) 1L else revision + 1L
            mutableRequests.value = NativeExternalDestinationRequest(revision, destination)
        }
        return true
    }

    fun consume(requestRevision: Long): Boolean = synchronized(lock) {
        if (mutableRequests.value?.revision != requestRevision) return false
        mutableRequests.value = null
        true
    }
}
