package com.shambac.remindme.domain.recurrence

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RecurrenceRulesTest {
    @Test fun selectedWeekdaysAreEncodedAsRfc5545ByDay() {
        val rule = RecurrenceRules.weekly(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertTrue(rule.contains("FREQ=WEEKLY"))
        assertTrue(rule.contains("BYDAY=MO,FR"))
    }
    @Test fun monthlyRulesSupportFixedDayAndOrdinalWeekday() {
        assertTrue(RecurrenceRules.monthlyOnDay(15).contains("BYMONTHDAY=15"))
        assertTrue(RecurrenceRules.monthlyOnWeekday(2, DayOfWeek.TUESDAY).contains("BYDAY=2TU"))
        assertTrue(RecurrenceRules.monthlyOnWeekday(-1, DayOfWeek.FRIDAY).contains("BYDAY=-1FR"))
    }
}
