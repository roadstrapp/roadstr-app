package app.roadstr.feature.offline

import app.roadstr.service.offline.OfflineDownloadResponse
import app.roadstr.service.offline.OfflinePackageDownloadTransport
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineMapProviderClientTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `provider catalog merges countries regions and bundled cities`() = runBlocking {
        val index = """{
          "features": [
            {"properties":{"id":"europe","parent":null,"name":"Europe","urls":{}},"geometry":{"coordinates":[[[0,0],[20,0],[20,60],[0,60],[0,0]]]}},
            {"properties":{"id":"italy","parent":"europe","name":"Italy","iso3166-1:alpha2":["IT"],"urls":{"pbf":"https://download.geofabrik.de/europe/italy-latest.osm.pbf"}},"geometry":{"coordinates":[[[6,36],[19,36],[19,48],[6,48],[6,36]]]}},
            {"properties":{"id":"nord-est","parent":"italy","name":"Nord-Est","urls":{"pbf":"https://download.geofabrik.de/europe/italy/nord-est-latest.osm.pbf"}},"geometry":{"coordinates":[[[10,43],[14,43],[14,47],[10,47],[10,43]]]}}
          ]
        }""".trimIndent().toByteArray()
        val places = "A\tIT\t05\tEmilia-Romagna\nC\t3169361\tRimini\tRimini\t44.05755\t12.56528\tIT\t05\t148688\n".toByteArray()
        val transport = QueueTransport(response(index))
        val client = OfflineMapProviderClient(temporary.newFolder(), transport) { ByteArrayInputStream(places) }

        val catalog = client.loadGeofabrik()

        assertEquals(1, catalog.manifest.artifacts.size)
        assertEquals("gf-nord-est", catalog.manifest.artifacts.single().catalogAreaId)
        val rimini = OfflineGeographicCatalog.search(catalog.root, "Rimini").single().node
        assertEquals(setOf("gf-nord-est"), rimini.packageAreaIds)
        assertTrue(OfflineGeographicCatalog.plan(rimini, catalog.manifest.artifacts, emptyList()).artifacts.isNotEmpty())
    }

    @Test
    fun `provider refresh replaces an existing cached index`() = runBlocking {
        val index = """{
          "features": [
            {"properties":{"id":"europe","parent":null,"name":"Europe","urls":{}},"geometry":{"coordinates":[[[0,0],[20,0],[20,60],[0,60],[0,0]]]}},
            {"properties":{"id":"malta","parent":"europe","name":"Malta","iso3166-1:alpha2":["MT"],"urls":{"pbf":"https://download.geofabrik.de/europe/malta-latest.osm.pbf"}},"geometry":{"coordinates":[[[14,35],[15,35],[15,36],[14,36],[14,35]]]}}
          ]
        }""".trimIndent().toByteArray()
        val cache = temporary.newFolder()
        java.io.File(cache, "geofabrik-index-v1.json").writeText("stale")
        val client = OfflineMapProviderClient(cache, QueueTransport(response(index)))

        val catalog = client.loadGeofabrik()

        assertEquals(false, catalog.loadedFromCache)
        assertEquals("geofabrik-malta-shortbread", catalog.manifest.artifacts.single().id)
    }

    @Test
    fun `provider package is probed for exact size and validator`() = runBlocking {
        val transport = QueueTransport(
            OfflineDownloadResponse(200, 507_740_160L, null, "etag-1", null, null, ByteArrayInputStream(byteArrayOf())),
        )
        val client = OfflineMapProviderClient(temporary.newFolder(), transport)
        val artifact = providerArtifact()

        val prepared = client.prepareForInstall(artifact)

        assertEquals(507_740_160L, prepared.sizeBytes)
        assertEquals("etag-1", prepared.sourceValidator)
    }

    private fun providerArtifact() = app.roadstr.service.offline.OfflinePackageArtifact(
        id = "geofabrik-nord-est-shortbread",
        version = 1,
        datasetType = app.roadstr.service.offline.OfflineDatasetType.MbTilesMap,
        area = app.roadstr.service.offline.OfflineCoverageArea.Polygon(listOf(listOf(
            app.roadstr.service.offline.OfflineCoveragePoint(0.0, 0.0),
            app.roadstr.service.offline.OfflineCoveragePoint(0.0, 1.0),
            app.roadstr.service.offline.OfflineCoveragePoint(1.0, 1.0),
            app.roadstr.service.offline.OfflineCoveragePoint(1.0, 0.0),
            app.roadstr.service.offline.OfflineCoveragePoint(0.0, 0.0),
        ))),
        levels = setOf(0, 14),
        sizeBytes = 0,
        installedSizeBytes = 0,
        sha256 = null,
        url = "https://download.geofabrik.de/europe/italy/nord-est-shortbread-1.0.mbtiles",
        license = "ODbL 1.0",
        attribution = "OpenStreetMap contributors",
        build = app.roadstr.service.offline.OfflinePackageBuild(
            "geofabrik-shortbread", "shortbread-1.0", "test", "shortbread-1.0",
        ),
    )

    private fun response(bytes: ByteArray) = OfflineDownloadResponse(
        200, bytes.size.toLong(), null, "catalog-v1", null, null, ByteArrayInputStream(bytes),
    )

    private class QueueTransport(vararg values: OfflineDownloadResponse) : OfflinePackageDownloadTransport {
        private val queue = ArrayDeque(values.toList())
        override fun open(url: String, rangeStart: Long, ifRange: String?): OfflineDownloadResponse = queue.removeFirst()
    }
}
