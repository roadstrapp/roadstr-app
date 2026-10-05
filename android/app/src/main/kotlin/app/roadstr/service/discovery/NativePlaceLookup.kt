package app.roadstr.service.discovery

import app.roadstr.core.discovery.AreaGeocoding
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.resolve.NominatimPlaceSearch
import app.roadstr.core.discovery.resolve.PlaceLookup
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeSearchHttpTransport
import java.util.concurrent.CancellationException

/**
 * The name lookups behind web results: each one goes through the shared Nominatim pacer, the
 * answer is remembered for a day, and any failure is just "nothing found".
 */
class NativePlaceLookup(
    private val transport: NativeSearchHttpTransport,
    private val pacer: HostPacer,
    private val cache: TtlCache<String, List<RoadstrPlace>> = TtlCache(32, NativeDiscoveryService.AREA_TTL_MILLIS),
) : PlaceLookup {
    override suspend fun find(
        name: String,
        locality: String?,
        near: GeoPoint,
        languageCode: String,
    ): List<RoadstrPlace> {
        val request = NominatimPlaceSearch.request(name, locality, near, languageCode) ?: return emptyList()
        cache.get(request.uri)?.let { return it }
        val response = try {
            pacer.paced { transport.execute(request, LIMITS) }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return emptyList()
        }
        if (response.statusCode != HTTP_OK) return emptyList()
        val places = NominatimPlaceSearch.parse(response.bodyUtf8, near, languageCode)
        cache.put(request.uri, places)
        return places
    }

    private companion object {
        const val HTTP_OK = 200
        val LIMITS = NativeHttpRequestLimits(8_000L, NetworkResponseLimit.SmallJson.bytes)
    }
}
