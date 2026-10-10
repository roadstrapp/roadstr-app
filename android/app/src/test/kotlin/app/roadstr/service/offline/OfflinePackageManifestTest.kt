package app.roadstr.service.offline

import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.service.routing.RoutingCoverageResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OfflinePackageManifestTest {
    @Test
    fun `bounded manifest decodes routing artifact`() {
        val manifest = OfflinePackageManifestProtocol.decode(manifest().toByteArray())

        assertEquals(1, manifest.schemaVersion)
        assertEquals("routing-example", manifest.artifacts.single().id)
        assertEquals(456L, manifest.artifacts.single().installedSizeBytes)
        assertEquals("config-1", manifest.artifacts.single().build.compatibilityId)
    }

    @Test
    fun `manifest rejects insecure urls credentials duplicate ids and excessive bytes`() {
        assertThrows(OfflineManifestException::class.java) {
            OfflinePackageManifestProtocol.decode(manifest().replace("https://", "http://").toByteArray())
        }
        assertThrows(OfflineManifestException::class.java) {
            OfflinePackageManifestProtocol.decode(
                manifest().replace("packages.example", "user:secret@packages.example").toByteArray(),
            )
        }
        val entry = artifact()
        assertThrows(OfflineManifestException::class.java) {
            OfflinePackageManifestProtocol.decode(envelope("$entry,$entry").toByteArray())
        }
        assertThrows(OfflineManifestException::class.java) {
            OfflinePackageManifestProtocol.decode(ByteArray(OfflinePackageManifestProtocol.MAX_MANIFEST_BYTES + 1))
        }
    }

    @Test
    fun `manifest rejects malformed geometry and unsupported schema`() {
        assertThrows(OfflineManifestException::class.java) {
            OfflinePackageManifestProtocol.decode(manifest().replace("[0,0]]", "[0,1]]").toByteArray())
        }
        assertThrows(OfflineManifestException::class.java) {
            OfflinePackageManifestProtocol.decode(manifest().replace("\"schemaVersion\":1", "\"schemaVersion\":2").toByteArray())
        }
    }

    @Test
    fun `coverage requires one complete continuously covered package`() {
        val artifact = OfflinePackageManifestProtocol.decode(manifest().toByteArray()).artifacts.single()
        val index = OfflineCoverageIndex(
            listOf(InstalledOfflinePackage(artifact, "/private/routing.tar", 1L)),
        )

        assertEquals(RoutingCoverageResult.Covered, index.coversPoint(RoutingRequestPoint(0.5, 0.5)))
        assertEquals(RoutingCoverageResult.NotCovered, index.coversPoint(RoutingRequestPoint(4.0, 4.0)))
        assertEquals(
            RoutingCoverageResult.Covered,
            index.coversRoute(listOf(RoutingRequestPoint(0.1, 0.1), RoutingRequestPoint(0.9, 0.9))),
        )
        assertEquals(
            RoutingCoverageResult.NotCovered,
            index.coversRoute(listOf(RoutingRequestPoint(0.1, 0.1), RoutingRequestPoint(2.0, 2.0))),
        )
    }

    @Test
    fun `adjacent packages are not falsely stitched into one extract`() {
        val first = OfflinePackageManifestProtocol.decode(manifest().toByteArray()).artifacts.single()
        val second = first.copy(
            id = "routing-adjacent",
            area = OfflineCoverageArea.Polygon(
                listOf(
                    listOf(
                        OfflineCoveragePoint(0.0, 1.0),
                        OfflineCoveragePoint(0.0, 2.0),
                        OfflineCoveragePoint(1.0, 2.0),
                        OfflineCoveragePoint(1.0, 1.0),
                        OfflineCoveragePoint(0.0, 1.0),
                    ),
                ),
            ),
        )
        val index = OfflineCoverageIndex(
            listOf(
                InstalledOfflinePackage(first, "/private/one.tar", 1L),
                InstalledOfflinePackage(second, "/private/two.tar", 2L),
            ),
        )

        assertEquals(
            RoutingCoverageResult.NotCovered,
            index.coversRoute(listOf(RoutingRequestPoint(0.5, 0.5), RoutingRequestPoint(0.5, 1.5))),
        )
    }

    private fun manifest(): String = envelope(artifact())

    private fun envelope(artifacts: String): String =
        """{"schemaVersion":1,"generatedAt":"2026-10-09T10:00:00Z","artifacts":[$artifacts]}"""

    private fun artifact(): String = """
        {"id":"routing-example","version":1,"datasetType":"valhalla-routing",
        "area":{"kind":"polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]},
        "levels":[0,1,2],"sizeBytes":123,"installedSizeBytes":456,
        "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "url":"https://packages.example/routing.tar","license":"ODbL-1.0",
        "attribution":"OpenStreetMap contributors",
        "build":{"tool":"valhalla","version":"3.9.1","recipe":"auto-production","compatibilityId":"config-1"}}
    """.trimIndent()
}
