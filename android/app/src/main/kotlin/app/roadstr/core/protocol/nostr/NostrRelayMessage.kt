package app.roadstr.core.protocol.nostr

import java.util.Collections

enum class NostrRelayDecodeFailure(val wireName: String) {
    NON_STRING("nonString"),
    TOO_LONG("tooLong"),
    TOO_DEEP("tooDeep"),
    MALFORMED_JSON("malformedJson"),
    INVALID_ENVELOPE("invalidEnvelope"),
    UNSUPPORTED_TYPE("unsupportedType"),
}

class NostrRelayDecodeResult private constructor(
    val message: NostrRelayMessage?,
    val failure: NostrRelayDecodeFailure?,
) {
    val isAccepted: Boolean
        get() = message != null

    init {
        require((message == null) != (failure == null)) {
            "A relay decode result must contain exactly one outcome"
        }
    }

    companion object {
        fun accepted(message: NostrRelayMessage): NostrRelayDecodeResult =
            NostrRelayDecodeResult(message, null)

        fun rejected(failure: NostrRelayDecodeFailure): NostrRelayDecodeResult =
            NostrRelayDecodeResult(null, failure)
    }
}

sealed interface NostrRelayMessage

class NostrRelayEventMessage(
    val subscriptionId: String,
    event: Map<String, Any?>,
) : NostrRelayMessage {
    val event: Map<String, Any?> = immutableRelayJsonObject(event)
}

data class NostrRelayEoseMessage(
    val subscriptionId: String,
) : NostrRelayMessage

data class NostrRelayOkMessage(
    val eventId: String,
    /** Null means the relay supplied a malformed non-boolean status. */
    val accepted: Boolean?,
    val reason: Any?,
) : NostrRelayMessage

data class NostrRelayNoticeMessage(
    val detail: Any?,
) : NostrRelayMessage

data class NostrRelayClosedMessage(
    val subscriptionId: String,
    val detail: Any?,
) : NostrRelayMessage

data class NostrRelayAuthMessage(
    val challenge: String,
) : NostrRelayMessage

/**
 * Bounded structural decoder for relay-to-client NIP-01/NIP-42 frames.
 *
 * Signature verification and subscription/kind authorization deliberately
 * remain caller responsibilities. This boundary only decides whether an
 * untrusted frame is small, shallow and structurally usable.
 */
object NostrRelayMessageDecoder {
    const val MAX_FRAME_UTF16_CODE_UNITS: Int = 256 * 1024
    const val MAX_JSON_NESTING_DEPTH: Int = 64

    fun decode(raw: Any?): NostrRelayDecodeResult {
        if (raw !is String) {
            return NostrRelayDecodeResult.rejected(NostrRelayDecodeFailure.NON_STRING)
        }
        if (raw.length > MAX_FRAME_UTF16_CODE_UNITS) {
            return NostrRelayDecodeResult.rejected(NostrRelayDecodeFailure.TOO_LONG)
        }
        if (exceedsNestingLimit(raw)) {
            return NostrRelayDecodeResult.rejected(NostrRelayDecodeFailure.TOO_DEEP)
        }

        val decoded = try {
            BoundedJsonParser(raw).parse()
        } catch (_: JsonParseException) {
            return NostrRelayDecodeResult.rejected(NostrRelayDecodeFailure.MALFORMED_JSON)
        }
        if (decoded !is List<*> || decoded.isEmpty() || decoded[0] !is String) {
            return NostrRelayDecodeResult.rejected(
                NostrRelayDecodeFailure.INVALID_ENVELOPE,
            )
        }

        return when (decoded[0] as String) {
            "EVENT" -> decodeEvent(decoded)
            "EOSE" -> decodeEose(decoded)
            "OK" -> decodeOk(decoded)
            "NOTICE" -> NostrRelayDecodeResult.accepted(
                NostrRelayNoticeMessage(decoded.getOrElse(1) { "" }),
            )

            "CLOSED" -> decodeClosed(decoded)
            "AUTH" -> decodeAuth(decoded)
            else -> NostrRelayDecodeResult.rejected(
                NostrRelayDecodeFailure.UNSUPPORTED_TYPE,
            )
        }
    }

