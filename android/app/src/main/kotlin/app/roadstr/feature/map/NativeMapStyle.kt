package app.roadstr.feature.map

import app.roadstr.service.offline.InstalledOfflinePackage
import app.roadstr.service.offline.OfflineMapFormat
import java.net.URI

enum class NativeTileUrlDecision {
    Accepted,
    Invalid,
    CleartextRejected,
}

/**
 * Admission policy for the user-configurable raster template.
 *
 * MapLibre consumes a URL template, not an arbitrary JSON fragment.  The
 * template is therefore validated before it is escaped into the style JSON;
 * cleartext keeps the same loopback-only exception as the native network
 * policy.
 */
object NativeTileUrlPolicy {
    val cleartextAllowedHosts: Set<String> = setOf(
        "localhost",
        "127.0.0.1",
        "10.0.2.2",
    )

    fun decision(tileUrl: String): NativeTileUrlDecision {
        val candidate = tileUrl.trim()
        if (candidate.isEmpty() || candidate.any { it.isISOControl() || it == '"' || it == '\\' }) {
            return NativeTileUrlDecision.Invalid
        }
        if (!candidate.contains("{z}") || !candidate.contains("{x}") || !candidate.contains("{y}")) {
            return NativeTileUrlDecision.Invalid
        }

        // java.net.URI rejects MapLibre's braces, so parse an equivalent
        // concrete path while retaining the original template for output.
        val parseable = candidate
            .replace("{z}", "z")
            .replace("{x}", "x")
            .replace("{y}", "y")
        val uri = try {
            URI(parseable)
        } catch (_: Exception) {
            return NativeTileUrlDecision.Invalid
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || host.isNullOrEmpty() || uri.userInfo != null) {
            return NativeTileUrlDecision.Invalid
        }
        if (uri.fragment != null) return NativeTileUrlDecision.Invalid
        if (scheme == "http" && host !in cleartextAllowedHosts) {
            return NativeTileUrlDecision.CleartextRejected
        }
        return NativeTileUrlDecision.Accepted
    }

    fun requireAccepted(tileUrl: String): String {
        require(decision(tileUrl) == NativeTileUrlDecision.Accepted) {
            "Map tile URL is not admitted by the native safety policy"
        }
        return tileUrl.trim()
    }
}

/** Native counterpart of the Flutter MapLibre raster style builder. */
object NativeMapStyle {
    const val DEFAULT_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"

    fun rasterStyle(dark: Boolean, tileUrl: String = DEFAULT_TILE_URL): String {
        val admittedUrl = NativeTileUrlPolicy.requireAccepted(tileUrl)
        val escapedUrl = jsonString(admittedUrl)
        val darkPaint = if (dark) {
            """,
      "paint": {
        "raster-hue-rotate": 180,
        "raster-brightness-min": 1,
        "raster-brightness-max": 0,
        "raster-saturation": -0.5,
        "raster-contrast": 0.1
      }"""
        } else {
            ""
        }
        return """{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["$escapedUrl"],
      "tileSize": 256,
      "attribution": "${jsonString(OSM_ATTRIBUTION)}"
    }

  },
  "layers": [
    {
      "id": "osm",
      "type": "raster",
      "source": "osm"$darkPaint
    }
  ]
}"""
    }

