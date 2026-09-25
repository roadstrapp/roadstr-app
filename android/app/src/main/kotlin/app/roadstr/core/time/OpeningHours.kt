package app.roadstr.core.time

import java.time.LocalDateTime

enum class OpenState { OPEN, CLOSED, UNKNOWN }

data class OpeningStatus(val state: OpenState, val nextChange: LocalDateTime?) {
    companion object {
        val Unknown = OpeningStatus(OpenState.UNKNOWN, null)
    }
}

/**
 * Conservative parser for the common OpenStreetMap opening_hours subset.
 * Unsupported constructs return UNKNOWN, matching the Flutter safety policy.
 */
object OpeningHours {
    private val days = mapOf(
        "mo" to 0,
        "tu" to 1,
        "we" to 2,
        "th" to 3,
        "fr" to 4,
        "sa" to 5,
        "su" to 6,
    )

    private const val DAY_TOKEN = "(?:mo|tu|we|th|fr|sa|su|ph|sh)"
    private const val WEEKDAY_TOKEN = "(?:mo|tu|we|th|fr|sa|su)"
    private const val DAY_PART = "$DAY_TOKEN(?:\\s*-\\s*$WEEKDAY_TOKEN)?"
    private const val DAY_LIST = "$DAY_PART(?:\\s*,\\s*$DAY_PART)*"
    private const val TIME = "\\d{1,2}:\\d{2}"
    private const val TIME_SPAN = "$TIME\\s*-\\s*$TIME"
    private const val TIME_LIST = "$TIME_SPAN(?:\\s*,\\s*$TIME_SPAN)*"
    private val group = Regex(
        "($DAY_LIST)?\\s*(off|closed|$TIME_LIST)",
        RegexOption.IGNORE_CASE,
    )
    private val tooComplex = Regex(
        "\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|week|sunrise|sunset|dawn|dusk|easter)\\b|\"|\\[",
        RegexOption.IGNORE_CASE,
    )
    private val holiday = Regex("\\b(?:PH|SH)\\b", RegexOption.IGNORE_CASE)
    private val separators = Regex("[\\s;,]+")
    private val clock = Regex("^(\\d{1,2}):(\\d{2})$")

    fun evaluate(raw: String, now: LocalDateTime): OpeningStatus {
        val input = raw.trim()
        if (input.isEmpty()) return OpeningStatus.Unknown
        if (input == "24/7" || input == "00:00-24:00" || input == "Mo-Su 00:00-24:00") {
            return OpeningStatus(OpenState.OPEN, null)
        }
        if (tooComplex.containsMatchIn(input) || holiday.containsMatchIn(input)) {
            return OpeningStatus.Unknown
        }

        val matches = group.findAll(input).toList()
        val unsupported = separators.replace(group.replace(input, ""), "")
        if (matches.isEmpty() || unsupported.isNotEmpty()) return OpeningStatus.Unknown

        val week = MutableList(7) { mutableListOf<Interval>() }
        var matched = false
        var invalid = false
        for (match in matches) {
            matched = true
            val daysSpec = match.groups[1]?.value
            val timesSpec = match.groups[2]!!.value.lowercase()
            val selectedDays = parseDays(daysSpec)
            if (selectedDays.isEmpty()) continue
            if (timesSpec == "off" || timesSpec == "closed") {
                selectedDays.forEach { week[it].clear() }
                continue
            }
            for (rawSpan in timesSpec.split(',')) {
                val span = parseSpan(rawSpan.trim())
                if (span == null) {
                    invalid = true
                    continue
                }
                for (day in selectedDays) {
                    when {
                        span.end > span.start -> week[day].add(span)
                        span.end < span.start -> {
                            week[day].add(Interval(span.start, MINUTES_PER_DAY))
                            week[(day + 1) % 7].add(Interval(0, span.end))
                        }
                    }
                }
            }
        }
        if (!matched || invalid) return OpeningStatus.Unknown

        for (day in week.indices) {
            val sorted = week[day].sortedBy(Interval::start)
            val merged = mutableListOf<Interval>()
            for (interval in sorted) {
                val previous = merged.lastOrNull()
                if (previous == null || interval.start > previous.end) {
                    merged += interval
                } else if (interval.end > previous.end) {
                    merged[merged.lastIndex] = previous.copy(end = interval.end)
                }
            }
            week[day] = merged
        }

        val nowMinute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value - 1
        for (interval in week[today]) {
            if (nowMinute >= interval.start && nowMinute < interval.end) {
                val midnight = now.toLocalDate().atStartOfDay()
                return OpeningStatus(OpenState.OPEN, midnight.plusMinutes(interval.end.toLong()))
            }
        }

        val midnight = now.toLocalDate().atStartOfDay()
        for (offset in 0..7) {
            val day = (today + offset) % 7
            for (start in week[day].map(Interval::start).sorted()) {
                if (offset == 0 && start <= nowMinute) continue
                return OpeningStatus(
                    OpenState.CLOSED,
                    midnight.plusDays(offset.toLong()).plusMinutes(start.toLong()),
                )
            }
        }
        return OpeningStatus(OpenState.CLOSED, null)
    }

    private data class Interval(val start: Int, val end: Int)

    private fun parseDays(spec: String?): Set<Int> {
        if (spec.isNullOrBlank()) return (0..6).toSet()
        val result = linkedSetOf<Int>()
        for (rawPart in spec.lowercase().split(',')) {
            val part = rawPart.trim()
            if ('-' in part) {
                val ends = part.split('-')
                if (ends.size != 2) continue
                val first = days[ends[0].trim()] ?: continue
                val last = days[ends[1].trim()] ?: continue
                var current = first
                while (true) {
                    result += current
                    if (current == last) break
                    current = (current + 1) % 7
                }
            } else {
                days[part]?.let(result::add)
            }
        }
        return result
    }

    private fun parseSpan(raw: String): Interval? {
        val parts = raw.split('-')
        if (parts.size != 2) return null
        val start = parseTime(parts[0].trim(), allow24 = false) ?: return null
        val end = parseTime(parts[1].trim(), allow24 = true) ?: return null
        if (start == end) return null
        return Interval(start, end)
    }

    private fun parseTime(raw: String, allow24: Boolean): Int? {
        val match = clock.matchEntire(raw) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 24 || minute > 59 || hour == 24 && (!allow24 || minute != 0)) return null
        return hour * 60 + minute
    }

    private const val MINUTES_PER_DAY = 1440
}
