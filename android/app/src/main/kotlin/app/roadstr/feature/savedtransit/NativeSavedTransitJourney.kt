package app.roadstr.feature.savedtransit

import app.roadstr.core.network.TransitAccessibility
import app.roadstr.core.network.TransitAlert
import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitLeg
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitProvider
import app.roadstr.core.network.TransitResponsePoint
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitStop
import app.roadstr.core.network.TransitRequestPoint
import app.roadstr.core.network.TransitParsedPlan
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.feature.savedroute.NativeSavedRouteProtocol
import app.roadstr.feature.transit.NativeTransitNavigationCheckpoint
import app.roadstr.feature.transit.NativeTransitNavigationStage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.Collections
import java.util.UUID

enum class NativeSavedTransitState {
    SavedSnapshot,
    NeedsVerification,
    Updated,
    Unavailable,
}

data class NativeSavedTransitQuery(
    val originLabel: String,
    val destinationLabel: String,
    val origin: TransitRequestPoint,
    val destination: TransitRequestPoint,
    val requestedDeparture: Instant,
)

data class NativeSavedTransitJourney(
    val id: String,
    val name: String,
    val query: NativeSavedTransitQuery,
    val provider: TransitProvider,
    val serviceDay: LocalDate,
    val timezone: String,
    val original: TransitItinerary,
    val updated: TransitItinerary?,
    val state: NativeSavedTransitState,
    val savedAt: Instant,
    val lastVerifiedAt: Instant?,
)

enum class NativeSavedTransitDifferenceKind {
    DelayChanged,
    PlatformChanged,
    CancellationChanged,
    TripChanged,
    StopsChanged,
    GeometryChanged,
    UncertainIdentity,
}

data class NativeSavedTransitDifference(
    val legIndex: Int,
    val kind: NativeSavedTransitDifferenceKind,
)

/** Scoped IDs and scheduled values are authoritative; names alone never match a service. */
object NativeSavedTransitComparator {
    fun compare(
        original: TransitItinerary,
        updated: TransitItinerary,
    ): List<NativeSavedTransitDifference> {
        if (original.id != null && updated.id != null && original.id.providerId != updated.id.providerId) {
            return listOf(NativeSavedTransitDifference(-1, NativeSavedTransitDifferenceKind.UncertainIdentity))
        }
        val differences = mutableListOf<NativeSavedTransitDifference>()
        val count = maxOf(original.legs.size, updated.legs.size)
        for (index in 0 until count) {
            val before = original.legs.getOrNull(index)
            val after = updated.legs.getOrNull(index)
            if (before == null || after == null) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.StopsChanged)
                continue
            }
            val scopedIdentityKnown = before.tripId != null && after.tripId != null
            if (scopedIdentityKnown && before.tripId != after.tripId) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.TripChanged)
            } else if (!scopedIdentityKnown && before.mode.isTransit) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.UncertainIdentity)
            }
            if (before.startTime != after.startTime || before.endTime != after.endTime) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.DelayChanged)
            }
            if (before.from?.expectedPlatform != after.from?.expectedPlatform ||
                before.to?.expectedPlatform != after.to?.expectedPlatform
            ) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.PlatformChanged)
            }
            if (before.cancelled != after.cancelled || before.from?.cancelled != after.from?.cancelled) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.CancellationChanged)
            }
            if (before.intermediateStops.map { it.id } != after.intermediateStops.map { it.id }) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.StopsChanged)
            }
            if (before.geometry != after.geometry) {
                differences += NativeSavedTransitDifference(index, NativeSavedTransitDifferenceKind.GeometryChanged)
            }
        }
        return differences.distinct()
    }
}

/** Versioned, bounded binary snapshot encoded as Base64 inside a separate encrypted value. */
object NativeSavedTransitProtocol {
    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_JOURNEYS = 12
    const val MAX_STORED_BYTES = 2 * 1024 * 1024
    private const val MAX_TEXT_BYTES = 8 * 1024
    private const val MAX_LEGS = 128
    private const val MAX_STOPS = 1_024
    private const val MAX_ALERTS = 256
    private const val MAGIC = 0x5253544A // RSTJ

