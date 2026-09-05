package com.shambac.remindme.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

typealias ReminderId = String
typealias AlarmInstanceId = String

enum class TimezoneMode { DEVICE_LOCAL, FIXED_ZONE }
enum class ExceptionType { SKIP, OVERRIDE }

enum class AlarmState { SCHEDULED, FIRING, SNOOZED, DISMISSED, MISSED, CANCELLED }

data class ReminderSeries(
    val id: ReminderId = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val startDate: LocalDate,
    val startTime: LocalTime? = null,
    val dateOnly: Boolean = startTime == null,
    val timezoneMode: TimezoneMode = TimezoneMode.DEVICE_LOCAL,
    val zoneId: String? = null,
    val recurrenceRule: String? = null,
    val alarmSoundUri: String? = null,
    val vibrate: Boolean = true,
    val speakReminder: Boolean = false,
    val snoozeMinutes: Int = 10,
    val maxRingMinutes: Int = 10,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class ReminderOccurrence(
    val seriesId: ReminderId,
    val localDateTime: LocalDateTime,
    val instant: Instant,
    val title: String,
    val description: String?,
    val alarmSoundUri: String? = null,
    val vibrate: Boolean = true,
    val speakReminder: Boolean = false,
    val snoozeMinutes: Int = 10,
    val maxRingMinutes: Int = 10,
    val isOverride: Boolean = false,
)

data class OccurrenceException(
    val id: String = UUID.randomUUID().toString(),
    val seriesId: ReminderId,
    val originalOccurrenceLocalDateTime: LocalDateTime,
    val type: ExceptionType,
    val overrideDate: LocalDate? = null,
    val overrideTime: LocalTime? = null,
    val overrideTitle: String? = null,
    val overrideDescription: String? = null,
    val overrideAlarmSoundUri: String? = null,
    val overrideVibrate: Boolean? = null,
    val overrideSpeakReminder: Boolean? = null,
    val overrideSnoozeMinutes: Int? = null,
    val overrideMaxRingMinutes: Int? = null,
)

data class AlarmInstance(
    val instanceId: AlarmInstanceId,
    val seriesId: ReminderId,
    val originalOccurrence: LocalDateTime,
    val triggerInstant: Instant,
    val state: AlarmState,
    val snoozeCount: Int = 0,
    val createdAt: Instant,
    val firedAt: Instant? = null,
    val dismissedAt: Instant? = null,
)

data class AlarmAudioConfig(
    val soundUri: String?,
    val vibrate: Boolean,
    val speakReminder: Boolean,
    val title: String,
    val description: String?,
    val maxRingMinutes: Int,
)

data class ReminderEdit(
    val title: String,
    val description: String?,
    val startDate: LocalDate,
    val startTime: LocalTime?,
    val recurrenceRule: String?,
    val dateOnly: Boolean,
    val timezoneMode: TimezoneMode,
    val zoneId: String?,
    val alarmSoundUri: String?,
    val vibrate: Boolean,
    val speakReminder: Boolean,
    val snoozeMinutes: Int,
    val maxRingMinutes: Int,
)

fun ReminderSeries.zone(): ZoneId = when (timezoneMode) {
    TimezoneMode.DEVICE_LOCAL -> ZoneId.systemDefault()
    TimezoneMode.FIXED_ZONE -> ZoneId.of(zoneId ?: ZoneId.systemDefault().id)
}
