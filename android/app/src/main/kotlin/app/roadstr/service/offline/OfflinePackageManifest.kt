package app.roadstr.service.offline

import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.net.URI
import java.time.Instant
import java.util.Locale
import kotlin.math.floor

enum class OfflineDatasetType(val wireValue: String) {
    ValhallaRouting("valhalla-routing"),
    PmTilesMap("pmtiles-map"),
    SearchIndex("search-index");

    companion object {
        fun fromWire(value: String): OfflineDatasetType = entries.firstOrNull {
            it.wireValue == value
        } ?: throw OfflineManifestException("Unsupported dataset type")
    }
}

data class OfflineCoveragePoint(val latitude: Double, val longitude: Double)

sealed interface OfflineCoverageArea {
    data class Polygon(val rings: List<List<OfflineCoveragePoint>>) : OfflineCoverageArea
    data class Corridor(
        val centreline: List<OfflineCoveragePoint>,
        val marginMeters: Double,
        val level2TileIds: Set<Int>,
    ) : OfflineCoverageArea
}

data class OfflinePackageBuild(
    val tool: String,
    val version: String,
    val recipe: String,
    val compatibilityId: String,
)

data class OfflinePackageArtifact(
    val id: String,
    val version: Int,
    val datasetType: OfflineDatasetType,
    val area: OfflineCoverageArea,
    val levels: Set<Int>,
    val sizeBytes: Long,
    val installedSizeBytes: Long,
    val sha256: String,
    val url: String,
    val license: String,
    val attribution: String,
    val build: OfflinePackageBuild,
)

data class OfflinePackageManifest(
    val schemaVersion: Int,
    val generatedAt: Instant,
    val artifacts: List<OfflinePackageArtifact>,
)

class OfflineManifestException(message: String) : IllegalArgumentException(message)

object OfflinePackageManifestProtocol {
    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_MANIFEST_BYTES = 512 * 1024
    const val MAX_ARTIFACTS = 200
    const val MAX_POINTS_PER_ARTIFACT = 20_000
    const val MAX_STRING_CHARS = 2_048
    private const val MAX_NESTING = 24
    private val ID = Regex("[a-z0-9][a-z0-9._-]{0,95}")
    private val SHA256 = Regex("[0-9a-f]{64}")

    fun decode(bytes: ByteArray): OfflinePackageManifest {
        if (bytes.size > MAX_MANIFEST_BYTES) throw OfflineManifestException("Manifest is too large")
        val raw = bytes.toString(Charsets.UTF_8)
        if (maxNesting(raw) > MAX_NESTING) throw OfflineManifestException("Manifest is too deeply nested")
        val root = parseObject(raw)
        val schema = integer(root["schemaVersion"], "schema version")
        if (schema != CURRENT_SCHEMA_VERSION) throw OfflineManifestException("Unsupported manifest schema")
        val generatedAt = runCatching { Instant.parse(text(root["generatedAt"], "generated timestamp")) }
            .getOrElse { throw OfflineManifestException("Invalid generated timestamp") }
        val entries = root["artifacts"] as? List<*>
            ?: throw OfflineManifestException("Manifest artifacts are missing")
        if (entries.size > MAX_ARTIFACTS) throw OfflineManifestException("Too many manifest artifacts")
        val artifacts = entries.map { artifact(it as? Map<*, *>) }
        if (artifacts.map { it.id }.distinct().size != artifacts.size) {
            throw OfflineManifestException("Duplicate artifact id")
        }
        return OfflinePackageManifest(schema, generatedAt, artifacts)
    }

    fun encodeArtifact(artifact: OfflinePackageArtifact): ByteArray = buildString {
        append("{\"schemaVersion\":1,\"generatedAt\":\"1970-01-01T00:00:00Z\",\"artifacts\":[")
        appendArtifact(artifact)
        append("]}")
    }.toByteArray(Charsets.UTF_8)

    fun validateUrl(value: String): URI {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw OfflineManifestException("Invalid artifact URL")
        if (
            !uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawFragment != null ||
            (uri.port != -1 && uri.port !in 1..65_535)
        ) throw OfflineManifestException("Artifact URL must be credential-free HTTPS")
        return uri
    }