    fun create(
        name: String,
        originLabel: String,
        destinationLabel: String,
        plan: TransitParsedPlan,
        itinerary: TransitItinerary,
        timezone: ZoneId,
        now: Instant,
        id: String = UUID.randomUUID().toString(),
    ): NativeSavedTransitJourney {
        val origin = requireNotNull(plan.origin) { "Saved transit origin is missing" }
        val destination = requireNotNull(plan.destination) { "Saved transit destination is missing" }
        val departure = plan.requestedDeparture ?: itinerary.startTime
        return normalize(
            NativeSavedTransitJourney(
                id = id,
                name = name,
                query = NativeSavedTransitQuery(
                    originLabel,
                    destinationLabel,
                    origin,
                    destination,
                    departure,
                ),
                provider = plan.provider,
                serviceDay = itinerary.startTime.atZone(timezone).toLocalDate(),
                timezone = timezone.id,
                original = itinerary,
                updated = null,
                state = NativeSavedTransitState.SavedSnapshot,
                savedAt = now,
                lastVerifiedAt = null,
            ),
        ) ?: error("Saved transit journey is invalid")
    }

    fun withUpdate(
        journey: NativeSavedTransitJourney,
        updated: TransitItinerary?,
        verifiedAt: Instant,
    ): NativeSavedTransitJourney = requireNotNull(
        normalize(
            journey.copy(
                updated = updated,
                state = if (updated == null) NativeSavedTransitState.Unavailable else NativeSavedTransitState.Updated,
                lastVerifiedAt = verifiedAt,
            ),
        ),
    )

    fun needsVerification(journey: NativeSavedTransitJourney): NativeSavedTransitJourney =
        journey.copy(state = NativeSavedTransitState.NeedsVerification)

    fun upsert(
        current: List<NativeSavedTransitJourney>,
        value: NativeSavedTransitJourney,
    ): List<NativeSavedTransitJourney> = Collections.unmodifiableList(
        (listOf(requireNotNull(normalize(value))) + current.filterNot { it.id == value.id })
            .mapNotNull(::normalize)
            .sortedByDescending { it.savedAt }
            .take(MAX_JOURNEYS),
    )

