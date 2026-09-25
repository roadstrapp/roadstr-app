package app.roadstr.core.format

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Pure formatting counterpart of lib/utils/units.dart.
 *
 * The caller supplies the persisted unit preference. Keeping storage outside
 * this class makes the parity logic deterministic and usable from tests,
 * services and Compose without opening the legacy Hive box.
 */
class UnitFormatter(private val imperial: Boolean) {
    fun formatDistance(metres: Double, nowLabel: String = ""): String {
        if (metres < 50) {
            return if (nowLabel.isNotEmpty()) nowLabel else if (imperial) "0 ft" else "0 m"
        }
        if (imperial) {
            val feet = metres / METRES_PER_FOOT
            if (feet < 500) return "${dartRound(feet / 10) * 10} ft"
            val miles = metres / METRES_PER_MILE
            return if (miles < 10) "${fixedOne(miles)} mi" else "${dartRound(miles)} mi"
        }
        return if (metres < 1000) {
            "${dartRound(metres)} m"
        } else {
            "${fixedOne(metres / 1000)} km"
        }
    }

    fun toDisplaySpeed(kilometresPerHour: Double): Double =
        if (imperial) kilometresPerHour / 1.60934 else kilometresPerHour

    fun formatAltitude(metres: Double): String =
        if (imperial) "${dartRound(metres / METRES_PER_FOOT)} ft"
        else "${dartRound(metres)} m"

    val speedUnit: String get() = if (imperial) "mph" else "km/h"

    fun speedUnitForSpeech(language: String): String = when (language) {
        "it" -> if (imperial) "miglia orarie" else "chilometri orari"
        "en" -> if (imperial) "miles per hour" else "kilometres per hour"
        "de" -> if (imperial) "Meilen pro Stunde" else "Kilometer pro Stunde"
        "es" -> if (imperial) "millas por hora" else "kilómetros por hora"
        "fr" -> if (imperial) "miles par heure" else "kilomètres par heure"
        "pt" -> if (imperial) "milhas por hora" else "quilómetros por hora"
        "nl" -> if (imperial) "mijl per uur" else "kilometer per uur"
        "da" -> if (imperial) "mil i timen" else "kilometer i timen"
        "sv" -> if (imperial) "miles per hour" else "kilometer i timmen"
        "fi" -> if (imperial) "mailia tunnissa" else "kilometriä tunnissa"
        "pl" -> if (imperial) "mil na godzinę" else "kilometrów na godzinę"
        "cs" -> if (imperial) "mil za hodinu" else "kilometrů za hodinu"
        "sk" -> if (imperial) "míľ za hodinu" else "kilometrov za hodinu"
        "sl" -> if (imperial) "milj na uro" else "kilometrov na uro"
        "hr" -> if (imperial) "milja na sat" else "kilometara na sat"
        "hu" -> if (imperial) "mérföld per óra" else "kilométer per óra"
        "ro" -> if (imperial) "mile pe oră" else "kilometri pe oră"
        "bg" -> if (imperial) "мили в час" else "километра в час"
        "ru" -> if (imperial) "миль в час" else "километров в час"
        "uk" -> if (imperial) "миль на годину" else "кілометрів на годину"
        "el" -> if (imperial) "μίλια ανά ώρα" else "χιλιόμετρα ανά ώρα"
        "et" -> if (imperial) "miili tunnis" else "kilomeetrit tunnis"
        "lt" -> if (imperial) "mylių per valandą" else "kilometrų per valandą"
        "lv" -> if (imperial) "jūdzes stundā" else "kilometri stundā"
        "ga" -> if (imperial) "míle san uair" else "ciliméadar san uair"
        "mt" -> if (imperial) "mili fis-siegħa" else "kilometri fis-siegħa"
        "ja" -> if (imperial) "マイル毎時" else "キロ毎時"
        "zh" -> if (imperial) "英里每小时" else "公里每小时"
        else -> if (imperial) "miles per hour" else "kilometres per hour"
    }

