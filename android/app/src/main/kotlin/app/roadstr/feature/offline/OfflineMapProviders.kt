package app.roadstr.feature.offline

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.service.offline.OfflineCoverageArea
import app.roadstr.service.offline.OfflineCoveragePoint
import app.roadstr.service.offline.OfflineDatasetType
import app.roadstr.service.offline.OfflineMapFormat
import app.roadstr.service.offline.OfflinePackageArtifact
import app.roadstr.service.offline.OfflinePackageBuild
import app.roadstr.service.offline.OfflinePackageDownloadTransport
import app.roadstr.service.offline.OfflinePackageManifest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class OfflineMapProvider(
    val displayName: String,
    val sourceUrl: String,
    val directlyDownloadable: Boolean,
) {
    Geofabrik("Geofabrik", "https://download.geofabrik.de/index-v1.json", true),
    VersaTiles("VersaTiles", "https://download.versatiles.org/", false),
    OpenMapTiles("OpenMapTiles", "https://openmaptiles.org/", false),
    RoadstrManifest("Roadstr JSON", "", true),
}

data class OfflineProviderCatalog(
    val provider: OfflineMapProvider,
    val root: OfflineCatalogNode,
    val manifest: OfflinePackageManifest,
    val loadedFromCache: Boolean,
)

/** Loads the official Geofabrik hierarchy and turns its terminal Shortbread
 * extracts into lazy artifacts. A package is probed again immediately before
 * installation, so a missing extract never becomes a fake download. */
