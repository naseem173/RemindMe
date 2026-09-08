package com.shambac.remindme.domain.format

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats [time] as a 24-hour ("HH:mm") or 12-hour ("h:mm a") string depending on
 * [is24Hour]. Callers should derive [is24Hour] from the platform's actual preference
 * (`android.text.format.DateFormat.is24HourFormat(context)`) rather than hardcoding it,
 * so displayed and picked times respect the user's system setting.
 */
fun formatTime(time: LocalTime, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, locale))
}