    fun ttsDistancePrefix(metres: Int, language: String): String {
        if (metres <= 0) return ""
        if (!imperial) {
            if (metres >= 1000) {
                return inKilometres(decimal(metres / 1000.0, language), language)
            }
            return inMetres(metres, language)
        }
        val feet = dartRound(metres / METRES_PER_FOOT)
        if (feet < 500) return inFeet(dartRound(feet / 10.0) * 10, language)
        val miles = metres / METRES_PER_MILE
        val value = if (miles < 10) fixedOne(miles) else dartRound(miles).toString()
        return inMiles(value, language)
    }

    fun ttsDistanceInline(metres: Int, language: String): String {
        val prefix = ttsDistancePrefix(metres, language).trimEnd()
        if (prefix.isEmpty()) return ""
        val withoutSeparator = if (prefix.last() in TRAILING_SEPARATORS) prefix.dropLast(1) else prefix
        return withoutSeparator.replaceFirstChar { it.lowercase(Locale.ROOT) }
    }

    fun usesWordSpacing(language: String): Boolean = language != "ja" && language != "zh"

    fun joinDistance(distance: String, instruction: String, language: String): String {
        if (distance.isEmpty()) return instruction
        return if (usesWordSpacing(language)) "$distance $instruction" else "$distance$instruction"
    }

    private fun decimal(value: Double, language: String): String {
        val text = if (value < 10) fixedOne(value) else dartRound(value).toString()
        return if (language in COMMA_DECIMAL_LANGUAGES) text.replace('.', ',') else text
    }

    private fun inMetres(value: Int, language: String): String = when (language) {
        "it" -> "Tra $value metri, "
        "es" -> "En $value metros, "
        "fr" -> "Dans $value mètres, "
        "ja" -> "${value}メートル先で、"
        "zh" -> "在${value}米后，"
        "pt" -> "Em $value metros, "
        else -> "In $value meters, "
    }

    private fun inFeet(value: Int, language: String): String = when (language) {
        "it" -> "Tra $value piedi, "
        "es" -> "En $value pies, "
        "fr" -> "Dans $value pieds, "
        "ja" -> "${value}フィート先で、"
        "zh" -> "在${value}英尺后，"
        "pt" -> "Em $value pés, "
        else -> "In $value feet, "
    }

    private fun inKilometres(value: String, language: String): String = when (language) {
        "it" -> "Tra $value chilometri, "
        "es" -> "En $value kilómetros, "
        "fr" -> "Dans $value kilomètres, "
        "ja" -> "${value}キロ先で、"
        "zh" -> "在${value}公里后，"
        "pt" -> "Em $value quilómetros, "
        else -> "In $value kilometres, "
    }

    private fun inMiles(value: String, language: String): String = when (language) {
        "it" -> "Tra $value miglia, "
        "es" -> "En $value millas, "
        "fr" -> "Dans $value miles, "
        "ja" -> "${value}マイル先で、"
        "zh" -> "在${value}英里后，"
        "pt" -> "Em $value milhas, "
        else -> "In $value miles, "
    }

    private fun fixedOne(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    /** Dart rounds exact halves away from zero; Kotlin's roundToInt does not. */
    private fun dartRound(value: Double): Int {
        require(value.isFinite()) { "Cannot round a non-finite value" }
        return if (value >= 0) floor(value + 0.5).toInt() else ceil(value - 0.5).toInt()
    }

    private companion object {
        const val METRES_PER_FOOT = 0.3048
        const val METRES_PER_MILE = 1609.344
        val TRAILING_SEPARATORS = setOf(',', '、', '，')
        val COMMA_DECIMAL_LANGUAGES = setOf(
            "it", "es", "fr", "pt", "de", "nl", "pl", "ru", "cs", "sk", "hu",
            "ro", "bg", "hr", "lt", "lv", "et", "sl", "da", "fi", "sv", "el", "mt",
        )
    }
}
