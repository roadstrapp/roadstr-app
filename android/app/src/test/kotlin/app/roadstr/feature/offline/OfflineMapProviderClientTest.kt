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
    fun `only areas with a Shortbread extract are offered, whatever their level`() = runBlocking {
        // As published on 2026-10-28: Czechia has one file for the country and none for its
        // regions, Poland one per voivodeship and none for the country, Slovakia one.
        val index = """{
          "features": [
            {"properties":{"id":"europe","parent":null,"name":"Europe","urls":{}},"geometry":{"coordinates":[[[0,40],[30,40],[30,60],[0,60],[0,40]]]}},
            {"properties":{"id":"czech-republic","parent":"europe","name":"Czech Republic","iso3166-1:alpha2":["CZ"],"urls":{"pbf":"https://download.geofabrik.de/europe/czech-republic-latest.osm.pbf"}},"geometry":{"coordinates":[[[12,48],[19,48],[19,51],[12,51],[12,48]]]}},
            {"properties":{"id":"praha","parent":"czech-republic","name":"Praha","urls":{"pbf":"https://download.geofabrik.de/europe/czech-republic/praha-latest.osm.pbf"}},"geometry":{"coordinates":[[[14,49.9],[14.8,49.9],[14.8,50.2],[14,50.2],[14,49.9]]]}},
            {"properties":{"id":"jihocesky","parent":"czech-republic","name":"Jihocesky","urls":{"pbf":"https://download.geofabrik.de/europe/czech-republic/jihocesky-latest.osm.pbf"}},"geometry":{"coordinates":[[[13,48.5],[15,48.5],[15,49.6],[13,49.6],[13,48.5]]]}},
            {"properties":{"id":"slovakia","parent":"europe","name":"Slovakia","iso3166-1:alpha2":["SK"],"urls":{"pbf":"https://download.geofabrik.de/europe/slovakia-latest.osm.pbf"}},"geometry":{"coordinates":[[[16.8,47.7],[22.6,47.7],[22.6,49.6],[16.8,49.6],[16.8,47.7]]]}},
            {"properties":{"id":"poland","parent":"europe","name":"Poland","iso3166-1:alpha2":["PL"],"urls":{"pbf":"https://download.geofabrik.de/europe/poland-latest.osm.pbf"}},"geometry":{"coordinates":[[[14,49],[24,49],[24,55],[14,55],[14,49]]]}},
            {"properties":{"id":"mazowieckie","parent":"poland","name":"Mazowieckie","urls":{"pbf":"https://download.geofabrik.de/europe/poland/mazowieckie-latest.osm.pbf"}},"geometry":{"coordinates":[[[19,51],[23,51],[23,53.5],[19,53.5],[19,51]]]}}
          ]
        }""".trimIndent().toByteArray()
        val missing = "# probed\npraha\njihocesky\npoland\n".toByteArray()
        val client = OfflineMapProviderClient(
            temporary.newFolder(),
            QueueTransport(response(index)),
            missingShortbread = { ByteArrayInputStream(missing) },
        )

        val catalog = client.loadGeofabrik()
        val artifacts = catalog.manifest.artifacts

        assertEquals(
            setOf("geofabrik-czech-republic-shortbread", "geofabrik-slovakia-shortbread", "geofabrik-mazowieckie-shortbread"),
            artifacts.map { it.id }.toSet(),
        )
        val czech = artifacts.single { it.catalogAreaId == "cz" }
        assertEquals("https://download.geofabrik.de/europe/czech-republic-shortbread-1.0.mbtiles", czech.url)
        val byName = OfflineGeographicCatalog.search(catalog.root, "Czech Republic").first()
        assertEquals("cz", byName.node.id)
        assertEquals("europe-east", byName.path.dropLast(1).last().id)
        assertEquals(listOf(czech), OfflineGeographicCatalog.plan(byName.node, artifacts, emptyList()).artifacts)
        // A region with no file of its own downloads the country it belongs to.
        val praha = OfflineGeographicCatalog.find(catalog.root, "gf-praha")!!
        assertEquals(listOf(czech), OfflineGeographicCatalog.plan(praha, artifacts, emptyList()).artifacts)
        val slovakia = OfflineGeographicCatalog.search(catalog.root, "Slovakia").first().node
        assertEquals("sk", slovakia.id)
        assertEquals(
            OfflineCatalogAvailability.Available,
            OfflineGeographicCatalog.plan(slovakia, artifacts, emptyList()).availability,
        )
    }

    @Test
    fun `a city goes to the area whose outline holds it, not whose box does`() = runBlocking {
        // England's outline is a triangle here; its box also spans the town to the west, which
        // only the Irish area really contains (as with Northern Ireland and Geofabrik's Ireland).
        val index = """{
          "features": [
            {"properties":{"id":"europe","parent":null,"name":"Europe","urls":{}},"geometry":{"coordinates":[[[-11,49],[2,49],[2,59],[-11,59],[-11,49]]]}},
            {"properties":{"id":"united-kingdom","parent":"europe","name":"United Kingdom","iso3166-1:alpha2":["GB"],"urls":{"pbf":"https://download.geofabrik.de/europe/united-kingdom-latest.osm.pbf"}},"geometry":{"coordinates":[[[-8,49],[2,49],[2,59],[-8,59],[-8,49]]]}},
            {"properties":{"id":"england","parent":"united-kingdom","name":"England","urls":{"pbf":"https://download.geofabrik.de/europe/united-kingdom/england-latest.osm.pbf"}},"geometry":{"coordinates":[[[[2,50],[2,56],[-7,50],[2,50]]]]}},
            {"properties":{"id":"ireland-and-northern-ireland","parent":"europe","name":"Ireland and Northern Ireland","iso3166-1:alpha2":["IE"],"urls":{"pbf":"https://download.geofabrik.de/europe/ireland-and-northern-ireland-latest.osm.pbf"}},"geometry":{"coordinates":[[[[-11,51],[-5,51],[-5,56],[-11,56],[-11,51]]]]}}
          ]
        }""".trimIndent().toByteArray()
        val places = "A\tGB\tNIR\tNorthern Ireland\nC\t2656407\tBanbridge\tBanbridge\t54.35\t-6.27\tGB\tNIR\t16000\n".toByteArray()
        val client = OfflineMapProviderClient(temporary.newFolder(), QueueTransport(response(index))) {
            ByteArrayInputStream(places)
        }

        val catalog = client.loadGeofabrik()

        val banbridge = OfflineGeographicCatalog.search(catalog.root, "Banbridge").single().node
        assertEquals(setOf("ie"), banbridge.packageAreaIds)
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
