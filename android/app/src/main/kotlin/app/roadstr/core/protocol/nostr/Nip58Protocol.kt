package app.roadstr.core.protocol.nostr

data class Nip58BadgeReference(val issuerPubkey: String, val identifier: String) {
    val address: String get() = "${Nip58Protocol.BADGE_DEFINITION_KIND}:$issuerPubkey:$identifier"
}

data class Nip58BadgeDefinition(
    val reference: Nip58BadgeReference,
    val name: String?,
    val description: String?,
    val imageUrl: String?,
    val thumbnailUrls: List<String>,
)

data class Nip58BadgeAward(
    val eventId: String,
    val reference: Nip58BadgeReference,
    val recipients: List<String>,
    val relayHint: String? = null,
)

/** Strict NIP-58 builders and validators. Issuer signing remains outside the Android client. */
object Nip58Protocol {
    const val BADGE_AWARD_KIND = 8
    const val PROFILE_BADGES_KIND = 10008
    const val BADGE_SET_KIND = 30008
    const val BADGE_DEFINITION_KIND = 30009

    fun definitionDraft(
        issuerPubkey: String,
        createdAt: Long,
        identifier: String,
        name: String,
        description: String,
        imageUrl: String,
        thumbnails: List<Pair<String, String>> = emptyList(),
    ): NostrEventDraft {
        requireIdentifier(identifier)
        requireHttps(imageUrl)
        thumbnails.forEach { requireHttps(it.first); requireDimensions(it.second) }
        return NostrEventDraft(
            issuerPubkey,
            createdAt,
            BADGE_DEFINITION_KIND,
            buildList {
                add(listOf("d", identifier))
                add(listOf("name", name.take(128)))
                add(listOf("description", description.take(512)))
                add(listOf("image", imageUrl, "1024x1024"))
                thumbnails.forEach { (url, dimensions) -> add(listOf("thumb", url, dimensions)) }
            },
            "",
        )
    }

    fun awardDraft(
        issuerPubkey: String,
        createdAt: Long,
        reference: Nip58BadgeReference,
        recipients: List<String>,
    ): NostrEventDraft {
        require(reference.issuerPubkey == issuerPubkey)
        val unique = recipients.distinct()
        require(unique.isNotEmpty() && unique.all(::isPubkey))
        return NostrEventDraft(
            issuerPubkey,
            createdAt,
            BADGE_AWARD_KIND,
            listOf(listOf("a", reference.address)) + unique.map { listOf("p", it) },
            "",
        )
    }

    fun badgeSetDraft(
        ownerPubkey: String,
        createdAt: Long,
        identifier: String,
        accepted: List<Nip58BadgeAward>,
    ): NostrEventDraft {
        requireIdentifier(identifier)
        return NostrEventDraft(
            ownerPubkey,
            createdAt,
            BADGE_SET_KIND,
            listOf(listOf("d", identifier)) + accepted.flatMap(::pairTags),
            "",
        )
    }

    /** Preserves every existing tag, including badges and sets from other projects. */
    fun profileBadgesDraft(
        ownerPubkey: String,
        createdAt: Long,
        existingTags: List<List<String>>,
        accepted: Nip58BadgeAward,
    ): NostrEventDraft {
        require(ownerPubkey in accepted.recipients)
        val pair = pairTags(accepted)
        val alreadyPresent = existingTags.zipWithNext().any { (a, e) -> a == pair[0] && e.firstOrNull() == "e" && e.getOrNull(1) == accepted.eventId }
        return NostrEventDraft(
            ownerPubkey,
            createdAt,
            PROFILE_BADGES_KIND,
            if (alreadyPresent) existingTags else existingTags + pair,
            "",
        )
    }

    fun parseDefinition(event: Map<String, Any?>, verifySignature: () -> Boolean): Nip58BadgeDefinition? {
        if (!basic(event, BADGE_DEFINITION_KIND, verifySignature)) return null
        val issuer = event["pubkey"] as? String ?: return null
        val tags = NativeNostrWireTags.read(event) ?: return null
        val identifier = tags.singleOrNull { it.firstOrNull() == "d" }?.getOrNull(1) ?: return null
        if (!validIdentifier(identifier)) return null
        val image = tags.firstOrNull { it.firstOrNull() == "image" }?.getOrNull(1)
        val thumbs = tags.filter { it.firstOrNull() == "thumb" }.mapNotNull { it.getOrNull(1) }
        if (image != null && !isHttps(image) || thumbs.any { !isHttps(it) }) return null
        return Nip58BadgeDefinition(
            Nip58BadgeReference(issuer, identifier),
            tags.firstOrNull { it.firstOrNull() == "name" }?.getOrNull(1),
            tags.firstOrNull { it.firstOrNull() == "description" }?.getOrNull(1),
            image,
            thumbs,
        )
    }

