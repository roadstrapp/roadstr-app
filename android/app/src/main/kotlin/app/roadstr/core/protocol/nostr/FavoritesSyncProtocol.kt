package app.roadstr.core.protocol.nostr

import java.math.BigDecimal
import java.net.URI
import java.security.MessageDigest

/** Deterministic NIP-78 favourites policy around crypto, storage and sockets. */
object FavoritesSyncProtocol {
    val defaultRelays: List<String> = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://purplerelay.com",
    )
    const val LEGACY_D_TAG = "roadstr-favorites"
    const val KIND = 30078
    const val MAX_CONTENT_CHARS = 200_000
    const val MAX_PLAINTEXT_BYTES = 65_535
    const val PAD_BUCKET = 4096

    fun normaliseRelayUrl(input: String): String? {
        return try {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.length > 200) return null
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if (
                scheme != "wss" ||
                host.isNullOrEmpty() ||
                '.' !in host ||
                !uri.userInfo.isNullOrEmpty() ||
                uri.rawQuery != null ||
                uri.rawFragment != null
            ) {
                return null
            }
            val path = if (uri.rawPath == "/") "" else normalizeEscapes(uri.rawPath.orEmpty())
            val normalized = buildString {
                append("wss://").append(host)
                if (uri.port >= 0) append(':').append(uri.port)
                append(path)
            }
            normalized.takeUnless(defaultRelays::contains)
        } catch (_: Exception) {
            null
        }
    }

    fun hashedDTag(pubkey: String): String = sha256Hex("$LEGACY_D_TAG:$pubkey")

    fun encodeFavorites(favorites: List<Map<String, Any?>>): String = FavoritesJson.encode(favorites)

    fun wrapPassphraseEnvelope(encrypted: Map<String, Any?>): String {
        val envelope = linkedMapOf<String, Any?>(
            "v" to 1,
            "encrypted" to true,
        )
        envelope.putAll(encrypted)
        return FavoritesJson.encode(envelope)
    }

    fun padToBucket(value: String): String {
        val length = value.toByteArray(Charsets.UTF_8).size
        require(length <= MAX_PLAINTEXT_BYTES) {
            "NIP-44 plaintext exceeds 65535 bytes"
        }
        if (length == MAX_PLAINTEXT_BYTES) return value
        val rounded = ((length + PAD_BUCKET - 1) / PAD_BUCKET) * PAD_BUCKET
        val target = minOf(rounded, MAX_PLAINTEXT_BYTES)
        return value + " ".repeat(target - length)
    }

    fun nextCreatedAt(nowUnixSeconds: Long, lastCreatedAt: Long): Long {
        val hourStart = nowUnixSeconds - nowUnixSeconds % 3600L
        return maxOf(hourStart, lastCreatedAt + 1L)
    }

    fun snapshotDraft(
        pubkey: String,
        createdAt: Long,
        encryptedContent: String,
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = KIND,
        tags = listOf(listOf("d", hashedDTag(pubkey))),
        content = encryptedContent,
    )

    fun legacyWipeDraft(pubkey: String, createdAt: Long): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = KIND,
        tags = listOf(listOf("d", LEGACY_D_TAG)),
        content = "",
    )

    fun legacyDeletionDraft(pubkey: String, createdAt: Long): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = 5,
        tags = listOf(listOf("a", "$KIND:$pubkey:$LEGACY_D_TAG")),
        content = "",
    )

    fun fetchRequest(
        subscriptionId: String,
        pubkey: String,
        dTag: String,
    ): List<Any?> = listOf(
        "REQ",
        subscriptionId,
        linkedMapOf(
            "kinds" to listOf(KIND),
            "authors" to listOf(pubkey),
            "#d" to listOf(dTag),
            "limit" to 1,
        ),
    )

    fun snapshotEventIsBound(
        event: Map<String, Any?>,
        pubkey: String,
        dTag: String,
        verifySignature: () -> Boolean,
    ): Boolean {
        return try {
            val content = event["content"]
            if (content is String && content.length > MAX_CONTENT_CHARS) return false
            val tags = event["tags"] as? List<*> ?: emptyList<Any?>()
            val dMatches = tags.any { rawTag ->
                rawTag is List<*> &&
                    rawTag.size >= 2 &&
                    rawTag[0] == "d" &&
                    rawTag[1] == dTag
            }
            val kind = event["kind"] as? Number
            event["pubkey"] == pubkey &&
                kind?.toDouble() == KIND.toDouble() &&
                dMatches &&
                verifySignature()
        } catch (_: Exception) {
            false
        }
    }

    /** First event wins ties, matching the shipped relay-order reduction. */
    fun newestSnapshot(events: Iterable<Map<String, Any?>?>): Map<String, Any?>? {
        var best: Map<String, Any?>? = null
        for (event in events) {
            if (event == null) continue
            val eventTime = (event["created_at"] as? Number)?.toLong() ?: 0L
            val bestTime = (best?.get("created_at") as? Number)?.toLong() ?: 0L
            if (best == null || eventTime > bestTime) best = event
        }
        return best
    }

    fun passesRollbackGuard(fetchedCreatedAt: Long, lastCreatedAt: Long?): Boolean =
        lastCreatedAt == null || fetchedCreatedAt >= lastCreatedAt

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun normalizeEscapes(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (
                value[index] == '%' &&
                index + 2 < value.length &&
                value[index + 1].isHexDigit() &&
                value[index + 2].isHexDigit()
            ) {
                result.append('%')
                result.append(value[index + 1].uppercaseChar())
                result.append(value[index + 2].uppercaseChar())
                index += 3
            } else {
                result.append(value[index++])
            }
        }
        return result.toString()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

