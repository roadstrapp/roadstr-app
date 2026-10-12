package app.roadstr.core.protocol.interop

/** One confirmation or denial of an interoperable report, reduced to what decides its lifetime. */
data class InteropVote(
    val pubkey: String,
    val createdAt: Long,
    val stillThere: Boolean,
)

/**
 * How long an interoperable report stays on the map: sections 5.2 and 5.3 of the jooray/roadstr
 * specification, as pure functions of the report and its votes.
 *
 * The `expiration` tag of those events is a fixed 14 days that only tells relays when they may drop
 * the event. It is not how long the marker is shown: that is the type's own lifetime, pushed forward
 * by every "still there" and cut short by every "no longer there".
 *
 * Choices the specification leaves open, made here once and documented in MESHCORE_NOSTR.md:
 *  - one voice per key: only the latest vote of a key counts (a key cannot out-vote itself);
 *  - a vote older than its report, or more than five minutes in the future, is not admitted;
 *  - votes with the same timestamp are applied denials first, so a tie keeps the report alive
 *    ("benefit of the doubt", section 5.3).
 */
object InteropLifetime {
    const val CONFLICT_WINDOW_SECONDS = 300L
    const val MAX_FUTURE_SKEW_SECONDS = 300L

    /** Latest vote per key, minus the ones that cannot be genuine for this report. */
    fun admissible(
        votes: Collection<InteropVote>,
        reportCreatedAt: Long,
        nowSeconds: Long,
    ): List<InteropVote> {
        val latest = HashMap<String, InteropVote>()
        for (vote in votes) {
            if (vote.createdAt < reportCreatedAt) continue
            if (vote.createdAt > nowSeconds + MAX_FUTURE_SKEW_SECONDS) continue
            val known = latest[vote.pubkey]
            if (known == null || supersedes(vote, known)) latest[vote.pubkey] = vote
        }
        return latest.values.toList()
    }

    /** Section 5.2 `computeEffectiveExpiry`. [votes] must already be [admissible]. */
    fun effectiveExpiry(
        reportCreatedAt: Long,
        type: CompactRoadType,
        votes: Collection<InteropVote>,
    ): Long {
        val ttl = type.effectiveTtlSeconds
        var expiry = reportCreatedAt + ttl
        val ordered = votes.sortedWith(compareBy<InteropVote> { it.createdAt }.thenBy { it.stillThere })
        for (vote in ordered) {
            expiry = if (vote.stillThere) maxOf(expiry, vote.createdAt + ttl) else minOf(expiry, vote.createdAt)
        }
        return expiry
    }

    /** Section 5.3 `resolveConflicts`: more recent denials than confirmations. A tie stays active. */
    fun deniedByRecentMajority(votes: Collection<InteropVote>, nowSeconds: Long): Boolean {
        val recent = votes.filter { nowSeconds - it.createdAt < CONFLICT_WINDOW_SECONDS }
        val confirmations = recent.count { it.stillThere }
        return recent.size - confirmations > confirmations
    }

    /** Both rules together, and the relay-side expiration that makes an event unservable. */
    fun isVisible(
        reportCreatedAt: Long,
        type: CompactRoadType,
        votes: Collection<InteropVote>,
        nowSeconds: Long,
    ): Boolean {
        if (nowSeconds >= reportCreatedAt + CompactRoadCodec.RELAY_TTL_SECONDS) return false
        val counted = admissible(votes, reportCreatedAt, nowSeconds)
        if (nowSeconds >= effectiveExpiry(reportCreatedAt, type, counted)) return false
        return !deniedByRecentMajority(counted, nowSeconds)
    }

    /** Newer wins; on a tie the confirmation does (benefit of the doubt). */
    private fun supersedes(candidate: InteropVote, known: InteropVote): Boolean =
        candidate.createdAt > known.createdAt ||
            (candidate.createdAt == known.createdAt && candidate.stillThere && !known.stillThere)
}
