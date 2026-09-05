package com.shambac.remindme.domain.recurrence

import com.shambac.remindme.domain.model.ReminderSeries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class Ical4jAndroidCompatibilityInstrumentedTest {
    @Test fun recurrenceLibraryCalculatesOnAndroidRuntime() {
        val series = ReminderSeries(
            id = "android-compat",
            title = "Compatibility",
            startDate = LocalDate.of(2025, 1, 1),
            startTime = LocalTime.of(9, 0),
            recurrenceRule = "FREQ=DAILY;COUNT=2",
            timezoneMode = com.shambac.remindme.domain.model.TimezoneMode.FIXED_ZONE,
            zoneId = ZoneId.of("UTC").id,
        )
        val next = Ical4jRecurrenceCalculator().nextOccurrence(series, Instant.parse("2025-01-01T10:00:00Z"))
        assertEquals(LocalDate.of(2025, 1, 2), next?.localDateTime?.toLocalDate())
    }
}