    private fun artifact(raw: Map<*, *>?): OfflinePackageArtifact {
        val value = raw ?: throw OfflineManifestException("Invalid artifact")
        val id = text(value["id"], "artifact id").lowercase(Locale.ROOT)
        if (!ID.matches(id)) throw OfflineManifestException("Invalid artifact id")
        val version = integer(value["version"], "artifact version")
        if (version <= 0) throw OfflineManifestException("Invalid artifact version")
        val type = OfflineDatasetType.fromWire(text(value["datasetType"], "dataset type"))
        val levels = integerList(value["levels"], "levels").toSet()
        if (levels.isEmpty() || levels.any { it !in 0..2 }) throw OfflineManifestException("Invalid levels")
        val size = long(value["sizeBytes"], "download size")
        val installed = (value["installedSizeBytes"] as? Number)?.toLong() ?: size
        if (size <= 0 || installed <= 0) throw OfflineManifestException("Invalid artifact size")
        val hash = text(value["sha256"], "sha256").lowercase(Locale.ROOT)
        if (!SHA256.matches(hash)) throw OfflineManifestException("Invalid sha256")
        val url = validateUrl(text(value["url"], "artifact URL")).toASCIIString()
        val build = build(value["build"] as? Map<*, *>)
        return OfflinePackageArtifact(
            id = id,
            version = version,
            datasetType = type,
            area = area(value["area"] as? Map<*, *>),
            levels = levels,
            sizeBytes = size,
            installedSizeBytes = installed,
            sha256 = hash,
            url = url,
            license = text(value["license"], "license"),
            attribution = text(value["attribution"], "attribution"),
            build = build,
        )
    }

    private fun build(raw: Map<*, *>?): OfflinePackageBuild {
        val value = raw ?: throw OfflineManifestException("Build metadata is missing")
        return OfflinePackageBuild(
            tool = text(value["tool"], "build tool"),
            version = text(value["version"], "build version"),
            recipe = text(value["recipe"], "build recipe"),
            compatibilityId = text(value["compatibilityId"], "compatibility id"),
        )
    }

    private fun area(raw: Map<*, *>?): OfflineCoverageArea {
        val value = raw ?: throw OfflineManifestException("Coverage area is missing")
        return when (text(value["kind"], "coverage kind")) {
            "polygon" -> OfflineCoverageArea.Polygon(rings(value["coordinates"]))
            "corridor" -> OfflineCoverageArea.Corridor(
                centreline = points(value["centreline"]),
                marginMeters = number(value["marginMeters"], "corridor margin").also {
                    if (!it.isFinite() || it !in 1.0..250_000.0) {
                        throw OfflineManifestException("Invalid corridor margin")
                    }
                },
                level2TileIds = integerList(value["level2TileIds"], "level-2 tiles").toSet().also {
                    if (it.isEmpty() || it.any { id -> id !in 0 until 720 * 1440 }) {
                        throw OfflineManifestException("Invalid level-2 tile ids")
                    }
                },
            )
            else -> throw OfflineManifestException("Unsupported coverage kind")
        }
    }

    private fun rings(raw: Any?): List<List<OfflineCoveragePoint>> {
        val values = raw as? List<*> ?: throw OfflineManifestException("Polygon coordinates are missing")
        if (values.isEmpty()) throw OfflineManifestException("Polygon has no rings")
        val rings = values.map(::points)
        if (rings.sumOf { it.size } > MAX_POINTS_PER_ARTIFACT) {
            throw OfflineManifestException("Polygon has too many points")
        }
        rings.forEach {
            if (it.size < 4 || it.first() != it.last()) throw OfflineManifestException("Polygon ring is not closed")
        }
        return rings
    }

    private fun points(raw: Any?): List<OfflineCoveragePoint> {
        val values = raw as? List<*> ?: throw OfflineManifestException("Coordinates are missing")
        if (values.size !in 2..MAX_POINTS_PER_ARTIFACT) {
            throw OfflineManifestException("Invalid coordinate count")
        }
        return values.map { item ->
            val pair = item as? List<*> ?: throw OfflineManifestException("Invalid coordinate")
            if (pair.size != 2) throw OfflineManifestException("Invalid coordinate")
            val longitude = number(pair[0], "longitude")
            val latitude = number(pair[1], "latitude")
            if (!latitude.isFinite() || latitude !in -90.0..90.0) {
                throw OfflineManifestException("Invalid latitude")
            }
            if (!longitude.isFinite() || longitude !in -180.0..180.0) {
                throw OfflineManifestException("Invalid longitude")
            }
            OfflineCoveragePoint(latitude, longitude)
        }
    }