    fun encode(journeys: List<NativeSavedTransitJourney>): String {
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(CURRENT_SCHEMA_VERSION)
                val values = journeys.mapNotNull(::normalize).take(MAX_JOURNEYS)
                output.writeInt(values.size)
                values.forEach { output.writeJourney(it) }
            }
        }.toByteArray()
        require(payload.size <= MAX_STORED_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val framed = ByteArray(payload.size + digest.size)
        payload.copyInto(framed)
        digest.copyInto(framed, payload.size)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(framed)
    }

    fun decode(encoded: String?): List<NativeSavedTransitJourney> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_STORED_BYTES * 2) return emptyList()
        return runCatching {
            val framed = Base64.getUrlDecoder().decode(encoded)
            if (framed.size <= 32 || framed.size > MAX_STORED_BYTES + 32) return emptyList()
            val payload = framed.copyOfRange(0, framed.size - 32)
            val digest = framed.copyOfRange(framed.size - 32, framed.size)
            if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload), digest)) {
                return emptyList()
            }
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                if (input.readInt() != MAGIC) return emptyList()
                if (input.readInt() != CURRENT_SCHEMA_VERSION) return emptyList()
                val count = input.readBoundedCount(MAX_JOURNEYS)
                val values = List(count) { input.readJourney() }
                    .mapNotNull(::normalize)
                    .distinctBy(NativeSavedTransitJourney::id)
                if (input.available() != 0) return emptyList()
                Collections.unmodifiableList(values)
            }
        }.getOrDefault(emptyList())
    }

    private fun normalize(value: NativeSavedTransitJourney): NativeSavedTransitJourney? {
        if (!value.id.matches(Regex("^[A-Za-z0-9._-]{1,80}$"))) return null
        if (value.name.isBlank() || value.name.length > 160) return null
        if (value.query.originLabel.length > 500 || value.query.destinationLabel.length > 500) return null
        if (!valid(value.query.origin) || !valid(value.query.destination)) return null
        if (value.provider.id.isBlank() || value.provider.id.length > 80) return null
        if (runCatching { ZoneId.of(value.timezone) }.isFailure) return null
        if (!valid(value.original) || value.updated?.let(::valid) == false) return null
        if (value.updated != null && value.updated.id?.providerId != null &&
            value.updated.id.providerId != value.provider.id
        ) return null
        return value.copy(
            name = value.name.trim(),
            query = value.query.copy(
                originLabel = value.query.originLabel.trim(),
                destinationLabel = value.query.destinationLabel.trim(),
            ),
        )
    }

    private fun valid(itinerary: TransitItinerary): Boolean =
        itinerary.legs.isNotEmpty() && itinerary.legs.size <= MAX_LEGS &&
            itinerary.durationSeconds >= 0 && itinerary.transfers >= 0 &&
            itinerary.legs.all { leg ->
                leg.durationSeconds >= 0 && leg.geometry.size <= 250_000 &&
                    leg.intermediateStops.size <= MAX_STOPS && leg.alerts.size <= MAX_ALERTS &&
                    leg.geometry.all(::valid)
            }

    private fun valid(point: TransitRequestPoint) =
        point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
            point.longitude.isFinite() && point.longitude in -180.0..180.0

    private fun valid(point: TransitResponsePoint) =
        point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
            point.longitude.isFinite() && point.longitude in -180.0..180.0

    private fun DataOutputStream.writeJourney(value: NativeSavedTransitJourney) {
        writeText(value.id)
        writeText(value.name)
        writeText(value.query.originLabel)
        writeText(value.query.destinationLabel)
        writeRequestPoint(value.query.origin)
        writeRequestPoint(value.query.destination)
        writeInstant(value.query.requestedDeparture)
        writeText(value.provider.id)
        writeText(value.provider.displayName)
        writeNullableText(value.provider.attributionUrl)
        writeText(value.serviceDay.toString())
        writeText(value.timezone)
        writeItinerary(value.original)
        writeBoolean(value.updated != null)
        value.updated?.let { writeItinerary(it) }
        writeInt(value.state.ordinal)
        writeInstant(value.savedAt)
        writeNullableInstant(value.lastVerifiedAt)
    }

    private fun DataInputStream.readJourney(): NativeSavedTransitJourney = NativeSavedTransitJourney(
        id = readText(),
        name = readText(),
        query = NativeSavedTransitQuery(
            originLabel = readText(),
            destinationLabel = readText(),
            origin = readRequestPoint(),
            destination = readRequestPoint(),
            requestedDeparture = readInstant(),
        ),
        provider = TransitProvider(readText(), readText(), readNullableText()),
        serviceDay = LocalDate.parse(readText()),
        timezone = readText(),
        original = readItinerary(),
        updated = if (readBoolean()) readItinerary() else null,
        state = NativeSavedTransitState.entries.getOrNull(readInt()) ?: error("Invalid saved transit state"),
        savedAt = readInstant(),
        lastVerifiedAt = readNullableInstant(),
    )

    private fun DataOutputStream.writeItinerary(value: TransitItinerary) {
        writeLong(value.durationSeconds)
        writeInstant(value.startTime)
        writeInstant(value.endTime)
        writeInt(value.transfers)
        writeScopedId(value.id)
        writeInt(value.providerRank)
        writeInt(value.legs.size)
        value.legs.forEach { writeLeg(it) }
    }

    private fun DataInputStream.readItinerary(): TransitItinerary {
        val duration = readLong()
        val start = readInstant()
        val end = readInstant()
        val transfers = readInt()
        val id = readScopedId()
        val rank = readInt()
        val legs = List(readBoundedCount(MAX_LEGS)) { readLeg() }
        return TransitItinerary(duration, start, end, transfers, legs, id, rank)
    }

    private fun DataOutputStream.writeLeg(value: TransitLeg) {
        writeInt(value.mode.ordinal)
        writeNullableDouble(value.distanceMeters)
        writeLong(value.durationSeconds)
        writeInstant(value.startTime)
        writeInstant(value.endTime)
        writeNullableText(value.fromName)
        writeNullableText(value.toName)
        writeNullableText(value.routeShortName)
        writeNullableText(value.routeLongName)
        writeNullableText(value.agencyName)
        writeNullableText(value.headsign)
        writeNullableLong(value.routeColorArgb)
        writeNullableLong(value.routeTextColorArgb)
        writeBoolean(value.realTime)
        writeGeometry(value.geometry)
        writeStop(value.from)
        writeStop(value.to)
        writeInstant(value.scheduledStartTime)
        writeInstant(value.scheduledEndTime)
        writeNullableInstant(value.actualStartTime)
        writeNullableInstant(value.actualEndTime)
        writeScopedId(value.routeId)
        writeScopedId(value.tripId)
        writeScopedId(value.agencyId)
        writeNullableText(value.displayName)
        writeInt(value.intermediateStops.size)
        value.intermediateStops.forEach { writeStop(it) }
        writeBoolean(value.cancelled)
        writeNullableBoolean(value.scheduled)
        writeInt(value.accessibility.ordinal)
        writeInt(value.alerts.size)
        value.alerts.forEach { writeAlert(it) }
        writeNullableText(value.sourceReference)
    }

    private fun DataInputStream.readLeg(): TransitLeg {
        val mode = TransitMode.entries.getOrNull(readInt()) ?: error("Invalid transit mode")
        val distance = readNullableDouble()
        val duration = readLong()
        val start = readInstant()
        val end = readInstant()
        val fromName = readNullableText()
        val toName = readNullableText()
        val shortName = readNullableText()
        val longName = readNullableText()
        val agencyName = readNullableText()
        val headsign = readNullableText()
        val background = readNullableLong()
        val foreground = readNullableLong()
        val realtime = readBoolean()
        val geometry = readGeometry()
        val from = readStop()
        val to = readStop()
        val scheduledStart = readInstant()
        val scheduledEnd = readInstant()
        val actualStart = readNullableInstant()
        val actualEnd = readNullableInstant()
        val routeId = readScopedId()
        val tripId = readScopedId()
        val agencyId = readScopedId()
        val displayName = readNullableText()
        val stops = List(readBoundedCount(MAX_STOPS)) { requireNotNull(readStop()) }
        val cancelled = readBoolean()
        val scheduled = readNullableBoolean()
        val accessibility = TransitAccessibility.entries.getOrNull(readInt())
            ?: error("Invalid transit accessibility")
        val alerts = List(readBoundedCount(MAX_ALERTS)) { readAlert() }
        val sourceReference = readNullableText()
        return TransitLeg(
            mode, distance, duration, start, end, fromName, toName, shortName, longName,
            agencyName, headsign, background, foreground, realtime, geometry, from, to,
            scheduledStart, scheduledEnd, actualStart, actualEnd, routeId, tripId, agencyId,
            displayName, stops, cancelled, scheduled, accessibility, alerts, sourceReference,
        )
    }

    private fun DataOutputStream.writeStop(value: TransitStop?) {
        writeBoolean(value != null)
        if (value == null) return
        writeScopedId(value.id)
        writeNullableText(value.name)
        writePoint(value.position)
        writeNullableText(value.timezone)
        writeNullableInstant(value.scheduledArrival)
        writeNullableInstant(value.expectedArrival)
        writeNullableInstant(value.actualArrival)
        writeNullableInstant(value.scheduledDeparture)
        writeNullableInstant(value.expectedDeparture)
        writeNullableInstant(value.actualDeparture)
        writeNullableText(value.scheduledPlatform)
        writeNullableText(value.expectedPlatform)
        writeBoolean(value.cancelled)
        writeInt(value.alerts.size)
        value.alerts.take(MAX_ALERTS).forEach { writeAlert(it) }
    }

    private fun DataInputStream.readStop(): TransitStop? {
        if (!readBoolean()) return null
        return TransitStop(
            id = readScopedId(),
            name = readNullableText(),
            position = readPoint(),
            timezone = readNullableText(),
            scheduledArrival = readNullableInstant(),
            expectedArrival = readNullableInstant(),
            actualArrival = readNullableInstant(),
            scheduledDeparture = readNullableInstant(),
            expectedDeparture = readNullableInstant(),
            actualDeparture = readNullableInstant(),
            scheduledPlatform = readNullableText(),
            expectedPlatform = readNullableText(),
            cancelled = readBoolean(),
            alerts = List(readBoundedCount(MAX_ALERTS)) { readAlert() },
        )
    }

    private fun DataOutputStream.writeAlert(value: TransitAlert) {
        writeNullableText(value.code)
        writeText(value.header)
        writeNullableText(value.description)
        writeNullableText(value.severity)
        writeNullableText(value.effect)
        writeNullableText(value.url)
    }

    private fun DataInputStream.readAlert() = TransitAlert(
        readNullableText(),
        readText(),
        readNullableText(),
        readNullableText(),
        readNullableText(),
        readNullableText(),
    )

    private fun DataOutputStream.writeGeometry(points: List<TransitResponsePoint>) {
        writeInt(points.size)
        when (points.size) {
            0 -> Unit
            1 -> {
                writeDouble(points[0].latitude)
                writeDouble(points[0].longitude)
            }
            else -> writeText(
                NativeSavedRouteProtocol.encodePolyline6(
                    points.map { RoutingResponsePoint(it.latitude, it.longitude) },
                ),
            )
        }
    }

    private fun DataInputStream.readGeometry(): List<TransitResponsePoint> {
        val count = readBoundedCount(250_000)
        if (count == 0) return emptyList()
        if (count == 1) return listOf(TransitResponsePoint(readDouble(), readDouble()))
        val decoded = NativeSavedRouteProtocol.decodePolyline6(readText()) ?: error("Invalid transit geometry")
        if (decoded.size != count) error("Transit geometry point count changed")
        return decoded.map { TransitResponsePoint(it.latitude, it.longitude) }
    }

    private fun DataOutputStream.writeScopedId(value: TransitScopedId?) {
        writeBoolean(value != null)
        if (value != null) {
            writeText(value.providerId)
            writeText(value.value)
        }
    }

    private fun DataInputStream.readScopedId(): TransitScopedId? =
        if (readBoolean()) TransitScopedId(readText(), readText()) else null

    private fun DataOutputStream.writeRequestPoint(value: TransitRequestPoint) {
        writeDouble(value.latitude)
        writeDouble(value.longitude)
    }

    private fun DataInputStream.readRequestPoint() = TransitRequestPoint(readDouble(), readDouble())

    private fun DataOutputStream.writePoint(value: TransitResponsePoint?) {
        writeBoolean(value != null)
        if (value != null) {
            writeDouble(value.latitude)
            writeDouble(value.longitude)
        }
    }

    private fun DataInputStream.readPoint(): TransitResponsePoint? =
        if (readBoolean()) TransitResponsePoint(readDouble(), readDouble()) else null

    private fun DataOutputStream.writeInstant(value: Instant) = writeLong(value.toEpochMilli())
    private fun DataInputStream.readInstant(): Instant = Instant.ofEpochMilli(readLong())

    private fun DataOutputStream.writeNullableInstant(value: Instant?) {
        writeBoolean(value != null)
        if (value != null) writeInstant(value)
    }

    private fun DataInputStream.readNullableInstant(): Instant? = if (readBoolean()) readInstant() else null

    private fun DataOutputStream.writeNullableDouble(value: Double?) {
        writeBoolean(value != null)
        if (value != null) writeDouble(value)
    }

    private fun DataInputStream.readNullableDouble(): Double? = if (readBoolean()) readDouble() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableBoolean(value: Boolean?) {
        writeByte(when (value) { null -> -1; false -> 0; true -> 1 })
    }

    private fun DataInputStream.readNullableBoolean(): Boolean? = when (readByte().toInt()) {
        -1 -> null
        0 -> false
        1 -> true
        else -> error("Invalid nullable boolean")
    }

    private fun DataOutputStream.writeNullableText(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeText(value)
    }

    private fun DataInputStream.readNullableText(): String? = if (readBoolean()) readText() else null

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String {
        val size = readBoundedCount(MAX_TEXT_BYTES)
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readBoundedCount(maximum: Int): Int {
        val value = readInt()
        if (value !in 0..maximum) error("Saved transit count is outside its bound")
        return value
    }
}

