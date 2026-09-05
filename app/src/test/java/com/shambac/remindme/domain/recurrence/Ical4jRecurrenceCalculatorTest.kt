package com.shambac.remindme.domain.recurrence

import com.shambac.remindme.domain.model.ReminderSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class Ical4jRecurrenceCalculatorTest {
    private val calculator = Ical4jRecurrenceCalculator()
    private val zone = ZoneId.of("Europe/London")
    private val base = Instant.parse("2025-01-01T08:00:00Z")
    private fun series(rule: String?) = ReminderSeries("id", "Medicine", startDate = LocalDate.of(2025, 1, 1), startTime = LocalTime.of(9, 0), recurrenceRule = rule, timezoneMode = com.shambac.remindme.domain.model.TimezoneMode.FIXED_ZONE, zoneId = zone.id)

    @Test fun dailyUsesStandardsLibrary() {
        val next = calculator.nextOccurrence(series("FREQ=DAILY"), base)
        assertNotNull(next); assertEquals(LocalDate.of(2025, 1, 1), next!!.localDateTime.toLocalDate())
    }

    @Test fun countEndsSeries() {
        val next = calculator.nextOccurrence(series("FREQ=DAILY;COUNT=1"), Instant.parse("2025-01-02T00:00:00Z"))
        assertNull(next)
    }

    @Test fun dstSpringForwardMovesToValidWallTime() {
        val next = calculator.nextOccurrence(series("FREQ=DAILY"), Instant.parse("2025-03-29T09:01:00Z"))
        assertEquals(LocalDate.of(2025, 3, 30), next!!.localDateTime.toLocalDate())
    }
}
