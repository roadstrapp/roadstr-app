package app.roadstr.feature.saved

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeMapPointOverlayKind
import app.roadstr.feature.map.NativeMapPointOverlayMarker
import java.math.BigDecimal
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NativeSavedPlace(
    val label: String,
    val address: String,
    val point: NativeMapPoint,
)

data class NativeParkingPosition(
    val point: NativeMapPoint,
    val savedAtEpochMillis: Long? = null,
)

data class NativeFavoritesImportEnvelope(
    val encrypted: Boolean,
    val plaintext: String?,
    val encryptedFields: Map<String, Any?>?,
)

/**
 * Storage- and UI-free mirror of FavoritePlace plus the local parking value.
 *
 * The stored favourites value is a JSON list containing one JSON string per
 * item, exactly as the Flutter Hive box does. Import plaintext is instead a
 * direct JSON list of objects. No file picker, password, network or storage
 * adapter is owned here.
 */
object NativeSavedPlacesProtocol {
    const val MAX_LABEL_CHARS = 200
    const val MAX_ADDRESS_CHARS = 500
    const val MAX_STORED_ITEMS = 1_000
    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    const val PARKING_MARKER_ID = "saved-parking"

    fun normalizeFavorite(value: NativeSavedPlace): NativeSavedPlace? = favorite(
        label = value.label,
        address = value.address,
        latitude = value.point.latitude,
        longitude = value.point.longitude,
    )

    fun decodeStoredFavorites(raw: String?): List<NativeSavedPlace> {
        if (raw == null || raw.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) {
            return emptyList()
        }
        val values = parse(raw) as? List<*> ?: return emptyList()
        val decoded = ArrayList<NativeSavedPlace>(minOf(values.size, MAX_STORED_ITEMS))
        for (value in values) {
            if (decoded.size == MAX_STORED_ITEMS) break
            val encoded = value as? String ?: continue
            val favorite = (parse(encoded) as? Map<*, *>)?.let(::favoriteFromMap) ?: continue
            decoded += favorite
        }
        return Collections.unmodifiableList(decoded)
    }

    fun encodeStoredFavorites(values: Iterable<NativeSavedPlace>): String {
        val encoded = values.mapNotNull(::normalizeFavorite).take(MAX_STORED_ITEMS)
        return buildString {
            append('[')
            encoded.forEachIndexed { index, value ->
                if (index > 0) append(',')
                appendJsonString(encodeFavorite(value))
            }
            append(']')
        }
    }