class NativeSavedTransitStore(
    private val readEncryptedValue: () -> String?,
    private val writeEncryptedValue: (String) -> Boolean,
    private val removeEncryptedValue: () -> Boolean,
) {
    fun load(): List<NativeSavedTransitJourney> = NativeSavedTransitProtocol.decode(readEncryptedValue())

    fun save(values: List<NativeSavedTransitJourney>): Boolean {
        val bounded = values.take(NativeSavedTransitProtocol.MAX_JOURNEYS)
        if (bounded.isEmpty()) return removeEncryptedValue()
        return runCatching { writeEncryptedValue(NativeSavedTransitProtocol.encode(bounded)) }.getOrDefault(false)
    }

    fun upsert(
        current: List<NativeSavedTransitJourney>,
        value: NativeSavedTransitJourney,
    ): List<NativeSavedTransitJourney>? = NativeSavedTransitProtocol.upsert(current, value).takeIf(::save)
}

data class NativeActiveTransitJourney(
    val itinerary: TransitItinerary,
    val checkpoint: NativeTransitNavigationCheckpoint,
)

/**
 * Small, checksummed payload stored inside protected preferences while transit guidance is active.
 * It intentionally has a different magic/version from the user's saved-journey collection so a
 * corrupt or future payload fails closed without affecting saved trips.
 */
