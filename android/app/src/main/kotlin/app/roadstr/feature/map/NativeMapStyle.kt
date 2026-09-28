package app.roadstr.feature.map

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
}
