package com.shambac.remindme.domain.recurrence

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** JVM smoke coverage for iCal4j API used by Android release/R8 builds. */
class Ical4jAndroidCompatibilityTest {
    @Test fun recurrenceEngineLoadsAndCalculates() {
        val dates = net.fortuna.ical4j.model.Recur<LocalDateTime>("FREQ=DAILY").getDates(
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 3, 9, 0),
            3,
        )
        assertTrue(dates.isNotEmpty())
    }
}