object NativeActiveTransitProtocol {
    private const val MAGIC = 0x52535441 // RSTA
    private const val VERSION = 1
    private const val MAX_BYTES = NativeSavedTransitProtocol.MAX_STORED_BYTES + 1024 * 1024

    fun encode(value: NativeActiveTransitJourney): String {
        val holder = NativeSavedTransitJourney(
            id = "active",
            name = "Active transit guidance",
            query = NativeSavedTransitQuery(
                originLabel = "",
                destinationLabel = "",
                origin = value.itinerary.legs.first().geometry.firstOrNull()?.let {
                    TransitRequestPoint(it.latitude, it.longitude)
                } ?: TransitRequestPoint(0.0, 0.0),
                destination = value.itinerary.legs.last().geometry.lastOrNull()?.let {
                    TransitRequestPoint(it.latitude, it.longitude)
                } ?: TransitRequestPoint(0.0, 0.0),
                requestedDeparture = value.itinerary.startTime,
            ),
            provider = TransitProvider(
                value.itinerary.id?.providerId
                    ?: value.itinerary.transitLegs.firstNotNullOfOrNull { it.tripId?.providerId }
                    ?: "unknown",
                "Active transit provider",
                null,
            ),
            serviceDay = value.itinerary.startTime.atZone(ZoneId.of("UTC")).toLocalDate(),
            timezone = "UTC",
            original = value.itinerary,
            updated = null,
            state = NativeSavedTransitState.SavedSnapshot,
            savedAt = value.itinerary.startTime,
            lastVerifiedAt = null,
        )
        val itineraryPayload = NativeSavedTransitProtocol.encode(listOf(holder)).toByteArray(Charsets.UTF_8)
        val bytes = ByteArrayOutputStream().also { raw ->
            DataOutputStream(raw).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(itineraryPayload.size)
                output.write(itineraryPayload)
                val checkpoint = value.checkpoint
                output.writeBoolean(checkpoint.itineraryId != null)
                checkpoint.itineraryId?.take(8_192)?.let(output::writeUTF)
                output.writeLong(checkpoint.revision)
                output.writeInt(checkpoint.stage.ordinal)
                output.writeInt(checkpoint.legIndex)
                output.writeInt(checkpoint.suspendedFrom?.ordinal ?: -1)
                output.writeLong(checkpoint.verifySequence)
                output.writeLong(checkpoint.replanSequence)
                output.writeInt(checkpoint.intermediateStopIndex)
            }
        }.toByteArray()
        require(bytes.size <= MAX_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes + digest)
    }

    fun decode(encoded: String?): NativeActiveTransitJourney? {
        if (encoded.isNullOrBlank() || encoded.length > MAX_BYTES * 2) return null
        return runCatching {
            val framed = Base64.getUrlDecoder().decode(encoded)
            if (framed.size <= 32 || framed.size > MAX_BYTES + 32) return null
            val payload = framed.copyOfRange(0, framed.size - 32)
            val digest = framed.copyOfRange(framed.size - 32, framed.size)
            if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload), digest)) return null
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val size = input.readInt()
                if (size !in 1..MAX_BYTES || size > input.available()) return null
                val itineraryEncoded = ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
                val itinerary = NativeSavedTransitProtocol.decode(itineraryEncoded).singleOrNull()?.original ?: return null
                val itineraryId = if (input.readBoolean()) input.readUTF().take(8_192) else null
                val revision = input.readLong()
                val stage = NativeTransitNavigationStage.entries.getOrNull(input.readInt()) ?: return null
                val legIndex = input.readInt()
                val suspendedOrdinal = input.readInt()
                val suspended = if (suspendedOrdinal < 0) null else
                    NativeTransitNavigationStage.entries.getOrNull(suspendedOrdinal) ?: return null
                val checkpoint = NativeTransitNavigationCheckpoint(
                    itineraryId = itineraryId,
                    revision = revision,
                    stage = stage,
                    legIndex = legIndex,
                    suspendedFrom = suspended,
                    verifySequence = input.readLong(),
                    replanSequence = input.readLong(),
                    intermediateStopIndex = input.readInt(),
                )
                if (input.available() != 0) return null
                NativeActiveTransitJourney(itinerary, checkpoint)
            }
        }.getOrNull()
    }
}

class NativeActiveTransitStore(
    private val readEncryptedValue: () -> String?,
    private val writeEncryptedValue: (String) -> Boolean,
    private val removeEncryptedValue: () -> Boolean,
) {
    fun load(): NativeActiveTransitJourney? = NativeActiveTransitProtocol.decode(readEncryptedValue())

    fun save(value: NativeActiveTransitJourney?): Boolean = if (value == null) {
        removeEncryptedValue()
    } else {
        runCatching { writeEncryptedValue(NativeActiveTransitProtocol.encode(value)) }.getOrDefault(false)
    }
}
