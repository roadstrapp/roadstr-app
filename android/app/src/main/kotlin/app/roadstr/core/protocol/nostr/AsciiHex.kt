package app.roadstr.core.protocol.nostr

/**
 * Value of one ASCII hexadecimal digit, or -1.
 *
 * Character.digit and digitToIntOrNull also accept non-ASCII decimal digits
 * (Arabic-Indic, fullwidth and others), so two different strings could decode
 * to the same key, id or signature. Keys and ids are compared as strings
 * elsewhere, which turns that into identity confusion; only ASCII is accepted.
 */
internal fun asciiHexDigit(character: Char): Int = when (character) {
    in '0'..'9' -> character - '0'
    in 'a'..'f' -> character - 'a' + 10
    in 'A'..'F' -> character - 'A' + 10
    else -> -1
}
