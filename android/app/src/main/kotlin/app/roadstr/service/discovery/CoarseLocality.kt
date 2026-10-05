package app.roadstr.service.discovery

import app.roadstr.core.discovery.AreaGeocoding
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeSearchHttpTransport
import java.util.concurrent.CancellationException

/**
 * Turns the user's position into the name of their town for a web query, so that "near me"
 * can be searched without telling a web service where they are. The point is rounded to
 * about a kilometre, the answer is remembered per grid cell, and the call goes through the
 * shared Nominatim pacer.
 */
class CoarseLocality(
    private val transport: NativeSearchHttpTransport,
    private val pacer: HostPacer,
    private val cache: TtlCache<String, String> = TtlCache(32, NativeDiscoveryService.AREA_TTL_MILLIS),
) {
    suspend fun nameOf(point: GeoPoint, languageCode: String): String? {
        val key = AreaGeocoding.localityCell(point) + "|" + languageCode
        cache.get(key)?.let { return it }
        val request = AreaGeocoding.localityRequest(point, languageCode)
        val response = try {
            pacer.paced { transport.execute(request, LIMITS) }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return null
        }
        if (response.statusCode != HTTP_OK) return null
        return AreaGeocoding.parseLocality(response.bodyUtf8)?.also { cache.put(key, it) }
    }

    private companion object {
        const val HTTP_OK = 200
        val LIMITS = NativeHttpRequestLimits(5_000L, NetworkResponseLimit.SmallJson.bytes)
    }
}