    /**
     * Builds a completely local style for the installed visual-map archives.
     * PMTiles stays outside MapLibre's OfflineManager: the package manager
     * owns download, checksum and atomic activation, while MapLibre reads the
     * activated file through its `pmtiles://file://` protocol.  The
     * Shortbread branch is intentionally data-only (fills and road/boundary
     * lines); labels need a matching local glyph bundle and are not claimed
     * until a style asset is shipped with the catalogue.
     */
    fun offlineMapStyle(
        dark: Boolean,
        packages: List<InstalledOfflinePackage>,
    ): String {
        require(packages.isNotEmpty()) { "At least one offline map package is required" }
        val background = if (dark) "#17212b" else "#eef1f4"
        val land = if (dark) "#263746" else "#e9e4d7"
        val water = if (dark) "#193b5a" else "#a9d7ee"
        val building = if (dark) "#344454" else "#d6d0c6"
        val road = if (dark) "#f0c674" else "#ffffff"
        val primaryRoad = if (dark) "#f39c6b" else "#e58b5d"
        val boundary = if (dark) "#9aa8b5" else "#79838c"
        return buildString {
            append("{\"version\":8,\"sources\":{")
            packages.forEachIndexed { index, installed ->
                if (index > 0) append(',')
                val sourceId = "offline_$index"
                val uri = when (installed.artifact.datasetType) {
                    app.roadstr.service.offline.OfflineDatasetType.MbTilesMap -> "mbtiles://${installed.filePath}"
                    else -> "pmtiles://file://${installed.filePath}"
                }
                appendJson(sourceId)
                append(":{")
                if (installed.artifact.mapFormat == OfflineMapFormat.Raster) {
                    append("\"type\":\"raster\",")
                    if (installed.artifact.datasetType == app.roadstr.service.offline.OfflineDatasetType.MbTilesMap) {
                        append("\"url\":")
                        appendJson(uri)
                    } else {
                        append("\"tiles\":[")
                        appendJson(uri)
                        append(']')
                    }
                    append(",\"tileSize\":256,\"attribution\":")
                    appendJson(installed.artifact.attribution)
                } else {
                    append("\"type\":\"vector\",")
                    if (installed.artifact.datasetType == app.roadstr.service.offline.OfflineDatasetType.MbTilesMap) {
                        append("\"url\":")
                        appendJson(uri)
                    } else {
                        append("\"tiles\":[")
                        appendJson(uri)
                        append(']')
                    }
                    append(",\"attribution\":")
                    appendJson(installed.artifact.attribution)
                }
                append('}')
            }
            append("},\"layers\":[")
            append("{\"id\":\"offline-background\",\"type\":\"background\",\"paint\":{\"background-color\":")
            appendJson(background)
            append("}}")
            packages.forEachIndexed { index, installed ->
                val sourceId = "offline_$index"
                if (installed.artifact.mapFormat == OfflineMapFormat.Raster) {
                    append(", {\"id\":\"offline-raster-$index\",\"type\":\"raster\",\"source\":")
                    appendJson(sourceId)
                    append("}")
                } else {
                    appendShortbreadLayers(
                        sourceId = sourceId,
                        prefix = "offline-$index",
                        land = land,
                        water = water,
                        building = building,
                        road = road,
                        primaryRoad = primaryRoad,
                        boundary = boundary,
                    )
                }
            }
            append("]}")
        }
    }

    private fun StringBuilder.appendShortbreadLayers(
        sourceId: String,
        prefix: String,
        land: String,
        water: String,
        building: String,
        road: String,
        primaryRoad: String,
        boundary: String,
    ) {
        append(", {\"id\":\"$prefix-land\",\"type\":\"fill\",\"source\":")
        appendJson(sourceId)
        append(",\"source-layer\":\"land\",\"paint\":{\"fill-color\":")
        appendJson(land)
        append("}}")
        append(", {\"id\":\"$prefix-water\",\"type\":\"fill\",\"source\":")
        appendJson(sourceId)
        append(",\"source-layer\":\"water_polygons\",\"paint\":{\"fill-color\":")
        appendJson(water)
        append("}}")
        append(", {\"id\":\"$prefix-buildings\",\"type\":\"fill\",\"source\":")
        appendJson(sourceId)
        append(",\"source-layer\":\"buildings\",\"minzoom\":14,\"paint\":{\"fill-color\":")
        appendJson(building)
        append("}}")
        append(", {\"id\":\"$prefix-streets\",\"type\":\"line\",\"source\":")
        appendJson(sourceId)
        append(",\"source-layer\":\"streets\",\"paint\":{\"line-color\":")
        appendJson(road)
        append(",\"line-width\":[\"interpolate\",[\"linear\"],[\"zoom\"],8,0.5,14,3,18,10]}}")
        append(", {\"id\":\"$prefix-primary\",\"type\":\"line\",\"source\":")
        appendJson(sourceId)
        append(",\"source-layer\":\"streets\",\"filter\":[\"in\",[\"get\",\"kind\"],\"motorway\",\"trunk\",\"primary\",\"secondary\"],\"paint\":{\"line-color\":")
        appendJson(primaryRoad)
        append(",\"line-width\":[\"interpolate\",[\"linear\"],[\"zoom\"],6,0.7,14,4,18,12]}}")
        append(", {\"id\":\"$prefix-boundaries\",\"type\":\"line\",\"source\":")
        appendJson(sourceId)
        append(",\"source-layer\":\"boundaries\",\"paint\":{\"line-color\":")
        appendJson(boundary)
        append(",\"line-width\":1}}")
    }

    private fun jsonString(value: String): String = buildString(value.length + 8) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }

    private fun StringBuilder.appendJson(value: String) {
        append('"')
        append(jsonString(value))
        append('"')
    }
}