    private fun decodeEvent(values: List<*>): NostrRelayDecodeResult {
        if (values.size < 3 || values[1] !is String || values[2] !is Map<*, *>) {
            return invalidEnvelope()
        }
        val rawEvent = values[2] as Map<*, *>
        if (rawEvent.keys.any { key -> key !is String }) return invalidEnvelope()
        val event = linkedMapOf<String, Any?>()
        for ((key, value) in rawEvent) event[key as String] = value
        return NostrRelayDecodeResult.accepted(
            NostrRelayEventMessage(values[1] as String, event),
        )
    }

    private fun decodeEose(values: List<*>): NostrRelayDecodeResult {
        if (values.size < 2 || values[1] !is String) return invalidEnvelope()
        return NostrRelayDecodeResult.accepted(
            NostrRelayEoseMessage(values[1] as String),
        )
    }

    private fun decodeOk(values: List<*>): NostrRelayDecodeResult {
        if (values.size < 3 || values[1] !is String) return invalidEnvelope()
        return NostrRelayDecodeResult.accepted(
            NostrRelayOkMessage(
                eventId = values[1] as String,
                accepted = values[2] as? Boolean,
                reason = values.getOrNull(3),
            ),
        )
    }

    private fun decodeClosed(values: List<*>): NostrRelayDecodeResult {
        if (values.size < 3 || values[1] !is String) return invalidEnvelope()
        return NostrRelayDecodeResult.accepted(
            NostrRelayClosedMessage(values[1] as String, values[2]),
        )
    }

    private fun decodeAuth(values: List<*>): NostrRelayDecodeResult {
        if (values.size < 2 || values[1] !is String) return invalidEnvelope()
        return NostrRelayDecodeResult.accepted(
            NostrRelayAuthMessage(values[1] as String),
        )
    }

    private fun invalidEnvelope(): NostrRelayDecodeResult =
        NostrRelayDecodeResult.rejected(NostrRelayDecodeFailure.INVALID_ENVELOPE)

    /** Mirrors Dart's pre-decode scan and ignores brackets inside strings. */
    private fun exceedsNestingLimit(input: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (character in input) {
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '[', '{' -> {
                    depth++
                    if (depth > MAX_JSON_NESTING_DEPTH) return true
                }

                ']', '}' -> if (depth > 0) depth--
            }
        }
        return false
    }
}

private class JsonParseException : RuntimeException()

