package app.roadstr.core.geo

/** Decoder for the Google encoded-polyline format used by Transitous. */
object EncodedPolyline {
    fun decode(encoded: String, precision: Int): List<GeoPoint> {
        if (encoded.isEmpty()) return emptyList()
        val factor = powerOfTen(precision)
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            val decodedLatitude = decodeSignedValue(encoded, index) ?: return points
            latitude += decodedLatitude.value
            index = decodedLatitude.nextIndex

            val decodedLongitude = decodeSignedValue(encoded, index) ?: return points
            longitude += decodedLongitude.value
            index = decodedLongitude.nextIndex

            points += GeoPoint(latitude / factor, longitude / factor)
        }
        return points
    }

    private data class DecodedValue(val value: Int, val nextIndex: Int)

    private fun decodeSignedValue(encoded: String, start: Int): DecodedValue? {
        var index = start
        var shift = 0
        var result = 0
        while (true) {
            if (index >= encoded.length) return null
            val chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f) shl shift)
            if (chunk < 0x20) break
            shift += 5
            if (shift > 30) return null
        }
        val value = if ((result and 1) != 0) (result ushr 1).inv() else result ushr 1
        return DecodedValue(value, index)
    }

    private fun powerOfTen(exponent: Int): Double {
        var result = 1.0
        for (i in 0 until exponent) result *= 10.0
        return result
    }
}
