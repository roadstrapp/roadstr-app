package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.BoundedJsonParser

/** Loader for docs/interoperability/vectors/roadstr-nostr-meshcore-v1.json (the published file). */
internal object InteropVectors {
    class Vector(private val fields: Map<String, Any?>) {
        val name: String get() = fields["name"] as String
        val kind: String get() = fields["class"] as String
        val binary: ByteArray get() = hexToBytes(fields["binaryHex"] as String)
        val flags: Int get() = (fields["flags"] as? Long)?.toInt() ?: 0

        @Suppress("UNCHECKED_CAST")
        val event: Map<String, Any?> get() = fields["event"] as Map<String, Any?>
        val expectStrict: String? get() = fields["expectStrict"] as? String
        val expectEncode: String? get() = fields["expectEncode"] as? String
        val expectedEventId: String? get() = fields["expectedEventId"] as? String
        val matchesCorrectEncoding: Boolean get() = fields["matchesCorrectEncoding"] == true

        val location: CompactReportLocation?
            get() {
                @Suppress("UNCHECKED_CAST")
                val reference = fields["reference"] as? Map<String, Any?> ?: return null
                val latitude = CompactCoordinates.parseLatitude(reference["lat"] as String)
                val longitude = CompactCoordinates.parseLongitude(reference["lon"] as String)
                return CompactReportLocation(
                    reference["eventId"] as String,
                    (latitude as CompactCoordinates.Parsed.Value).e7,
                    (longitude as CompactCoordinates.Parsed.Value).e7,
                )
            }

        override fun toString(): String = name
    }

    private val root: Map<String, Any?> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/roadstr-nostr-meshcore-v1.json")) {
            "vectors file is not on the test classpath"
        }
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        @Suppress("UNCHECKED_CAST")
        BoundedJsonParser(text).parse() as Map<String, Any?>
    }

    val valid: List<Vector> get() = group("valid")
    val invalid: List<Vector> get() = group("invalid")
    val nonRepresentable: List<Vector> get() = group("nonRepresentable")
    val tolerated: List<Vector> get() = group("tolerated")
    val referenceTruncation: List<Vector> get() = group("referenceTruncation")

    private fun group(key: String): List<Vector> {
        @Suppress("UNCHECKED_CAST")
        val rows = root[key] as List<Map<String, Any?>>
        return rows.map(::Vector)
    }

    fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
