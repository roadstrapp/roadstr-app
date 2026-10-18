package app.roadstr.service.speech

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.speech.SpokenAddressRefiner
import app.roadstr.core.speech.StreetIndex
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [SpeechTextRefiner] for address searches: starts fetching the street names of the user's area when
 * listening begins, and by the time the user has finished speaking they are usually there. If they are
 * not within [waitMillis] the text is refined without them (numbers only) and the fetch carries on for
 * the next time.
 */
class AddressSpeechRefiner(
    private val gazetteer: StreetGazetteer,
    private val position: () -> GeoPoint?,
    private val scope: CoroutineScope,
    private val waitMillis: Long = 2_500L,
) : SpeechTextRefiner {
    private var pending: Deferred<StreetIndex?>? = null
    private var pendingPoint: GeoPoint? = null

    override suspend fun prepare(languageTag: String) {
        val point = position()
        val started = if (point == null) null else scope.async { gazetteer.load(point) }
        synchronized(this) {
            pendingPoint = point
            pending = started
        }
    }

    override suspend fun refine(text: String, languageTag: String): String {
        val index = streetIndex()
        val italian = languageTag.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT) == "it"
        return SpokenAddressRefiner.refine(text, index, italian)
    }

    private suspend fun streetIndex(): StreetIndex? {
        val waiting = synchronized(this) { pending }
        val fetched = if (waiting == null) null else withTimeoutOrNull(waitMillis) { waiting.await() }
        if (fetched != null) return fetched
        return synchronized(this) { pendingPoint }?.let(gazetteer::cached)
    }
}