/** Small JSON parser kept internal so the deterministic core adds no runtime dependency. */
internal class BoundedJsonParser(
    private val input: String,
) {
    private var position = 0

    fun parse(): Any? {
        skipWhitespace()
        val value = parseValue(depth = 0)
        skipWhitespace()
        if (position != input.length) fail()
        return value
    }

    private fun parseValue(depth: Int): Any? {
        if (position >= input.length) fail()
        return when (input[position]) {
            'n' -> parseLiteral("null", null)
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            '"' -> parseString()
            '[' -> parseArray(depth + 1)
            '{' -> parseObject(depth + 1)
            '-', in '0'..'9' -> parseNumber()
            else -> fail()
        }
    }

    private fun parseArray(depth: Int): List<Any?> {
        if (depth > NostrRelayMessageDecoder.MAX_JSON_NESTING_DEPTH) fail()
        position++
        skipWhitespace()
        val values = mutableListOf<Any?>()
        if (consumeIf(']')) return Collections.unmodifiableList(values)
        while (true) {
            skipWhitespace()
            values += parseValue(depth)
            skipWhitespace()
            when {
                consumeIf(']') -> return Collections.unmodifiableList(values)
                consumeIf(',') -> Unit
                else -> fail()
            }
        }
    }

    private fun parseObject(depth: Int): Map<String, Any?> {
        if (depth > NostrRelayMessageDecoder.MAX_JSON_NESTING_DEPTH) fail()
        position++
        skipWhitespace()
        val values = linkedMapOf<String, Any?>()
        if (consumeIf('}')) return Collections.unmodifiableMap(values)
        while (true) {
            skipWhitespace()
            if (position >= input.length || input[position] != '"') fail()
            val key = parseString()
            skipWhitespace()
            if (!consumeIf(':')) fail()
            skipWhitespace()
            values[key] = parseValue(depth)
            skipWhitespace()
            when {
                consumeIf('}') -> return Collections.unmodifiableMap(values)
                consumeIf(',') -> Unit
                else -> fail()
            }
        }
    }

    private fun parseString(): String {
        if (!consumeIf('"')) fail()
        val value = StringBuilder()
        while (position < input.length) {
            val character = input[position++]
            when {
                character == '"' -> return value.toString()
                character == '\\' -> value.append(parseEscape())
                character.code < 0x20 -> fail()
                else -> value.append(character)
            }
        }
        fail()
    }

    private fun parseEscape(): Char {
        if (position >= input.length) fail()
        return when (val escaped = input[position++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> fail()
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (position + 4 > input.length) fail()
        var value = 0
        repeat(4) {
            val digit = input[position++].digitToIntOrNull(16) ?: fail()
            value = value * 16 + digit
        }
        return value.toChar()
    }

    private fun parseNumber(): Number {
        val start = position
        consumeIf('-')
        if (position >= input.length) fail()
        if (input[position] == '0') {
            position++
        } else {
            if (input[position] !in '1'..'9') fail()
            while (position < input.length && input[position] in '0'..'9') position++
        }

        var integral = true
        if (consumeIf('.')) {
            integral = false
            val fractionStart = position
            while (position < input.length && input[position] in '0'..'9') position++
            if (position == fractionStart) fail()
        }
        if (position < input.length && (input[position] == 'e' || input[position] == 'E')) {
            integral = false
            position++
            if (position < input.length && (input[position] == '+' || input[position] == '-')) {
                position++
            }
            val exponentStart = position
            while (position < input.length && input[position] in '0'..'9') position++
            if (position == exponentStart) fail()
        }

        val token = input.substring(start, position)
        return if (integral) {
            token.toLongOrNull() ?: token.toDoubleOrNull()?.takeIf(Double::isFinite) ?: fail()
        } else {
            token.toDoubleOrNull()?.takeIf(Double::isFinite) ?: fail()
        }
    }

    private fun <T> parseLiteral(literal: String, value: T): T {
        if (!input.startsWith(literal, position)) fail()
        position += literal.length
        return value
    }

    private fun consumeIf(character: Char): Boolean {
        if (position >= input.length || input[position] != character) return false
        position++
        return true
    }

    private fun skipWhitespace() {
        while (position < input.length && input[position] in " \t\r\n") position++
    }

    private fun fail(): Nothing = throw JsonParseException()
}

private fun immutableRelayJsonObject(source: Map<String, Any?>): Map<String, Any?> {
    val copy = linkedMapOf<String, Any?>()
    for ((key, value) in source) copy[key] = immutableRelayJsonValue(value)
    return Collections.unmodifiableMap(copy)
}

private fun immutableRelayJsonValue(value: Any?): Any? = when (value) {
    is Map<*, *> -> {
        val copy = linkedMapOf<String, Any?>()
        for ((key, nested) in value) {
            require(key is String) { "JSON object keys must be strings" }
            copy[key] = immutableRelayJsonValue(nested)
        }
        Collections.unmodifiableMap(copy)
    }

    is List<*> -> Collections.unmodifiableList(value.map(::immutableRelayJsonValue))
    else -> value
}