    fun parseAward(event: Map<String, Any?>, verifySignature: () -> Boolean): Nip58BadgeAward? {
        if (!basic(event, BADGE_AWARD_KIND, verifySignature)) return null
        val issuer = event["pubkey"] as? String ?: return null
        val id = event["id"] as? String ?: return null
        val tags = NativeNostrWireTags.read(event) ?: return null
        val addresses = tags.filter { it.firstOrNull() == "a" }.mapNotNull { it.getOrNull(1) }
        if (addresses.size != 1) return null
        val reference = parseAddress(addresses.single()) ?: return null
        if (reference.issuerPubkey != issuer) return null
        val recipients = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }.distinct()
        if (recipients.isEmpty() || recipients.any { !isPubkey(it) }) return null
        return Nip58BadgeAward(id, reference, recipients)
    }

    fun parseAddress(value: String): Nip58BadgeReference? {
        val fields = value.split(':', limit = 3)
        if (fields.size != 3 || fields[0] != BADGE_DEFINITION_KIND.toString() || !isPubkey(fields[1]) || !validIdentifier(fields[2])) return null
        return Nip58BadgeReference(fields[1], fields[2])
    }

    fun awardRequest(subscriptionId: String, issuer: String, recipient: String): List<Any?> = listOf(
        "REQ", subscriptionId, linkedMapOf(
            "kinds" to listOf(BADGE_AWARD_KIND),
            "authors" to listOf(issuer),
            "#p" to listOf(recipient),
            "limit" to 100,
        ),
    )

    fun definitionRequest(subscriptionId: String, reference: Nip58BadgeReference): List<Any?> = listOf(
        "REQ", subscriptionId, linkedMapOf(
            "kinds" to listOf(BADGE_DEFINITION_KIND),
            "authors" to listOf(reference.issuerPubkey),
            "#d" to listOf(reference.identifier),
            "limit" to 4,
        ),
    )

    fun profileRequest(subscriptionId: String, owner: String): List<Any?> = listOf(
        "REQ", subscriptionId, linkedMapOf(
            "kinds" to listOf(PROFILE_BADGES_KIND),
            "authors" to listOf(owner),
            "limit" to 4,
        ),
    )

    private fun pairTags(award: Nip58BadgeAward): List<List<String>> = listOf(
        listOf("a", award.reference.address),
        listOfNotNull("e", award.eventId, award.relayHint),
    )

    private fun basic(event: Map<String, Any?>, kind: Int, verifySignature: () -> Boolean): Boolean =
        (event["kind"] as? Number)?.toInt() == kind &&
            isPubkey(event["pubkey"] as? String) &&
            isPubkey(event["id"] as? String) &&
            verifySignature()

    private fun requireIdentifier(value: String) = require(validIdentifier(value)) { "Invalid NIP-58 identifier" }
    private fun validIdentifier(value: String): Boolean = value.length in 1..128 && '\n' !in value && '\u0000' !in value
    private fun isPubkey(value: String?): Boolean = value?.matches(HEX_64) == true
    private fun requireHttps(value: String) = require(isHttps(value)) { "Badge assets require stable HTTPS URLs" }
    private fun isHttps(value: String): Boolean = value.startsWith("https://") && value.length <= 2_048
    private fun requireDimensions(value: String) = require(value.matches(Regex("^[1-9][0-9]{0,4}x[1-9][0-9]{0,4}$")))
    private val HEX_64 = Regex("^[0-9a-f]{64}$")
}

private object NativeNostrWireTags {
    fun read(event: Map<String, Any?>): List<List<String>>? {
        val tags = event["tags"] as? List<*> ?: return null
        return tags.map { raw -> (raw as? List<*>)?.map { it as? String ?: return null } ?: return null }
    }
}
