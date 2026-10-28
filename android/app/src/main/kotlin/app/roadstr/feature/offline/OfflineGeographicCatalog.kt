package app.roadstr.feature.offline

import app.roadstr.service.offline.InstalledOfflinePackage
import app.roadstr.service.offline.OfflineCoverageArea
import app.roadstr.service.offline.OfflineDatasetType
import app.roadstr.service.offline.OfflinePackageArtifact

/** A stable, data-driven geographic node. Names are presentation data; ids are
 * the keys used by package manifests and must not be changed casually. */
data class OfflineCatalogNode(
    val id: String,
    val name: String,
    val kind: Kind,
    val children: List<OfflineCatalogNode> = emptyList(),
    val aliases: List<String> = emptyList(),
    /** Provider-area ids that cover this curated human-friendly node. */
    val packageAreaIds: Set<String> = emptySet(),
) {
    enum class Kind { Root, Continent, Macroregion, Country, Region, City }
}

data class OfflineCatalogPathResult(
    val node: OfflineCatalogNode,
    val path: List<OfflineCatalogNode>,
)

enum class OfflineCatalogAvailability { Unavailable, Partial, Available, Installed }

data class OfflineCatalogPlan(
    val artifacts: List<OfflinePackageArtifact>,
    val availability: OfflineCatalogAvailability,
)

data class OfflineCatalogDownloadPlan(
    val artifacts: List<OfflinePackageArtifact>,
    val downloadBytes: Long,
    val installedBytes: Long,
)

data class OfflineManifestArtifactGroup(
    val title: String,
    val artifacts: List<OfflinePackageArtifact>,
)

/** Curated navigation nodes are merged with the live provider hierarchy. */
object OfflineGeographicCatalog {
    /**
     * External manifests do not have to use Roadstr's curated catalogue ids.
     * Their coverage geometry is still part of the manifest contract, so use
     * its centroid to provide a useful geographic grouping and fall back to a
     * stable alphabetical bucket for malformed/unknown coverage.
     */
    fun groupManifestArtifacts(
        artifacts: List<OfflinePackageArtifact>,
        query: String = "",
    ): List<OfflineManifestArtifactGroup> {
        val needle = query.trim().lowercase()
        val filtered = artifacts.filter { artifact ->
            needle.isBlank() || listOfNotNull(
                artifact.label,
                artifact.id,
                artifact.url,
                artifact.catalogAreaId,
            ).joinToString(" ").lowercase().contains(needle)
        }
        val grouped = filtered.groupBy(::manifestGeographicGroup)
        return grouped.entries
            .sortedWith(compareBy({ manifestGroupOrder(it.key) }, { it.key }))
            .map { (title, values) ->
                OfflineManifestArtifactGroup(
                    title = title,
                    artifacts = values.sortedWith(
                        compareBy(
                            { it.label?.takeIf(String::isNotBlank) ?: it.id },
                            { it.id },
                        ),
                    ),
                )
            }
    }

    private fun manifestGeographicGroup(artifact: OfflinePackageArtifact): String {
        val points = when (val coverage = artifact.area) {
            is OfflineCoverageArea.Polygon -> coverage.rings.flatten()
            is OfflineCoverageArea.Corridor -> coverage.centreline
        }
        if (points.isEmpty()) return "Other"
        val latitude = points.sumOf { it.latitude } / points.size
        val longitude = points.sumOf { it.longitude } / points.size
        return when {
            latitude < -55.0 -> "Antarctica"
            latitude in -56.0..13.0 && longitude in -90.0..-30.0 -> "South America"
            latitude in 5.0..84.0 && longitude in -180.0..-20.0 -> "North America"
            latitude in -50.0..5.0 && longitude in 95.0..180.0 -> "Oceania"
            latitude in -35.0..37.0 && longitude in -20.0..55.0 -> "Africa"
            latitude in 35.0..82.0 && longitude in -25.0..45.0 -> "Europe"
            latitude in 5.0..82.0 && longitude in 25.0..180.0 -> "Asia"
            else -> "Other"
        }
    }