/** JSON writer for user data, including the finite doubles used by coordinates. */
private object FavoritesJson {
    fun encode(value: Any?): String = buildString { appendValue(value) }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean -> append(if (value) "true" else "false")
            is Byte, is Short, is Int, is Long -> append(value.toString())
            is Float -> {
                require(value.isFinite()) { "JSON does not support non-finite numbers" }
                appendDartDouble(value.toString(), value.toDouble())
            }

            is Double -> {
                require(value.isFinite()) { "JSON does not support non-finite numbers" }
                appendDartDouble(value.toString(), value)
            }

            is List<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendValue(item)
                }
                append(']')
            }

            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { index, entry ->
                    require(entry.key is String) { "JSON object keys must be strings" }
                    if (index > 0) append(',')
                    appendString(entry.key as String)
                    append(':')
                    appendValue(entry.value)
                }
                append('}')
            }

            else -> throw IllegalArgumentException(
                "Unsupported favourites JSON value: ${value::class.java.name}",
            )
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        for (index in value.indices) {
            val character = value[index]
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\u000c' -> append("\\f")
                '\r' -> append("\\r")
                else -> {
                    val isUnpairedSurrogate =
                        Character.isHighSurrogate(character) &&
                            (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) ||
                            Character.isLowSurrogate(character) &&
                            (index == 0 || !Character.isHighSurrogate(value[index - 1]))
                    if (character.code < 0x20 || isUnpairedSurrogate) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    /** Mirrors Dart double.toString's fixed/exponential display thresholds. */
    private fun StringBuilder.appendDartDouble(shortest: String, value: Double) {
        if (value == 0.0) {
            append(if (value.toRawBits() < 0) "-0.0" else "0.0")
            return
        }
        val decimal = BigDecimal(shortest).stripTrailingZeros()
        val absolute = decimal.abs()
        if (absolute >= BigDecimal("0.000001") && absolute < BigDecimal("1e21")) {
            val plain = decimal.toPlainString()
            append(plain)
            if (decimal.scale() <= 0) append(".0")
            return
        }
        append(decimal.toString().lowercase())
    }
}