    private fun parseObject(raw: String): Map<*, *> = try {
        BoundedJsonParser(raw).parse() as? Map<*, *>
            ?: throw OfflineManifestException("Manifest must be an object")
    } catch (failure: OfflineManifestException) {
        throw failure
    } catch (_: Exception) {
        throw OfflineManifestException("Malformed manifest")
    }

    private fun text(value: Any?, label: String): String {
        val text = value as? String ?: throw OfflineManifestException("Invalid $label")
        if (text.isBlank() || text.length > MAX_STRING_CHARS || text.any { it.code < 0x20 }) {
            throw OfflineManifestException("Invalid $label")
        }
        return text
    }

    private fun integer(value: Any?, label: String): Int {
        val number = value as? Number ?: throw OfflineManifestException("Invalid $label")
        val long = number.toLong()
        if (number.toDouble() != long.toDouble() || long !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw OfflineManifestException("Invalid $label")
        }
        return long.toInt()
    }

    private fun long(value: Any?, label: String): Long {
        val number = value as? Number ?: throw OfflineManifestException("Invalid $label")
        val result = number.toLong()
        if (number.toDouble() != result.toDouble()) throw OfflineManifestException("Invalid $label")
        return result
    }

    private fun number(value: Any?, label: String): Double =
        (value as? Number)?.toDouble() ?: throw OfflineManifestException("Invalid $label")

    private fun integerList(value: Any?, label: String): List<Int> =
        (value as? List<*>)?.map { integer(it, label) }
            ?: throw OfflineManifestException("Invalid $label")

    private fun maxNesting(raw: String): Int {
        var depth = 0
        var maximum = 0
        var quoted = false
        var escaped = false
        raw.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else if (char == '"') {
                quoted = true
            } else if (char == '{' || char == '[') {
                depth++
                maximum = maxOf(maximum, depth)
            } else if (char == '}' || char == ']') {
                depth--
            }
        }
        return maximum
    }

    private fun StringBuilder.appendArtifact(value: OfflinePackageArtifact) {
        append("{\"id\":")
        appendJson(value.id)
        append(",\"version\":${value.version},\"datasetType\":")
        appendJson(value.datasetType.wireValue)
        append(",\"area\":")
        appendArea(value.area)
        append(",\"levels\":[${value.levels.sorted().joinToString(",")}]")
        append(",\"sizeBytes\":${value.sizeBytes},\"installedSizeBytes\":${value.installedSizeBytes}")
        append(",\"sha256\":")
        appendJson(value.sha256)
        append(",\"url\":")
        appendJson(value.url)
        append(",\"license\":")
        appendJson(value.license)
        append(",\"attribution\":")
        appendJson(value.attribution)
        append(",\"build\":{\"tool\":")
        appendJson(value.build.tool)
        append(",\"version\":")
        appendJson(value.build.version)
        append(",\"recipe\":")
        appendJson(value.build.recipe)
        append(",\"compatibilityId\":")
        appendJson(value.build.compatibilityId)
        append("}}")
    }

    private fun StringBuilder.appendArea(area: OfflineCoverageArea) {
        when (area) {
            is OfflineCoverageArea.Polygon -> {
                append("{\"kind\":\"polygon\",\"coordinates\":[")
                area.rings.forEachIndexed { index, ring ->
                    if (index > 0) append(',')
                    appendPoints(ring)
                }
                append("]}")
            }
            is OfflineCoverageArea.Corridor -> {
                append("{\"kind\":\"corridor\",\"centreline\":")
                appendPoints(area.centreline)
                append(",\"marginMeters\":${area.marginMeters},\"level2TileIds\":[")
                append(area.level2TileIds.sorted().joinToString(","))
                append("]}")
            }
        }
    }

    private fun StringBuilder.appendPoints(points: List<OfflineCoveragePoint>) {
        append('[')
        points.forEachIndexed { index, point ->
            if (index > 0) append(',')
            append("[${point.longitude},${point.latitude}]")
        }
        append(']')
    }

    private fun StringBuilder.appendJson(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}