    private fun manifestGroupOrder(title: String): Int = listOf(
        "Europe", "Asia", "Africa", "North America", "South America", "Oceania", "Antarctica", "Other",
    ).indexOf(title).takeIf { it >= 0 } ?: Int.MAX_VALUE

    fun world(): OfflineCatalogNode = OfflineCatalogNode(
        id = "world",
        name = "World",
        kind = OfflineCatalogNode.Kind.Root,
        children = listOf(
            europe(),
            branch("asia", "Asia", OfflineCatalogNode.Kind.Continent, listOf(
                branch("asia-east", "East Asia", OfflineCatalogNode.Kind.Macroregion, listOf(
                    branch("jp", "Japan", OfflineCatalogNode.Kind.Country, listOf(
                        branch("jp-kansai", "Kansai", OfflineCatalogNode.Kind.Macroregion, listOf(
                            branch("jp-osaka-pref", "Osaka Prefecture", OfflineCatalogNode.Kind.Region, listOf(
                                leaf("jp-osaka", "Osaka", OfflineCatalogNode.Kind.City),
                            )),
                        )),
                    )),
                    leaf("kr", "South Korea", OfflineCatalogNode.Kind.Country),
                )),
                branch("asia-south", "South Asia", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("in", "India", OfflineCatalogNode.Kind.Country),
                )),
                branch("asia-west", "Western Asia", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("tr", "Türkiye", OfflineCatalogNode.Kind.Country),
                )),
            )),
            branch("africa", "Africa", OfflineCatalogNode.Kind.Continent, listOf(
                branch("africa-north", "Northern Africa", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("eg", "Egypt", OfflineCatalogNode.Kind.Country),
                    leaf("ma", "Morocco", OfflineCatalogNode.Kind.Country),
                )),
                branch("africa-south", "Southern Africa", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("za", "South Africa", OfflineCatalogNode.Kind.Country),
                )),
            )),
            branch("america", "America", OfflineCatalogNode.Kind.Continent, listOf(
                branch("north-america", "North America", OfflineCatalogNode.Kind.Macroregion, listOf(
                    branch("us", "United States", OfflineCatalogNode.Kind.Country, listOf(
                        branch("us-ca", "California", OfflineCatalogNode.Kind.Region, listOf(
                            leaf("us-ca-los-angeles", "Los Angeles", OfflineCatalogNode.Kind.City),
                        )),
                    )),
                    leaf("ca", "Canada", OfflineCatalogNode.Kind.Country),
                )),
                branch("central-america", "Central America", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("mx", "Mexico", OfflineCatalogNode.Kind.Country),
                )),
                branch("caribbean", "Caribbean", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("cu", "Cuba", OfflineCatalogNode.Kind.Country),
                )),
                branch("south-america", "South America", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("br", "Brazil", OfflineCatalogNode.Kind.Country),
                    leaf("ar", "Argentina", OfflineCatalogNode.Kind.Country),
                )),
            )),
            branch("oceania", "Oceania", OfflineCatalogNode.Kind.Continent, listOf(
                branch("au", "Australia", OfflineCatalogNode.Kind.Country, listOf(
                    leaf("au-nsw-sydney", "Sydney", OfflineCatalogNode.Kind.City),
                )),
                leaf("nz", "New Zealand", OfflineCatalogNode.Kind.Country),
            )),
        ),
    )

    fun search(root: OfflineCatalogNode, query: String): List<OfflineCatalogPathResult> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val results = mutableListOf<OfflineCatalogPathResult>()
        fun visit(node: OfflineCatalogNode, path: List<OfflineCatalogNode>) {
            val currentPath = path + node
            val haystack = (listOf(node.name) + node.aliases).joinToString(" ").lowercase()
            if (node.kind != OfflineCatalogNode.Kind.Root && haystack.contains(needle)) {
                results += OfflineCatalogPathResult(node, currentPath)
            }
            node.children.forEach { visit(it, currentPath) }
        }
        visit(root, emptyList())
        return results.sortedWith(compareBy({ it.node.name.lowercase() }, { it.path.joinToString("/") { n -> n.id } }))
    }

    fun resolve(root: OfflineCatalogNode, ids: List<String>): List<OfflineCatalogNode> {
        val path = mutableListOf<OfflineCatalogNode>()
        var current = root
        for (id in ids) {
            val next = current.children.firstOrNull { it.id == id } ?: break
            path += next
            current = next
        }
        return path
    }

    fun plan(
        node: OfflineCatalogNode,
        artifacts: List<OfflinePackageArtifact>,
        installed: List<InstalledOfflinePackage>,
    ): OfflineCatalogPlan {
        val coveredIds = node.packageAreaIds + node.id
        val direct = artifacts.filter { it.catalogAreaId in coveredIds }.distinctBy { it.id }
        if (direct.isNotEmpty()) {
            val installedIds = installed.mapTo(hashSetOf()) { it.artifact.id }
            return OfflineCatalogPlan(
                direct,
                if (direct.all { it.id in installedIds }) OfflineCatalogAvailability.Installed
                else OfflineCatalogAvailability.Available,
            )
        }
        if (node.children.isEmpty()) return OfflineCatalogPlan(emptyList(), OfflineCatalogAvailability.Unavailable)
        val childPlans = node.children.map { plan(it, artifacts, installed) }
        val childArtifacts = childPlans.flatMap { it.artifacts }.distinctBy { it.id }
        val availability = when {
            childPlans.all { it.availability == OfflineCatalogAvailability.Installed } -> OfflineCatalogAvailability.Installed
            childPlans.all { it.availability == OfflineCatalogAvailability.Available || it.availability == OfflineCatalogAvailability.Installed } -> OfflineCatalogAvailability.Available
            childPlans.any { it.availability != OfflineCatalogAvailability.Unavailable } -> OfflineCatalogAvailability.Partial
            else -> OfflineCatalogAvailability.Unavailable
        }
        return OfflineCatalogPlan(childArtifacts, availability)
    }

    fun selection(
        root: OfflineCatalogNode,
        selectedIds: Set<String>,
        artifacts: List<OfflinePackageArtifact>,
        installed: List<InstalledOfflinePackage>,
    ): OfflineCatalogDownloadPlan {
        val selected = selectedIds.flatMap { id -> find(root, id)?.let { plan(it, artifacts, installed).artifacts }.orEmpty() }
            .distinctBy { it.id }
        val installedIds = installed.mapTo(hashSetOf()) { it.artifact.id }
        return OfflineCatalogDownloadPlan(
            artifacts = selected,
            downloadBytes = selected.filterNot { it.id in installedIds }.sumOf { it.sizeBytes },
            installedBytes = selected.filterNot { it.id in installedIds }.sumOf { it.installedSizeBytes },
        )
    }

    fun find(root: OfflineCatalogNode, id: String): OfflineCatalogNode? {
        if (root.id == id) return root
        root.children.forEach { find(it, id)?.let { return it } }
        return null
    }

    /** Merges hundreds of provider nodes without duplicating countries that
     * already have a curated, friendlier place in the hierarchy. */
    fun mergeProviderWorld(providerWorld: OfflineCatalogNode): OfflineCatalogNode {
        val seed = world()
        val providerIndex = buildMap {
            fun visit(node: OfflineCatalogNode) {
                put(node.id, node)
                node.children.forEach(::visit)
            }
            visit(providerWorld)
        }
        fun merge(seedNode: OfflineCatalogNode): OfflineCatalogNode {
            val seedIds = buildSet {
                fun collect(node: OfflineCatalogNode) {
                    add(node.id)
                    node.children.forEach(::collect)
                }
                collect(seedNode)
            }
            val provider = providerIndex[seedNode.id]
            val curated = seedNode.children.map(::merge)
            fun prune(node: OfflineCatalogNode): OfflineCatalogNode? {
                if (node.id in seedIds) return null
                return node.copy(children = node.children.mapNotNull(::prune))
            }
            val additions = provider?.children.orEmpty().mapNotNull(::prune)
            return seedNode.copy(
                children = (curated + additions).distinctBy { it.id }.sortedBy { it.name.lowercase() },
                packageAreaIds = seedNode.packageAreaIds + provider?.packageAreaIds.orEmpty(),
            )
        }
        return merge(seed)
    }

    private fun europe() = branch("europe", "Europe", OfflineCatalogNode.Kind.Continent, listOf(
        branch("europe-north", "Northern Europe", OfflineCatalogNode.Kind.Macroregion, listOf(
            leaf("se", "Sweden", OfflineCatalogNode.Kind.Country),
            leaf("no", "Norway", OfflineCatalogNode.Kind.Country),
        )),
        branch("europe-west", "Western Europe", OfflineCatalogNode.Kind.Macroregion, listOf(
            branch("fr", "France", OfflineCatalogNode.Kind.Country, listOf(
                leaf("fr-north", "Northern France", OfflineCatalogNode.Kind.Macroregion, packageAreaIds = setOf(
                    "gf-nord-pas-de-calais", "gf-picardie", "gf-haute-normandie", "gf-basse-normandie",
                )),
            )),
            branch("de", "Germany", OfflineCatalogNode.Kind.Country, listOf(
                leaf("de-south", "Southern Germany", OfflineCatalogNode.Kind.Macroregion, packageAreaIds = setOf(
                    "gf-bayern", "gf-baden-wuerttemberg",
                )),
            )),
        )),
        branch("europe-south", "Southern Europe", OfflineCatalogNode.Kind.Macroregion, listOf(
            branch("it", "Italy", OfflineCatalogNode.Kind.Country, listOf(
                branch("it-north", "Northern Italy", OfflineCatalogNode.Kind.Macroregion, listOf(
                    branch("it-emilia-romagna", "Emilia-Romagna", OfflineCatalogNode.Kind.Region, listOf(
                        leaf("it-rimini", "Rimini", OfflineCatalogNode.Kind.City, packageAreaIds = setOf("gf-nord-est")),
                    ), aliases = listOf("Emilia Romagna"), packageAreaIds = setOf("gf-nord-est")),
                    leaf("it-lombardia", "Lombardy", OfflineCatalogNode.Kind.Region, packageAreaIds = setOf("gf-nord-ovest")),
                    leaf("it-veneto", "Veneto", OfflineCatalogNode.Kind.Region, packageAreaIds = setOf("gf-nord-est")),
                ), packageAreaIds = setOf("gf-nord-est", "gf-nord-ovest")),
                leaf("it-central", "Central Italy", OfflineCatalogNode.Kind.Macroregion, packageAreaIds = setOf("gf-centro")),
                leaf("it-south", "Southern Italy", OfflineCatalogNode.Kind.Macroregion, packageAreaIds = setOf("gf-sud", "gf-isole")),
            )),
            leaf("es", "Spain", OfflineCatalogNode.Kind.Country),
            leaf("gr", "Greece", OfflineCatalogNode.Kind.Country),
            branch("pt", "Portugal", OfflineCatalogNode.Kind.Country, listOf(
                branch("pt-south", "Southern Portugal", OfflineCatalogNode.Kind.Macroregion, listOf(
                    leaf("pt-algarve", "Algarve", OfflineCatalogNode.Kind.Region),
                )),
            )),
        )),
        branch("europe-east", "Eastern Europe", OfflineCatalogNode.Kind.Macroregion, listOf(
            leaf("cz", "Czechia", OfflineCatalogNode.Kind.Country, aliases = listOf("Czech Republic")),
            leaf("pl", "Poland", OfflineCatalogNode.Kind.Country),
            leaf("ro", "Romania", OfflineCatalogNode.Kind.Country),
            leaf("sk", "Slovakia", OfflineCatalogNode.Kind.Country),
        )),
    ))

    private fun leaf(
        id: String,
        name: String,
        kind: OfflineCatalogNode.Kind,
        aliases: List<String> = emptyList(),
        packageAreaIds: Set<String> = emptySet(),
    ) = OfflineCatalogNode(id, name, kind, aliases = aliases, packageAreaIds = packageAreaIds)

    private fun branch(
        id: String,
        name: String,
        kind: OfflineCatalogNode.Kind,
        children: List<OfflineCatalogNode>,
        aliases: List<String> = emptyList(),
        packageAreaIds: Set<String> = emptySet(),
    ) = OfflineCatalogNode(id, name, kind, children, aliases, packageAreaIds)
}