    fun decodeImportPlaintext(raw: String): List<NativeSavedPlace> {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) return emptyList()
        val values = parse(raw) as? List<*> ?: return emptyList()
        val decoded = ArrayList<NativeSavedPlace>(minOf(values.size, MAX_STORED_ITEMS))
        for (value in values) {
            if (decoded.size == MAX_STORED_ITEMS) break
            val favorite = (value as? Map<*, *>)?.let(::favoriteFromMap) ?: continue
            decoded += favorite
        }
        return Collections.unmodifiableList(decoded)
    }

    fun encodeImportPlaintext(values: Iterable<NativeSavedPlace>): String {
        val encoded = values.mapNotNull(::normalizeFavorite).take(MAX_STORED_ITEMS)
        return encoded.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::encodeFavorite)
    }

    fun decodeImportEnvelope(raw: String): NativeFavoritesImportEnvelope? {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) return null
        val envelope = parse(raw) as? Map<*, *> ?: return null
        return if (envelope["encrypted"] == true) {
            val salt = envelope["salt"] as? String ?: return null
            val iv = envelope["iv"] as? String ?: return null
            val ciphertext = envelope["ciphertext"] as? String ?: return null
            val iterations = (envelope["iterations"] as? Number)?.toLong() ?: 600_000L
            if (iterations !in 1_000L..1_000_000L ||
                salt.length > 256 || iv.length > 256 || ciphertext.length > MAX_IMPORT_BYTES
            ) {
                return null
            }
            NativeFavoritesImportEnvelope(
                encrypted = true,
                plaintext = null,
                encryptedFields = Collections.unmodifiableMap(
                    linkedMapOf(
                        "v" to envelope["v"],
                        "iterations" to iterations,
                        "salt" to salt,
                        "iv" to iv,
                        "ciphertext" to ciphertext,
                    ),
                ),
            )
        } else {
            val plaintext = envelope["data"] as? String ?: return null
            if (plaintext.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) return null
            NativeFavoritesImportEnvelope(false, plaintext, null)
        }
    }

    /** Incoming entries replace the first existing entry with the same exact label. */
    fun mergeByLabel(
        current: Iterable<NativeSavedPlace>,
        incoming: Iterable<NativeSavedPlace>,
    ): List<NativeSavedPlace> {
        val merged = current.mapNotNull(::normalizeFavorite).take(MAX_STORED_ITEMS).toMutableList()
        for (raw in incoming) {
            val value = normalizeFavorite(raw) ?: continue
            val duplicate = merged.indexOfFirst { it.label == value.label }
            if (duplicate >= 0) {
                merged[duplicate] = value
            } else if (merged.size < MAX_STORED_ITEMS) {
                merged += value
            }
        }
        return Collections.unmodifiableList(merged)
    }

    fun decodeParking(raw: String?): NativeParkingPosition? {
        if (raw == null || raw.toByteArray(Charsets.UTF_8).size > 4_096) return null
        val value = parse(raw) as? Map<*, *> ?: return null
        val latitude = (value["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (value["lon"] as? Number)?.toDouble() ?: return null
        if (!validPoint(latitude, longitude)) return null
        val timestamp = (value["ts"] as? Number)?.toLong()?.takeIf { it >= 0 }
        return NativeParkingPosition(NativeMapPoint(latitude, longitude), timestamp)
    }

    fun encodeParking(value: NativeParkingPosition): String {
        require(validPoint(value.point.latitude, value.point.longitude)) {
            "Parking coordinate is outside the WGS84 range"
        }
        require(value.savedAtEpochMillis == null || value.savedAtEpochMillis >= 0) {
            "Parking timestamp must be non-negative"
        }
        return buildString {
            append("{\"lat\":")
            append(dartDoubleString(value.point.latitude))
            append(",\"lon\":")
            append(dartDoubleString(value.point.longitude))
            value.savedAtEpochMillis?.let { append(",\"ts\":").append(it) }
            append('}')
        }
    }

    fun parkingMarker(value: NativeParkingPosition): NativeMapPointOverlayMarker =
        NativeMapPointOverlayMarker(
            id = PARKING_MARKER_ID,
            point = value.point,
            kind = NativeMapPointOverlayKind.Parking,
        )

    private fun favoriteFromMap(value: Map<*, *>): NativeSavedPlace? {
        val label = value["label"] as? String ?: return null
        val address = value["address"] as? String ?: ""
        val latitude = (value["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (value["lon"] as? Number)?.toDouble() ?: return null
        return favorite(label, address, latitude, longitude)
    }

    private fun favorite(
        label: String,
        address: String,
        latitude: Double,
        longitude: Double,
    ): NativeSavedPlace? {
        val normalizedLabel = label.trim()
        val normalizedAddress = address.trim()
        if (normalizedLabel.isEmpty() || normalizedLabel.length > MAX_LABEL_CHARS) return null
        if (normalizedAddress.length > MAX_ADDRESS_CHARS) return null
        if (!validPoint(latitude, longitude)) return null
        return NativeSavedPlace(
            label = normalizedLabel,
            address = normalizedAddress,
            point = NativeMapPoint(latitude, longitude),
        )
    }

    private fun validPoint(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun parse(raw: String): Any? = try {
        BoundedJsonParser(raw).parse()
    } catch (_: RuntimeException) {
        null
    }

    private fun encodeFavorite(value: NativeSavedPlace): String = buildString {
        append("{\"label\":")
        appendJsonString(value.label)
        append(",\"address\":")
        appendJsonString(value.address)
        append(",\"lat\":")
        append(dartDoubleString(value.point.latitude))
        append(",\"lon\":")
        append(dartDoubleString(value.point.longitude))
        append('}')
    }

    private fun dartDoubleString(value: Double): String {
        require(value.isFinite()) { "Coordinate must be finite" }
        if (value == 0.0) {
            return if (java.lang.Double.doubleToRawLongBits(value) < 0) "-0.0" else "0.0"
        }
        val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
        val exponent = decimal.precision() - decimal.scale() - 1
        if (exponent in -6..20) {
            val plain = decimal.toPlainString()
            return if ('.' in plain) plain else "$plain.0"
        }
        val digits = decimal.unscaledValue().abs().toString()
        val mantissa = if (digits.length == 1) digits else "${digits[0]}.${digits.substring(1)}"
        val sign = if (decimal.signum() < 0) "-" else ""
        val exponentSign = if (exponent >= 0) "+" else ""
        return "$sign$mantissa" + "e$exponentSign$exponent"
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\u000c' -> append("\\f")
                '\r' -> append("\\r")
                else -> when {
                    character.code < 0x20 -> {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    }
                    Character.isHighSurrogate(character) &&
                        index + 1 < value.length &&
                        Character.isLowSurrogate(value[index + 1]) -> {
                        append(character)
                        index++
                        append(value[index])
                    }
                    Character.isSurrogate(character) -> {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    }
                    else -> append(character)
                }
            }
            index++
        }
        append('"')
    }
}

enum class NativeSavedPlacesStatus {
    Hidden,
    Ready,
}

data class NativeSavedPlacesSnapshot(
    val revision: Long,
    val status: NativeSavedPlacesStatus,
    val favorites: List<NativeSavedPlace>,
    val parking: NativeParkingPosition?,
    val lastImportedCount: Int?,
) {
    companion object {
        const val NO_REVISION = -1L

        fun hidden() = NativeSavedPlacesSnapshot(
            revision = NO_REVISION,
            status = NativeSavedPlacesStatus.Hidden,
            favorites = emptyList(),
            parking = null,
            lastImportedCount = null,
        )
    }
}

/** Revision-safe in-memory UI owner with no persistence, picker, route or sync adapter. */
class NativeSavedPlacesSession {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeSavedPlacesSnapshot.hidden())
    private var revision = NativeSavedPlacesSnapshot.NO_REVISION

    val state: StateFlow<NativeSavedPlacesSnapshot> = _state.asStateFlow()

    fun show(
        revision: Long,
        favorites: Iterable<NativeSavedPlace>,
        parking: NativeParkingPosition?,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Saved-places revision must be non-negative" }
        if (revision <= this.revision) return false
        this.revision = revision
        val normalizedFavorites = favorites
            .mapNotNull(NativeSavedPlacesProtocol::normalizeFavorite)
            .take(NativeSavedPlacesProtocol.MAX_STORED_ITEMS)
        _state.value = NativeSavedPlacesSnapshot(
            revision = revision,
            status = NativeSavedPlacesStatus.Ready,
            favorites = Collections.unmodifiableList(normalizedFavorites),
            parking = parking?.let { NativeSavedPlacesProtocol.decodeParking(
                NativeSavedPlacesProtocol.encodeParking(it),
            ) },
            lastImportedCount = null,
        )
        true
    }

    fun upsert(revision: Long, value: NativeSavedPlace, index: Int? = null): Boolean =
        synchronized(lock) {
            if (!active(revision)) return false
            val normalized = NativeSavedPlacesProtocol.normalizeFavorite(value) ?: return false
            val updated = _state.value.favorites.toMutableList()
            if (index != null) {
                if (index !in updated.indices) return false
                updated[index] = normalized
            } else {
                if (updated.size >= NativeSavedPlacesProtocol.MAX_STORED_ITEMS) return false
                updated += normalized
            }
            _state.value = _state.value.copy(
                favorites = Collections.unmodifiableList(updated),
                lastImportedCount = null,
            )
            true
        }

    fun delete(revision: Long, index: Int): Boolean = synchronized(lock) {
        if (!active(revision) || index !in _state.value.favorites.indices) return false
        val updated = _state.value.favorites.toMutableList().also { it.removeAt(index) }
        _state.value = _state.value.copy(
            favorites = Collections.unmodifiableList(updated),
            lastImportedCount = null,
        )
        true
    }

    fun mergeImported(revision: Long, incoming: Iterable<NativeSavedPlace>): Boolean =
        synchronized(lock) {
            if (!active(revision)) return false
            val valid = incoming.mapNotNull(NativeSavedPlacesProtocol::normalizeFavorite)
                .take(NativeSavedPlacesProtocol.MAX_STORED_ITEMS)
            _state.value = _state.value.copy(
                favorites = NativeSavedPlacesProtocol.mergeByLabel(_state.value.favorites, valid),
                lastImportedCount = valid.size,
            )
            true
        }

    fun setParking(revision: Long, parking: NativeParkingPosition): Boolean = synchronized(lock) {
        if (!active(revision)) return false
        val normalized = try {
            NativeSavedPlacesProtocol.decodeParking(NativeSavedPlacesProtocol.encodeParking(parking))
        } catch (_: IllegalArgumentException) {
            null
        } ?: return false
        _state.value = _state.value.copy(parking = normalized)
        true
    }

    fun clearParking(revision: Long): Boolean = synchronized(lock) {
        if (!active(revision) || _state.value.parking == null) return false
        _state.value = _state.value.copy(parking = null)
        true
    }

    fun hide(revision: Long): Boolean = synchronized(lock) {
        if (!active(revision)) return false
        _state.value = NativeSavedPlacesSnapshot.hidden().copy(revision = revision)
        true
    }

    private fun active(revision: Long): Boolean =
        revision == this.revision && _state.value.status == NativeSavedPlacesStatus.Ready
}