class OfflineMapProviderClient(
    cacheDirectory: File,
    private val transport: OfflinePackageDownloadTransport,
    private val bundledPlaces: () -> InputStream? = { null },
) {
    private val cacheFile = File(cacheDirectory, "geofabrik-index-v1.json")

    init {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IOException("Offline provider cache is unavailable")
        }
    }

    suspend fun loadGeofabrik(): OfflineProviderCatalog = withContext(Dispatchers.IO) {
        val downloaded = runCatching { downloadIndex() }
        val bytes = downloaded.getOrNull() ?: cacheFile.takeIf(File::isFile)?.readBytes()
            ?: throw downloaded.exceptionOrNull() ?: IOException("Geofabrik catalog is unavailable")
        if (downloaded.isSuccess) persistCache(bytes)
        parseGeofabrik(bytes, downloaded.isFailure)
    }

    suspend fun prepareForInstall(artifact: OfflinePackageArtifact): OfflinePackageArtifact =
        withContext(Dispatchers.IO) {
            if (artifact.build.tool != GEOFABRIK_TOOL || artifact.sizeBytes > 0L) {
                return@withContext artifact
            }
            val response = transport.open(artifact.url, 0L, null)
            response.use {
                if (response.statusCode != HTTP_OK) throw IOException("Provider package is unavailable")
                if (!response.contentEncoding.isNullOrBlank() && response.contentEncoding != "identity") {
                    throw IOException("Encoded map packages are not accepted")
                }
                val size = response.contentLength?.takeIf { value -> value > 0L }
                    ?: throw IOException("Provider did not report package size")
                val validator = response.etag ?: response.lastModified
                    ?: throw IOException("Provider did not report an entity validator")
                artifact.copy(
                    sizeBytes = size,
                    installedSizeBytes = size,
                    sourceValidator = validator,
                )
            }
        }

    private suspend fun downloadIndex(): ByteArray {
        val response = transport.open(OfflineMapProvider.Geofabrik.sourceUrl, 0L, null)
        response.use {
            if (response.statusCode != HTTP_OK) throw IOException("Geofabrik catalog request failed")
            if (!response.contentEncoding.isNullOrBlank() && response.contentEncoding != "identity") {
                throw IOException("Encoded provider catalogs are not accepted")
            }
            if (response.contentLength?.let { it > MAX_INDEX_BYTES } == true) {
                throw IOException("Geofabrik catalog is too large")
            }
            val output = ByteArrayOutputStream(512 * 1024)
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                coroutineContext.ensureActive()
                val count = response.body.read(buffer)
                if (count < 0) break
                if (total > MAX_INDEX_BYTES - count) throw IOException("Geofabrik catalog is too large")
                output.write(buffer, 0, count)
                total += count
            }
            return output.toByteArray()
        }
    }

    private fun persistCache(bytes: ByteArray) {
        val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        temporary.writeBytes(bytes)
        // File.renameTo does not replace an existing destination consistently
        // on Android. A normal catalog refresh must not fail merely because a
        // previous valid index is already cached.
        if (cacheFile.exists() && !cacheFile.delete()) {
            temporary.delete()
            throw IOException("Could not replace provider cache")
        }
        if (!temporary.renameTo(cacheFile)) {
            temporary.delete()
            throw IOException("Could not update provider cache")
        }
    }

    private fun parseGeofabrik(bytes: ByteArray, fromCache: Boolean): OfflineProviderCatalog {
        if (bytes.size !in 1..MAX_INDEX_BYTES) throw IOException("Invalid Geofabrik catalog size")
        val root = runCatching { BoundedJsonParser(bytes.toString(Charsets.UTF_8)).parse() as? Map<*, *> }
            .getOrNull() ?: throw IOException("Invalid Geofabrik catalog")
        val rawFeatures = root["features"] as? List<*> ?: throw IOException("Geofabrik areas are missing")
        if (rawFeatures.size !in 1..MAX_AREAS) throw IOException("Invalid Geofabrik area count")
        val features = rawFeatures.mapNotNull(::parseFeature)
        val byParent = features.groupBy { it.parentId }
        val byId = features.associateBy { it.rawId }
        val stableIds = features.associate { it.rawId to stableId(it) }

        fun countryIso(feature: ProviderFeature): String? {
            feature.rawId.substringBefore('/').takeIf { it != feature.rawId }
                ?.let(byId::get)?.isoCode?.let { return it }
            var current: ProviderFeature? = feature
            while (current != null) {
                current.isoCode?.let { return it }
                current = current.parentId?.let(byId::get)
            }
            return null
        }
        val downloadableLeaves = features.filter { byParent[it.rawId].isNullOrEmpty() && it.pbfUrl != null }
        val leavesByCountry = downloadableLeaves.groupBy(::countryIso)
        val places = bundledPlaces()?.use(::readPlaces) ?: BundledPlaces(emptyMap(), emptyList())
        val citiesByCountry = places.cities.groupBy { it.countryCode }

        fun packageAreaFor(city: BundledCity): String? = leavesByCountry[city.countryCode]
            .orEmpty()
            .filter { it.bounds.contains(city.latitude, city.longitude) }
            .minByOrNull { it.bounds.area }
            ?.let { stableIds.getValue(it.rawId) }

        fun cityId(city: BundledCity): String = when (city.countryCode to city.asciiName.lowercase()) {
            "it" to "rimini" -> "it-rimini"
            "jp" to "osaka" -> "jp-osaka"
            "us" to "los angeles" -> "us-ca-los-angeles"
            "au" to "sydney" -> "au-nsw-sydney"
            else -> "gn-${city.geonameId}"
        }

        fun cityNode(city: BundledCity): OfflineCatalogNode = OfflineCatalogNode(
            id = cityId(city),
            name = city.name,
            kind = OfflineCatalogNode.Kind.City,
            aliases = listOf(city.asciiName).filterNot { it.equals(city.name, ignoreCase = true) },
            packageAreaIds = setOfNotNull(packageAreaFor(city)),
        )

        fun normalised(value: String): String = value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "")

        fun addPlaces(country: OfflineCatalogNode): OfflineCatalogNode {
            val countryCode = country.id.takeIf { it.length == 2 } ?: return country
            val groups = citiesByCountry[countryCode].orEmpty().groupBy { it.admin1Code }
            if (groups.isEmpty()) return country
            val children = country.children.toMutableList()
            groups.forEach { (adminCode, cities) ->
                val adminName = places.adminNames[countryCode to adminCode] ?: adminCode.ifBlank { country.name }
                val cityNodes = cities.sortedBy { it.name.lowercase() }.map(::cityNode)
                val existingIndex = children.indexOfFirst { normalised(it.name) == normalised(adminName) }
                val packageIds = cityNodes.flatMapTo(linkedSetOf()) { it.packageAreaIds }
                if (existingIndex >= 0) {
                    val existing = children[existingIndex]
                    children[existingIndex] = existing.copy(
                        children = (existing.children + cityNodes).distinctBy { it.id }.sortedBy { it.name.lowercase() },
                        packageAreaIds = existing.packageAreaIds + packageIds,
                    )
                } else {
                    val id = when (countryCode to normalised(adminName)) {
                        "it" to "emiliaromagna" -> "it-emilia-romagna"
                        "it" to "lombardy" -> "it-lombardia"
                        "it" to "veneto" -> "it-veneto"
                        else -> "gn-admin-$countryCode-${sanitise(adminCode)}"
                    }
                    children += OfflineCatalogNode(
                        id = id,
                        name = adminName,
                        kind = OfflineCatalogNode.Kind.Region,
                        children = cityNodes,
                        packageAreaIds = packageIds,
                    )
                }
            }
            return country.copy(children = children.distinctBy { it.id }.sortedBy { it.name.lowercase() })
        }

        fun build(feature: ProviderFeature): OfflineCatalogNode {
            val children = byParent[feature.rawId].orEmpty().map(::build).sortedBy { it.name.lowercase() }
            val id = stableIds.getValue(feature.rawId)
            val kind = when {
                feature.rawId == "russia" -> OfflineCatalogNode.Kind.Country
                feature.parentId == null -> OfflineCatalogNode.Kind.Continent
                feature.isoCode != null -> OfflineCatalogNode.Kind.Country
                children.isEmpty() -> OfflineCatalogNode.Kind.Region
                else -> OfflineCatalogNode.Kind.Macroregion
            }
            val node = OfflineCatalogNode(
                id = id,
                name = friendlyName(feature),
                kind = kind,
                children = children,
                aliases = listOf(feature.rawId).filterNot { it == feature.name },
            )
            return if (kind == OfflineCatalogNode.Kind.Country) addPlaces(node) else node
        }

        val top = byParent[null].orEmpty().associateBy { it.rawId }
        val europe = top["europe"]?.let(::build)
        val russia = top["russia"]?.let(::build)
        val providerWorld = OfflineCatalogNode(
            id = "world",
            name = "World",
            kind = OfflineCatalogNode.Kind.Root,
            children = listOfNotNull(
                top["africa"]?.let(::build),
                OfflineCatalogNode(
                    id = "america",
                    name = "America",
                    kind = OfflineCatalogNode.Kind.Continent,
                    children = listOf("north-america", "central-america", "south-america")
                        .mapNotNull { top[it]?.let(::build) },
                ),
                top["asia"]?.let(::build),
                europe?.copy(children = (europe.children + listOfNotNull(russia)).sortedBy { it.name.lowercase() }),
                top["australia-oceania"]?.let(::build)?.copy(id = "oceania", name = "Oceania"),
            ),
        )
        val artifacts = downloadableLeaves.map { feature ->
            val catalogId = stableIds.getValue(feature.rawId)
            OfflinePackageArtifact(
                id = packageId(feature.rawId),
                version = 1,
                datasetType = OfflineDatasetType.MbTilesMap,
                area = boundsCoverage(feature.geometry),
                levels = (0..14).toSet(),
                sizeBytes = 0L,
                installedSizeBytes = 0L,
                sha256 = null,
                url = feature.pbfUrl!!.removeSuffix("-latest.osm.pbf") + "-shortbread-1.0.mbtiles",
                license = "ODbL 1.0",
                attribution = "© OpenStreetMap contributors; extract by Geofabrik",
                build = OfflinePackageBuild(
                    tool = GEOFABRIK_TOOL,
                    version = "shortbread-1.0",
                    recipe = "Official Geofabrik Shortbread MBTiles extract",
                    compatibilityId = "shortbread-1.0",
                ),
                label = friendlyName(feature),
                mapFormat = OfflineMapFormat.ShortbreadVector,
                catalogAreaId = catalogId,
            )
        }
        return OfflineProviderCatalog(
            provider = OfflineMapProvider.Geofabrik,
            root = OfflineGeographicCatalog.mergeProviderWorld(providerWorld),
            manifest = OfflinePackageManifest(1, Instant.EPOCH, artifacts),
            loadedFromCache = fromCache,
        )
    }

    private fun parseFeature(value: Any?): ProviderFeature? {
        val feature = value as? Map<*, *> ?: return null
        val properties = feature["properties"] as? Map<*, *> ?: return null
        val rawId = properties["id"] as? String ?: return null
        val name = properties["name"] as? String ?: rawId
        val isoCode = (properties["iso3166-1:alpha2"] as? List<*>)
            ?.filterIsInstance<String>()?.singleOrNull()?.lowercase()
        val urls = properties["urls"] as? Map<*, *>
        return ProviderFeature(
            rawId = rawId,
            parentId = properties["parent"] as? String,
            name = name,
            isoCode = isoCode,
            pbfUrl = urls?.get("pbf") as? String,
            geometry = feature["geometry"] as? Map<*, *>,
            bounds = bounds(feature["geometry"] as? Map<*, *>),
        )
    }

    private fun stableId(feature: ProviderFeature): String {
        val topLevel = when (feature.rawId) {
            "africa", "asia", "europe", "north-america", "central-america", "south-america" -> feature.rawId
            "australia-oceania" -> "oceania"
            "russia" -> "ru"
            else -> null
        }
        return topLevel ?: feature.isoCode ?: "gf-${sanitise(feature.rawId)}"
    }

    private fun packageId(rawId: String): String = "geofabrik-${sanitise(rawId)}-shortbread".take(96).trimEnd('-')

    private fun sanitise(value: String): String = value.lowercase().replace(Regex("[^a-z0-9._-]+"), "-")

    private fun friendlyName(feature: ProviderFeature): String = when {
        feature.rawId == "us/california" -> "California"
        feature.name.contains('/') -> feature.name.substringAfterLast('/').replace('-', ' ').replaceFirstChar(Char::uppercase)
        else -> feature.name
    }

    private fun boundsCoverage(geometry: Map<*, *>?): OfflineCoverageArea.Polygon {
        val bounds = bounds(geometry)
        val minLon = bounds.minLongitude
        val maxLon = bounds.maxLongitude
        val minLat = bounds.minLatitude
        val maxLat = bounds.maxLatitude
        val ring = listOf(
            OfflineCoveragePoint(minLat, minLon), OfflineCoveragePoint(minLat, maxLon),
            OfflineCoveragePoint(maxLat, maxLon), OfflineCoveragePoint(maxLat, minLon),
            OfflineCoveragePoint(minLat, minLon),
        )
        return OfflineCoverageArea.Polygon(listOf(ring))
    }

    private fun bounds(geometry: Map<*, *>?): ProviderBounds {
        var minLon = 180.0
        var maxLon = -180.0
        var minLat = 90.0
        var maxLat = -90.0
        fun visit(value: Any?) {
            val list = value as? List<*> ?: return
            if (list.size >= 2 && list[0] is Number && list[1] is Number) {
                val lon = (list[0] as Number).toDouble()
                val lat = (list[1] as Number).toDouble()
                if (lon in -180.0..180.0 && lat in -90.0..90.0) {
                    minLon = minOf(minLon, lon); maxLon = maxOf(maxLon, lon)
                    minLat = minOf(minLat, lat); maxLat = maxOf(maxLat, lat)
                }
            } else list.forEach(::visit)
        }
        visit(geometry?.get("coordinates"))
        if (minLon > maxLon || minLat > maxLat) {
            minLon = -180.0; maxLon = 180.0; minLat = -85.0; maxLat = 85.0
        }
        return ProviderBounds(minLat, maxLat, minLon, maxLon)
    }

    private fun readPlaces(input: InputStream): BundledPlaces {
        val adminNames = linkedMapOf<Pair<String, String>, String>()
        val cities = ArrayList<BundledCity>(36_000)
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val values = line.split('\t')
                when (values.firstOrNull()) {
                    "A" -> if (values.size >= 4) {
                        adminNames[values[1].lowercase() to values[2]] = values[3]
                    }
                    "C" -> if (values.size >= 9) {
                        val latitude = values[4].toDoubleOrNull()
                        val longitude = values[5].toDoubleOrNull()
                        if (latitude != null && longitude != null) {
                            cities += BundledCity(
                                geonameId = values[1],
                                name = values[2],
                                asciiName = values[3],
                                latitude = latitude,
                                longitude = longitude,
                                countryCode = values[6].lowercase(),
                                admin1Code = values[7],
                            )
                        }
                    }
                }
            }
        }
        return BundledPlaces(adminNames, cities)
    }

    private data class ProviderFeature(
        val rawId: String,
        val parentId: String?,
        val name: String,
        val isoCode: String?,
        val pbfUrl: String?,
        val geometry: Map<*, *>?,
        val bounds: ProviderBounds,
    )

    private data class ProviderBounds(
        val minLatitude: Double,
        val maxLatitude: Double,
        val minLongitude: Double,
        val maxLongitude: Double,
    ) {
        val area: Double get() = (maxLatitude - minLatitude) * (maxLongitude - minLongitude)
        fun contains(latitude: Double, longitude: Double): Boolean =
            latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
    }

    private data class BundledPlaces(
        val adminNames: Map<Pair<String, String>, String>,
        val cities: List<BundledCity>,
    )

    private data class BundledCity(
        val geonameId: String,
        val name: String,
        val asciiName: String,
        val latitude: Double,
        val longitude: Double,
        val countryCode: String,
        val admin1Code: String,
    )

    private companion object {
        const val GEOFABRIK_TOOL = "geofabrik-shortbread"
        const val HTTP_OK = 200
        const val MAX_INDEX_BYTES = 6 * 1024 * 1024
        const val MAX_AREAS = 2_000
    }
}
