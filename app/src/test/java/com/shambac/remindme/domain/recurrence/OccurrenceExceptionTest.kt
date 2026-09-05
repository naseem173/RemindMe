package com.shambac.remindme.domain.recurrence

import com.shambac.remindme.domain.model.ExceptionType
import com.shambac.remindme.domain.model.OccurrenceException
import com.shambac.remindme.domain.model.ReminderSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OccurrenceExceptionTest {
    private val calculator = Ical4jRecurrenceCalculator()
    private val series = ReminderSeries("series", "Reminder", startDate = LocalDate.of(2026, 1, 1), startTime = LocalTime.of(9, 0), recurrenceRule = "FREQ=DAILY")
    @Test fun skipExceptionRemovesOccurrence() {
        val occurrence = LocalDateTime.of(2026, 1, 2, 9, 0)
        val next = calculator.nextOccurrence(series, Instant.parse("2026-01-01T10:00:00Z"), listOf(OccurrenceException(seriesId = series.id, originalOccurrenceLocalDateTime = occurrence, type = ExceptionType.SKIP)))
        assertEquals(LocalDate.of(2026, 1, 3), next!!.localDateTime.toLocalDate())
    }
    @Test fun overrideExceptionChangesVisibleTextAndAlarmSettings() {
        val occurrence = LocalDateTime.of(2026, 1, 2, 9, 0)
        val next = calculator.nextOccurrence(
            series,
            Instant.parse("2026-01-01T10:00:00Z"),
            listOf(
                OccurrenceException(
                    seriesId = series.id,
                    originalOccurrenceLocalDateTime = occurrence,
                    type = ExceptionType.OVERRIDE,
                    overrideTitle = "Changed",
                    overrideVibrate = false,
                    overrideSnoozeMinutes = 5,
                ),
            ),
        )
        assertEquals("Changed", next!!.title)
        assertEquals(false, next.vibrate)
        assertEquals(5, next.snoozeMinutes)
    }
}
