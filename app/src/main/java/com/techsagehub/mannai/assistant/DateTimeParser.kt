package com.techsagehub.mannai.assistant

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Deterministic natural-language date/time interpretation in the device's
 * local timezone. Pure functions — fully unit-testable with an explicit [now].
 */
data class DateTimeMatch(
    val dateTime: LocalDateTime,
    val matchedText: String,
    /** Individual matched fragments (date bit, time bit) for title cleanup. */
    val parts: List<String> = listOf(matchedText)
)

object DateTimeParser {
    private val MONTHS = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "jun" to 6, "jul" to 7, "aug" to 8, "sept" to 9, "sep" to 9,
        "oct" to 10, "nov" to 11, "dec" to 12
    )
    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY,
        "tues" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY
    )
    private val TIME_COLON = Regex("""(\d{1,2}):(\d{2})\s*(am|pm|a\.m\.|p\.m\.)?""")
    private val TIME_HOUR = Regex("""(?<!\d)(\d{1,2})\s*(am|pm|a\.m\.|p\.m\.|o'?clock)""")
    private val IN_DURATION = Regex("""in\s+(\d+)\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days|week|weeks)""")
    private val NUMERIC_DATE = Regex("""(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?""")
    private val MONTH_DATE = Regex("""(\d{1,2})(st|nd|rd|th)?\s+(of\s+)?(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)""")

    fun find(text: String, now: LocalDateTime = LocalDateTime.now()): DateTimeMatch? {
        val lower = text.lowercase()
        // "in X minutes/hours/..." is self-contained.
        IN_DURATION.find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            val dt = when (m.groupValues[2]) {
                "minute", "minutes", "min", "mins" -> now.plusMinutes(n.toLong())
                "hour", "hours", "hr", "hrs" -> now.plusHours(n.toLong())
                "day", "days" -> now.plusDays(n.toLong())
                else -> now.plusDays(7L * n)
            }
            return DateTimeMatch(dt.withSecond(0).withNano(0), m.value)
        }

        var date = resolveDate(lower, now) // Pair(date, matchedText or null)
        val time = resolveTime(lower)

        if (date == null && time == null) return null

        var base = (date?.first ?: now.toLocalDate())
        var t = time?.first ?: defaultTimeFor(lower)
        if (date == null && time != null && !time.first.isAfter(now.toLocalTime())) {
            base = base.plusDays(1) // "5 PM" already past today -> tomorrow
        }
        if (date != null && time == null) {
            t = defaultTimeFor(lower) // date only -> 9:00 unless part of day given
        }
        var dt = LocalDateTime.of(base, t)
        if (dt.isBefore(now) && date == null) dt = dt.plusDays(1)
        val parts = listOfNotNull(date?.second, time?.second)
        return DateTimeMatch(dt, parts.joinToString(" "), parts)
    }

    private fun defaultTimeFor(lower: String): java.time.LocalTime {
        val t = java.time.LocalTime.of(9, 0)
        return when {
            "tonight" in lower -> java.time.LocalTime.of(20, 0)
            "this evening" in lower -> java.time.LocalTime.of(18, 0)
            "this afternoon" in lower -> java.time.LocalTime.of(14, 0)
            "this morning" in lower -> java.time.LocalTime.of(9, 0)
            "morning" in lower -> java.time.LocalTime.of(9, 0)
            "afternoon" in lower -> java.time.LocalTime.of(14, 0)
            "evening" in lower -> java.time.LocalTime.of(18, 0)
            "night" in lower -> java.time.LocalTime.of(20, 0)
            "noon" in lower || "midday" in lower -> java.time.LocalTime.of(12, 0)
            else -> t
        }
    }

    private fun resolveDate(lower: String, now: LocalDateTime): Pair<java.time.LocalDate, String?>? {
        if ("day after tomorrow" in lower) return now.toLocalDate().plusDays(2) to "day after tomorrow"
        if ("tomorrow" in lower) return now.toLocalDate().plusDays(1) to "tomorrow"
        if ("tonight" in lower || "today" in lower || "this morning" in lower ||
            "this afternoon" in lower || "this evening" in lower
        ) {
            val hit = listOf("tonight", "today", "this morning", "this afternoon", "this evening")
                .first { it in lower }
            return now.toLocalDate() to hit
        }
        for ((word, dow) in WEEKDAYS) {
            val wb = Regex("""\b$word\b""").find(lower)
            if (wb != null) {
                val hasNext = lower.contains("next $word")
                val upcoming = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dow))
                // "next <day>" = the occurrence in the following week.
                val d = if (hasNext) upcoming.plusWeeks(1)
                    else if (upcoming == now.toLocalDate()) upcoming else upcoming
                return d to ((if (hasNext) "next " else "") + word)
            }
        }
        MONTH_DATE.find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS[m.groupValues[4]] ?: return@let
            var d = runCatching {
                java.time.LocalDate.of(now.year, Month.of(month), day.coerceIn(1, 28))
            }.getOrNull() ?: return@let
            if (d.isBefore(now.toLocalDate())) d = d.plusYears(1)
            return d to m.value
        }
        NUMERIC_DATE.find(lower)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            // Assume D/M (local convention); fall back if month invalid.
            val (day, month) = if (b in 1..12) a to b else if (a in 1..12) b to a else return@let
            var d = runCatching {
                java.time.LocalDate.of(now.year, month.coerceIn(1, 12), day.coerceIn(1, 28))
            }.getOrNull() ?: return@let
            if (d.isBefore(now.toLocalDate())) d = d.plusYears(1)
            return d to m.value
        }
        return null
    }

    private fun resolveTime(lower: String): Pair<java.time.LocalTime, String>? {
        TIME_COLON.find(lower)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt().coerceIn(0, 59)
            when (m.groupValues[3].lowercase()) {
                "pm", "p.m." -> if (h < 12) h += 12
                "am", "a.m." -> if (h == 12) h = 0
            }
            if (h in 0..23) return java.time.LocalTime.of(h, min) to m.value.trim()
        }
        TIME_HOUR.find(lower)?.let { m ->
            var h = m.groupValues[1].toInt()
            when (m.groupValues[2].lowercase()) {
                "pm", "p.m." -> if (h < 12) h += 12
                "am", "a.m." -> if (h == 12) h = 0
                else -> if (h > 23) return@let
            }
            if (h in 0..23) return java.time.LocalTime.of(h, 0) to m.value.trim()
        }
        return null
    }
}
