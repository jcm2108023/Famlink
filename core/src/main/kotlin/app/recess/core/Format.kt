package app.recess.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Formatting and calendar helpers, ported from the web app's `format.ts`. UI copy is English. */
object Format {
    private val locale = Locale.US
    private val clock = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val longDay = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dueOther = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", locale)
    private val headerDay = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    private val dayKey = DateTimeFormatter.ofPattern("yyyy-MM-dd", locale)

    fun minutesLabel(min: Number): String {
        val n = maxOf(0, min.toDouble().roundToInt())
        if (n == 0) return "0 min"
        val h = n / 60
        val m = n % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    fun greetingFor(now: ZonedDateTime): String = when (now.hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    fun at(ms: EpochMs, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    fun isToday(ms: EpochMs, now: ZonedDateTime): Boolean =
        at(ms, now.zone).toLocalDate() == now.toLocalDate()

    fun dueLabel(ms: EpochMs?, now: ZonedDateTime): String {
        if (ms == null) return "No due date"
        val d = at(ms, now.zone)
        val time = clock.format(d)
        val days = d.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        return when (days) {
            0L -> if (ms < now.toInstant().toEpochMilli()) "Overdue · $time" else "Today · $time"
            1L -> "Tomorrow · $time"
            -1L -> "Yesterday · $time"
            else -> dueOther.format(d)
        }
    }

    fun clockLabel(ms: EpochMs, zone: ZoneId): String = clock.format(at(ms, zone))

    fun dayLabel(ms: EpochMs, now: ZonedDateTime): String =
        if (isToday(ms, now)) "Today" else longDay.format(at(ms, now.zone))

    fun headerDate(now: ZonedDateTime): String = headerDay.format(now)

    fun kindLabel(kind: TaskKind): String = when (kind) {
        TaskKind.Quiz -> "Quiz"
        TaskKind.Question -> "Question"
        TaskKind.ShortAnswer -> "Short answer"
        TaskKind.Assignment -> "Assignment"
    }

    fun todayKey(now: ZonedDateTime): String = dayKey.format(now)

    /** Parses "HH:mm"; malformed parts fall back to 0 like the original. */
    fun parseHhmm(hhmm: String): LocalTime {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return LocalTime.of(h, m)
    }

    fun hhmm(time: LocalTime): String = "%02d:%02d".format(locale, time.hour, time.minute)

    fun isAfterBedtime(bedtime: String, now: ZonedDateTime): Boolean =
        !now.toLocalTime().isBefore(parseHhmm(bedtime))

    fun inDowntime(start: String, end: String, now: ZonedDateTime): Boolean {
        val n = now.hour * 60 + now.minute
        val s = parseHhmm(start).let { it.hour * 60 + it.minute }
        val e = parseHhmm(end).let { it.hour * 60 + it.minute }
        if (s == e) return false
        return if (s < e) n in s until e else n >= s || n < e
    }

    fun clockFromHhmm(hhmm: String): String = clock.format(parseHhmm(hhmm))

    /** Single-letter weekday for slot [index] of a [length]-day window ending today. */
    fun weekdayShort(index: Int, today: LocalDate, length: Int = 7): String =
        today.minusDays((length - 1 - index).toLong()).dayOfWeek
            .getDisplayName(java.time.format.TextStyle.NARROW, locale)

    fun categoryLabel(category: AppCategory): String = when (category) {
        AppCategory.Learning -> "Learning"
        AppCategory.Social -> "Social"
        AppCategory.Games -> "Games"
        AppCategory.Video -> "Video"
        AppCategory.Other -> "Other"
    }
}

fun remainingMinutes(child: Child): Int =
    maxOf(0, child.dailyLimitMin + child.bonusTodayMin - child.usedTodayMin)