data class InstalledOfflinePackage(
    val artifact: OfflinePackageArtifact,
    val filePath: String,
    val installedAtEpochMillis: Long,
)

class OfflineCoverageIndex(
    installed: List<InstalledOfflinePackage>,
) {
    private val routing = installed.filter {
        it.artifact.datasetType == OfflineDatasetType.ValhallaRouting &&
            it.artifact.levels.containsAll(setOf(0, 1, 2))
    }

    fun coversPoint(point: RoutingRequestPoint): app.roadstr.service.routing.RoutingCoverageResult {
        if (!valid(point)) return app.roadstr.service.routing.RoutingCoverageResult.Unknown
        return if (routing.any { covers(it.artifact.area, point) }) {
            app.roadstr.service.routing.RoutingCoverageResult.Covered
        } else {
            app.roadstr.service.routing.RoutingCoverageResult.NotCovered
        }
    }

    fun coveringPackages(points: List<RoutingRequestPoint>): Set<String> {
        if (points.isEmpty() || points.any { !valid(it) }) return emptySet()
        val samples = sampled(points)
        return routing.filter { installed -> samples.all { covers(installed.artifact.area, it) } }
            .mapTo(linkedSetOf()) { it.artifact.id }
    }

    fun coversRoute(points: List<RoutingRequestPoint>): app.roadstr.service.routing.RoutingCoverageResult {
        if (points.size < 2 || points.any { !valid(it) }) {
            return app.roadstr.service.routing.RoutingCoverageResult.Unknown
        }
        return if (coveringPackages(points).isNotEmpty()) {
            app.roadstr.service.routing.RoutingCoverageResult.Covered
        } else {
            app.roadstr.service.routing.RoutingCoverageResult.NotCovered
        }
    }

    private fun sampled(points: List<RoutingRequestPoint>): List<RoutingRequestPoint> {
        val output = mutableListOf(points.first())
        points.zipWithNext().forEach { (start, end) ->
            val steps = maxOf(
                1,
                kotlin.math.ceil(
                    maxOf(
                        kotlin.math.abs(end.latitude - start.latitude),
                        kotlin.math.abs(end.longitude - start.longitude),
                    ) / SAMPLE_DEGREES,
                ).toInt(),
            )
            for (index in 1..steps) {
                val fraction = index.toDouble() / steps
                output += RoutingRequestPoint(
                    latitude = start.latitude + (end.latitude - start.latitude) * fraction,
                    longitude = start.longitude + (end.longitude - start.longitude) * fraction,
                )
            }
        }
        return output
    }

    private fun covers(area: OfflineCoverageArea, point: RoutingRequestPoint): Boolean = when (area) {
        is OfflineCoverageArea.Corridor -> level2TileId(point) in area.level2TileIds
        is OfflineCoverageArea.Polygon -> insidePolygon(area.rings, point)
    }

    private fun insidePolygon(rings: List<List<OfflineCoveragePoint>>, point: RoutingRequestPoint): Boolean {
        if (!insideRing(rings.first(), point)) return false
        return rings.drop(1).none { insideRing(it, point) }
    }

    private fun insideRing(ring: List<OfflineCoveragePoint>, point: RoutingRequestPoint): Boolean {
        var inside = false
        var previous = ring.last()
        ring.forEach { current ->
            val crosses = (current.latitude > point.latitude) != (previous.latitude > point.latitude)
            if (crosses) {
                val boundary = (previous.longitude - current.longitude) *
                    (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
                if (point.longitude < boundary) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun level2TileId(point: RoutingRequestPoint): Int {
        val row = floor((point.latitude + 90.0) * 4.0).toInt().coerceIn(0, 719)
        val column = floor((point.longitude + 180.0) * 4.0).toInt().coerceIn(0, 1439)
        return row * 1440 + column
    }

    private fun valid(point: RoutingRequestPoint): Boolean =
        point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
            point.longitude.isFinite() && point.longitude in -180.0..180.0

    private companion object {
        const val SAMPLE_DEGREES = 0.05
    }
}
