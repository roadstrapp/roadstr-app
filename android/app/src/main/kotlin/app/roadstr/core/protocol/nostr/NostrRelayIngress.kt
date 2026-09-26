package app.roadstr.core.protocol.nostr

enum class NostrIngressVerdict(val wireName: String) {
    IGNORE("ignore"),
    REJECT_KIND("rejectKind"),
    VERIFY("verify"),
    LIMIT_REACHED("limitReached"),
}

enum class NostrIngressRoute(val wireName: String) {
    ROAD_EVENT("roadEvent"),
    ROAD_UPDATE("roadUpdate"),
    CONFIRMATION("confirmation"),
    OWN_CONFIRMATION("ownConfirmation"),
    ZAP_RECEIPT("zapReceipt"),
    USER_REPORT("userReport"),
    USER_UPDATE("userUpdate"),
    USER_VOTE("userVote"),
    EDIT_REQUEST("editRequest"),
    PROFILE_VISIBILITY("profileVisibility"),
    PROFILE_METADATA("profileMetadata"),
    FAVORITE_SNAPSHOT("favoriteSnapshot"),
    LIGHTNING_ADDRESS("lightningAddress"),
    NWC_RESPONSE("nwcResponse"),
    ZAP_RECEIPT_QUERY("zapReceiptQuery"),
}

class NostrIngressRule(
    val name: String,
    val subscriptionId: String,
    routes: Map<Int, NostrIngressRoute> = emptyMap(),
    val fallbackRoute: NostrIngressRoute? = null,
    val maxEvents: Int? = null,
) {
    val routes: Map<Int, NostrIngressRoute> = routes.toMap()

    init {
        require(name.isNotEmpty()) { "Ingress rule name must not be empty" }
        require(this.routes.isNotEmpty() || fallbackRoute != null) {
            "An ingress rule must expose at least one route"
        }
        require(maxEvents == null || maxEvents > 0) {
            "Ingress event budget must be positive"
        }
    }
}

data class NostrIngressDecision(
    val verdict: NostrIngressVerdict,
    val route: NostrIngressRoute? = null,
    val ruleName: String? = null,
    val observedEvents: Int = 0,
) {
    val shouldVerify: Boolean
        get() = verdict == NostrIngressVerdict.VERIFY

    val limitReached: Boolean
        get() = verdict == NostrIngressVerdict.LIMIT_REACHED
}

/**
 * Cheap admission and budget policy for already shape-checked relay EVENTs.
 *
 * A VERIFY result authorizes cryptographic verification, not trust or effects.
 * One instance is intended to be owned by one serialized relay stream.
 */
class NostrRelayIngress(rules: Iterable<NostrIngressRule>) {
    private val rules = rules.toList()
    private val counts = mutableMapOf<String, Int>()

    init {
        require(this.rules.map(NostrIngressRule::name).distinct().size == this.rules.size) {
            "Ingress rule names must be unique"
        }
    }

    /**
     * If ids collide, the first matching rule owns the budget while the first
     * matching kind route wins. This mirrors the current Dart dispatch order.
     */
    fun inspect(subscriptionId: String, claimedKind: Any?): NostrIngressDecision {
        val matching = rules.filter { rule -> rule.subscriptionId == subscriptionId }
        if (matching.isEmpty()) {
            return NostrIngressDecision(NostrIngressVerdict.IGNORE)
        }

        val budgetRule = matching.first()
        val observed = (counts[budgetRule.name] ?: 0) + 1
        counts[budgetRule.name] = observed
        val maximum = budgetRule.maxEvents
        if (maximum != null && observed > maximum) {
            return NostrIngressDecision(
                verdict = NostrIngressVerdict.LIMIT_REACHED,
                ruleName = budgetRule.name,
                observedEvents = observed,
            )
        }

        val kind = claimedKind.toIngressKind()
        var route: NostrIngressRoute? = null
        if (kind != null) {
            for (rule in matching) {
                route = rule.routes[kind]
                if (route != null) break
            }
        }
        if (route == null) {
            for (rule in matching) {
                route = rule.fallbackRoute
                if (route != null) break
            }
        }
        if (route == null) {
            return NostrIngressDecision(
                verdict = NostrIngressVerdict.REJECT_KIND,
                ruleName = budgetRule.name,
                observedEvents = observed,
            )
        }
        return NostrIngressDecision(
            verdict = NostrIngressVerdict.VERIFY,
            route = route,
            ruleName = budgetRule.name,
            observedEvents = observed,
        )
    }

    fun observedFor(ruleName: String): Int = counts[ruleName] ?: 0

    private fun Any?.toIngressKind(): Int? = when (this) {
        is Int -> this
        is Long -> takeIf { value -> value in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Short -> toInt()
        is Byte -> toInt()
        else -> null
    }
}
