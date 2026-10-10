package app.roadstr.feature.offline

import app.roadstr.service.offline.OfflineCoverageArea
import app.roadstr.service.offline.OfflineCoveragePoint
import app.roadstr.service.offline.OfflineDatasetType
import app.roadstr.service.offline.OfflinePackageArtifact
import app.roadstr.service.offline.OfflinePackageBuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGeographicCatalogTest {
    @Test
    fun `world starts with five navigable continents`() {
        val root = OfflineGeographicCatalog.world()

        assertEquals(listOf("europe", "asia", "africa", "america", "oceania"), root.children.map { it.id })
        assertTrue(OfflineGeographicCatalog.search(root, "Rimini").single().path.map { it.id }.contains("it-emilia-romagna"))
    }

    @Test
    fun `search preserves full path for duplicate names`() {
        val root = OfflineGeographicCatalog.world()

        val results = OfflineGeographicCatalog.search(root, "Southern")

        assertTrue(results.any { it.path.joinToString("/") { node -> node.id } == "world/europe/europe-west/de/de-south" })
        assertTrue(results.any { it.path.joinToString("/") { node -> node.id } == "world/europe/europe-south/it/it-south" })
    }

    @Test
    fun `composed parent plan deduplicates overlapping child packages`() {
        val root = OfflineGeographicCatalog.world()
        val packageForEmilia = artifact("emilia-package", "it-emilia-romagna", 100L)
        val packageForNorth = artifact("northern-package", "it-north", 100L)
        val packageForCentral = artifact("central-package", "it-central", 100L)
        val packageForSouth = artifact("south-package", "it-south", 100L)

        val plan = OfflineGeographicCatalog.plan(
            OfflineGeographicCatalog.find(root, "it")!!,
            listOf(packageForEmilia, packageForNorth, packageForCentral, packageForSouth),
            emptyList(),
        )

        assertEquals(3, plan.artifacts.size)
        assertEquals(OfflineCatalogAvailability.Available, plan.availability)
        val selection = OfflineGeographicCatalog.selection(
            root,
            setOf("it", "it-north"),
            listOf(packageForEmilia, packageForNorth, packageForCentral, packageForSouth),
            emptyList(),
        )
        assertEquals(3, selection.artifacts.size)
        assertEquals(300L, selection.downloadBytes)
    }

    private fun artifact(id: String, areaId: String, size: Long) = OfflinePackageArtifact(
        id = id,
        version = 1,
        datasetType = OfflineDatasetType.PmTilesMap,
        area = OfflineCoverageArea.Polygon(
            listOf(
                listOf(
                    OfflineCoveragePoint(0.0, 0.0),
                    OfflineCoveragePoint(0.0, 1.0),
                    OfflineCoveragePoint(1.0, 1.0),
                    OfflineCoveragePoint(1.0, 0.0),
                    OfflineCoveragePoint(0.0, 0.0),
                ),
            ),
        ),
        levels = setOf(0, 6, 14),
        sizeBytes = size,
        installedSizeBytes = size,
        sha256 = "a".repeat(64),
        url = "https://example.com/$id.pmtiles",
        license = "ODbL-1.0",
        attribution = "OpenStreetMap contributors",
        build = OfflinePackageBuild("test", "1", "test", "test"),
        catalogAreaId = areaId,
    )
}
