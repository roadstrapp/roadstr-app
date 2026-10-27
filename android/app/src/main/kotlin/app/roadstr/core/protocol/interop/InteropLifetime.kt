package app.roadstr.core.protocol.interop

/** One confirmation or denial of a report, reduced to what decides its lifetime. */
data class InteropVote(
    val pubkey: String,
    val createdAt: Long,
    val stillThere: Boolean,
)

/**
 * How long a report heard on the radio stays on the map ("Display lifetime" in nips/road-events.md,
 * jooray/roadbeacon), as pure functions of the report and its votes.
 *
 * The marker lives for the type's own lifetime, pushed forward by every "still there" and cut short
 * by every "no longer there", and never past the event's own `expiration` tag: relays may drop the
 * event then. That tag is `created_at + max(14 days, lifetime)`, so a 30-day speed camera is shown
 * for 30 days, not 14.
 *
 * Choices the specification leaves open, made here once:
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

    /**
     * When the marker stops being shown. [votes] must already be [admissible]; [expiration] is the
     * event's NIP-40 tag, if it has one.
     */
    fun effectiveExpiry(
        reportCreatedAt: Long,
        type: CompactRoadType,
        votes: Collection<InteropVote>,
        expiration: Long? = null,
    ): Long {
        val ttl = type.effectiveTtlSeconds
        var expiry = reportCreatedAt + ttl
        val ordered = votes.sortedWith(compareBy<InteropVote> { it.createdAt }.thenBy { it.stillThere })
        for (vote in ordered) {
            expiry = if (vote.stillThere) maxOf(expiry, vote.createdAt + ttl) else minOf(expiry, vote.createdAt)
        }
        return if (expiration != null) minOf(expiry, expiration) else expiry
    }

    /** More denials than confirmations in the last five minutes. A tie stays visible. */
    fun deniedByRecentMajority(votes: Collection<InteropVote>, nowSeconds: Long): Boolean {
        val recent = votes.filter { nowSeconds - it.createdAt < CONFLICT_WINDOW_SECONDS }
        val confirmations = recent.count { it.stillThere }
        return recent.size - confirmations > confirmations
    }

    /** Both rules together, bounded by the event's own `expiration` ([expiration], if any). */
    fun isVisible(
        reportCreatedAt: Long,
        type: CompactRoadType,
        votes: Collection<InteropVote>,
        nowSeconds: Long,
        expiration: Long? = null,
    ): Boolean {
        val counted = admissible(votes, reportCreatedAt, nowSeconds)
        if (nowSeconds >= effectiveExpiry(reportCreatedAt, type, counted, expiration)) return false
        return !deniedByRecentMajority(counted, nowSeconds)
    }

    /** Newer wins; on a tie the confirmation does (benefit of the doubt). */
    private fun supersedes(candidate: InteropVote, known: InteropVote): Boolean =
        candidate.createdAt > known.createdAt ||
            (candidate.createdAt == known.createdAt && candidate.stillThere && !known.stillThere)
}
