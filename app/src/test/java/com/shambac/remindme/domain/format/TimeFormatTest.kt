package com.shambac.remindme.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class TimeFormatTest {
    @Test fun formats24Hour() {
        assertEquals("09:05", formatTime(LocalTime.of(9, 5), is24Hour = true, locale = Locale.US))
        assertEquals("23:45", formatTime(LocalTime.of(23, 45), is24Hour = true, locale = Locale.US))
        assertEquals("00:00", formatTime(LocalTime.of(0, 0), is24Hour = true, locale = Locale.US))
    }

    @Test fun formats12Hour() {
        assertEquals("9:05 AM", formatTime(LocalTime.of(9, 5), is24Hour = false, locale = Locale.US))
        assertEquals("11:45 PM", formatTime(LocalTime.of(23, 45), is24Hour = false, locale = Locale.US))
        assertEquals("12:00 AM", formatTime(LocalTime.of(0, 0), is24Hour = false, locale = Locale.US))
        assertEquals("12:00 PM", formatTime(LocalTime.of(12, 0), is24Hour = false, locale = Locale.US))
    }

    @Test fun defaultsToSystemLocaleWhenNoneGiven() {
        val defaultLocale = Locale.getDefault()
        assertEquals(formatTime(LocalTime.of(14, 30), is24Hour = true, locale = defaultLocale), formatTime(LocalTime.of(14, 30), is24Hour = true))
    }
}
