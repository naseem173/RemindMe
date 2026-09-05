package com.shambac.remindme.domain.recurrence

import com.shambac.remindme.domain.model.OccurrenceException
import com.shambac.remindme.domain.model.ReminderOccurrence
import com.shambac.remindme.domain.model.ReminderSeries
import com.shambac.remindme.domain.model.zone
import net.fortuna.ical4j.model.Recur
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

interface RecurrenceCalculator {
    fun nextOccurrence(
        series: ReminderSeries,
        after: Instant,
        exceptions: List<OccurrenceException> = emptyList(),
    ): ReminderOccurrence?

    fun occurrencesBetween(
        series: ReminderSeries,
        from: Instant,
        to: Instant,
        exceptions: List<OccurrenceException> = emptyList(),
    ): List<ReminderOccurrence>
}

/** RFC 5545 recurrence is delegated to iCal4j. Local time remains source of truth. */
class Ical4jRecurrenceCalculator : RecurrenceCalculator {
    override fun nextOccurrence(
        series: ReminderSeries,
        after: Instant,
        exceptions: List<OccurrenceException>,
    ): ReminderOccurrence? = occurrencesBetween(series, after, after.plus(Duration.ofDays(366 * 4L)), exceptions).firstOrNull()

    override fun occurrencesBetween(
        series: ReminderSeries,
        from: Instant,
        to: Instant,
        exceptions: List<OccurrenceException>,
    ): List<ReminderOccurrence> {
        if (!series.enabled) return emptyList()
        val zone = series.zone()
        val time = series.startTime ?: LocalDateTime.MIN.toLocalTime().plusHours(9)
        val seed = LocalDateTime.of(series.startDate, time)
        val fromLocal = from.atZone(zone).toLocalDateTime()
        val toLocal = to.atZone(zone).toLocalDateTime()
        val candidates = if (series.recurrenceRule.isNullOrBlank()) {
            listOf(seed).filter { it > fromLocal && it <= toLocal }
        } else {
            val recur = Recur<LocalDateTime>(series.recurrenceRule)
            recur.getDates(seed, fromLocal, toLocal, 10_000).filter { it > fromLocal }
        }
        return candidates.asSequence()
            .mapNotNull { local -> applyException(series, local, exceptions, zone) }
            .filter { it.instant > from && it.instant <= to }
            .distinctBy { it.localDateTime }
            .sortedBy { it.instant }
            .toList()
    }

    private fun applyException(
        series: ReminderSeries,
        local: LocalDateTime,
        exceptions: List<OccurrenceException>,
        zone: ZoneId,
    ): ReminderOccurrence? {
        val exception = exceptions.firstOrNull {
            it.originalOccurrenceLocalDateTime == local
        }
        if (exception?.type == com.shambac.remindme.domain.model.ExceptionType.SKIP) return null
        val effectiveLocal = if (exception?.type == com.shambac.remindme.domain.model.ExceptionType.OVERRIDE) {
            LocalDateTime.of(exception.overrideDate ?: local.toLocalDate(), exception.overrideTime ?: local.toLocalTime())
        } else local
        // atZone resolves nonexistent wall times to next valid time and ambiguous times to earlier offset.
        val instant = effectiveLocal.atZone(zone).toInstant()
        return ReminderOccurrence(
            seriesId = series.id,
            localDateTime = effectiveLocal,
            instant = instant,
            title = exception?.overrideTitle ?: series.title,
            description = exception?.overrideDescription ?: series.description,
            alarmSoundUri = exception?.overrideAlarmSoundUri ?: series.alarmSoundUri,
            vibrate = exception?.overrideVibrate ?: series.vibrate,
            speakReminder = exception?.overrideSpeakReminder ?: series.speakReminder,
            snoozeMinutes = exception?.overrideSnoozeMinutes ?: series.snoozeMinutes,
            maxRingMinutes = exception?.overrideMaxRingMinutes ?: series.maxRingMinutes,
            isOverride = exception != null,
        )
    }
}

object RecurrenceRules {
    fun daily(interval: Int = 1, count: Int? = null, until: java.time.LocalDate? = null): String = rule("DAILY", interval, count, until)
    fun weekly(interval: Int = 1, weekdays: Set<java.time.DayOfWeek> = emptySet(), count: Int? = null, until: java.time.LocalDate? = null): String =
        rule("WEEKLY", interval, count, until, weekdays = weekdays)
    fun monthly(interval: Int = 1, count: Int? = null, until: java.time.LocalDate? = null): String = rule("MONTHLY", interval, count, until)
    fun monthlyOnDay(day: Int, interval: Int = 1, count: Int? = null, until: java.time.LocalDate? = null): String =
        rule("MONTHLY", interval, count, until, monthDays = listOf(day))
    fun monthlyOnWeekday(ordinal: Int, day: java.time.DayOfWeek, interval: Int = 1, count: Int? = null, until: java.time.LocalDate? = null): String =
        rule("MONTHLY", interval, count, until, ordinalWeekday = "$ordinal${day.toRrule()}")
    fun yearly(interval: Int = 1, count: Int? = null, until: java.time.LocalDate? = null): String = rule("YEARLY", interval, count, until)

    private fun rule(
        frequency: String,
        interval: Int,
        count: Int?,
        until: java.time.LocalDate?,
        weekdays: Set<java.time.DayOfWeek> = emptySet(),
        monthDays: List<Int> = emptyList(),
        ordinalWeekday: String? = null,
    ): String = buildList {
        add("FREQ=$frequency")
        if (interval > 1) add("INTERVAL=$interval")
        if (weekdays.isNotEmpty()) add("BYDAY=${weekdays.sortedBy { it.value }.joinToString(",") { it.toRrule() }}")
        if (monthDays.isNotEmpty()) add("BYMONTHDAY=${monthDays.joinToString(",")}")
        ordinalWeekday?.let { add("BYDAY=$it") }
        count?.let { add("COUNT=$it") }
        until?.let { add("UNTIL=${it.toString().replace("-", "")}T235959") }
    }.joinToString(";")

    private fun java.time.DayOfWeek.toRrule(): String = when (this) {
        java.time.DayOfWeek.MONDAY -> "MO"
        java.time.DayOfWeek.TUESDAY -> "TU"
        java.time.DayOfWeek.WEDNESDAY -> "WE"
        java.time.DayOfWeek.THURSDAY -> "TH"
        java.time.DayOfWeek.FRIDAY -> "FR"
        java.time.DayOfWeek.SATURDAY -> "SA"
        java.time.DayOfWeek.SUNDAY -> "SU"
    }
}
