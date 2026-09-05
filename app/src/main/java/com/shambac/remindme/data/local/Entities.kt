package com.shambac.remindme.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shambac.remindme.domain.model.AlarmState
import com.shambac.remindme.domain.model.ExceptionType
import com.shambac.remindme.domain.model.ReminderSeries
import com.shambac.remindme.domain.model.TimezoneMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "reminder_series", indices = [Index("enabled"), Index("startDate")])
data class ReminderSeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val enabled: Boolean,
    val startDate: String,
    val startTime: String?,
    val dateOnly: Boolean,
    val timezoneMode: String,
    val zoneId: String?,
    val recurrenceRule: String?,
    val alarmSoundUri: String?,
    val vibrate: Boolean,
    val speakReminder: Boolean,
    val snoozeMinutes: Int,
    val maxRingMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "occurrence_exceptions",
    indices = [Index(value = ["seriesId", "originalOccurrence"]), Index("seriesId")],
)
data class ReminderOccurrenceExceptionEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val originalOccurrence: String,
    val type: String,
    val overrideDate: String?,
    val overrideTime: String?,
    val overrideTitle: String?,
    val overrideDescription: String?,
    val overrideAlarmSoundUri: String?,
    val overrideVibrate: Boolean?,
    val overrideSpeakReminder: Boolean?,
    val overrideSnoozeMinutes: Int?,
    val overrideMaxRingMinutes: Int?,
)

@Entity(tableName = "alarm_instances", indices = [Index("seriesId"), Index("state"), Index("triggerInstant")])
data class AlarmInstanceEntity(
    @PrimaryKey val instanceId: String,
    val seriesId: String,
    val originalOccurrence: String,
    val triggerInstant: Long,
    val state: String,
    val snoozeCount: Int,
    val createdAt: Long,
    val firedAt: Long?,
    val dismissedAt: Long?,
)

fun ReminderSeriesEntity.toDomain() = ReminderSeries(
    id = id,
    title = title,
    description = description,
    enabled = enabled,
    startDate = LocalDate.parse(startDate),
    startTime = startTime?.let(LocalTime::parse),
    dateOnly = dateOnly,
    timezoneMode = TimezoneMode.valueOf(timezoneMode),
    zoneId = zoneId,
    recurrenceRule = recurrenceRule,
    alarmSoundUri = alarmSoundUri,
    vibrate = vibrate,
    speakReminder = speakReminder,
    snoozeMinutes = snoozeMinutes,
    maxRingMinutes = maxRingMinutes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun ReminderSeries.toEntity() = ReminderSeriesEntity(
    id, title, description, enabled, startDate.toString(), startTime?.toString(), dateOnly,
    timezoneMode.name, zoneId, recurrenceRule, alarmSoundUri, vibrate, speakReminder,
    snoozeMinutes, maxRingMinutes, createdAt.toEpochMilli(), updatedAt.toEpochMilli(),
)

fun ReminderOccurrenceExceptionEntity.toDomain() = com.shambac.remindme.domain.model.OccurrenceException(
    id, seriesId, LocalDateTime.parse(originalOccurrence), ExceptionType.valueOf(type),
    overrideDate?.let(LocalDate::parse), overrideTime?.let(LocalTime::parse), overrideTitle,
    overrideDescription, overrideAlarmSoundUri, overrideVibrate, overrideSpeakReminder,
    overrideSnoozeMinutes, overrideMaxRingMinutes,
)

fun com.shambac.remindme.domain.model.OccurrenceException.toEntity() = ReminderOccurrenceExceptionEntity(
    id, seriesId, originalOccurrenceLocalDateTime.toString(), type.name,
    overrideDate?.toString(), overrideTime?.toString(), overrideTitle, overrideDescription,
    overrideAlarmSoundUri, overrideVibrate, overrideSpeakReminder, overrideSnoozeMinutes,
    overrideMaxRingMinutes,
)

fun AlarmInstanceEntity.toDomain() = com.shambac.remindme.domain.model.AlarmInstance(
    instanceId, seriesId, LocalDateTime.parse(originalOccurrence), Instant.ofEpochMilli(triggerInstant),
    AlarmState.valueOf(state), snoozeCount, Instant.ofEpochMilli(createdAt), firedAt?.let(Instant::ofEpochMilli),
    dismissedAt?.let(Instant::ofEpochMilli),
)

fun com.shambac.remindme.domain.model.AlarmInstance.toEntity() = AlarmInstanceEntity(
    instanceId, seriesId, originalOccurrence.toString(), triggerInstant.toEpochMilli(), state.name,
    snoozeCount, createdAt.toEpochMilli(), firedAt?.toEpochMilli(), dismissedAt?.toEpochMilli(),
)

class RoomConverters {
    @androidx.room.TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @androidx.room.TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
