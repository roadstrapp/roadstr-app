package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import java.security.SecureRandom

/** Whether, and how, a Roadstr report can also be written in the jooray/roadstr layout. */
sealed interface CompatPlan {
    /**
     * [frameable] is true only without a comment: the comment goes to relays in `content`, but the
     * 110-byte radio frame has no room for it, and it is never cut to fit.
     */
    data class Eligible(
        val type: CompactRoadType,
        val latitudeE7: Int,
        val longitudeE7: Int,
        val comment: String,
        val frameable: Boolean,
    ) : CompatPlan

    data class Ineligible(val reason: CompatIneligible) : CompatPlan
}

enum class CompatIneligible {
    /** The format has no such type; mapping it to the nearest one would be inventing data. */
    NO_EQUIVALENT_CATEGORY,
    COORDINATES_OUT_OF_RANGE,
}

/** What the user chose for compatible publishing; everything is off until they ask for it. */
data class InteropPublishPolicy(
    val enabled: Boolean = false,
    /** Sign compatible reports with a throw-away key instead of the account's. */
    val ephemeralKey: Boolean = false,
) {
    companion object {
        val OFF = InteropPublishPolicy()
    }
}

object CompatReportPlanner {
    /**
     * A signed native event is never turned into a compatible one: a different layout is a different
     * event, so a new one is built from the same facts and signed on its own. What is lost is
     * stated, not hidden: the speed limit of a camera has no place in the format and stays in the
     * native event only.
     */
    fun plan(category: RoadCategoryWire, latitude: Double, longitude: Double, comment: String): CompatPlan {
        val type = CompactRoadType.fromNative(category)
            ?: return CompatPlan.Ineligible(CompatIneligible.NO_EQUIVALENT_CATEGORY)
        val latitudeE7 = CompactCoordinates.latitudeE7(latitude)
        val longitudeE7 = CompactCoordinates.longitudeE7(longitude)
        if (latitudeE7 == null || longitudeE7 == null) {
            return CompatPlan.Ineligible(CompatIneligible.COORDINATES_OUT_OF_RANGE)
        }
        return CompatPlan.Eligible(type, latitudeE7, longitudeE7, comment, frameable = comment.isEmpty())
    }
}

/**
 * A key used once and forgotten. A report signed with it cannot be linked to the account that made
 * it, which is the point; the cost is that nobody can tell it apart from a stranger's, so other
 * Roadstr users will see it next to the account's own native report instead of merged with it.
 */
object OneTimeKey {
    class Pair(val privateKeyHex: String, val publicKeyHex: String)

    fun generate(random: SecureRandom = SecureRandom()): Pair {
        while (true) {
            val bytes = ByteArray(32).also(random::nextBytes)
            val privateKey = bytes.joinToString("") { "%02x".format(it) }
            val publicKey = runCatching { NostrSchnorr.publicKey(privateKey) }.getOrNull() ?: continue
            return Pair(privateKey, publicKey)
        }
    }
}
