package com.shambac.remindme.data.repository

import androidx.room.withTransaction
import com.shambac.remindme.alarm.scheduling.ReminderScheduler
import com.shambac.remindme.data.local.ExceptionDao
import com.shambac.remindme.data.local.RemindMeDatabase
import com.shambac.remindme.data.local.ReminderDao
import com.shambac.remindme.data.local.toDomain
import com.shambac.remindme.data.local.toEntity
import com.shambac.remindme.domain.model.AlarmInstance
import com.shambac.remindme.domain.model.ExceptionType
import com.shambac.remindme.domain.model.OccurrenceException
import com.shambac.remindme.domain.model.ReminderEdit
import com.shambac.remindme.domain.model.ReminderSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val database: RemindMeDatabase,
    private val reminderDao: ReminderDao,
    private val exceptionDao: ExceptionDao,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    fun observeReminders(): Flow<List<ReminderSeries>> = reminderDao.observeAll().map { rows -> rows.map { it.toDomain() } }
    fun search(query: String): Flow<List<ReminderSeries>> = reminderDao.search(query).map { rows -> rows.map { it.toDomain() } }
    suspend fun get(id: String): ReminderSeries? = reminderDao.find(id)?.toDomain()
    suspend fun exceptions(id: String): List<OccurrenceException> = exceptionDao.forSeries(id).map { it.toDomain() }

    suspend fun save(edit: ReminderEdit, existingId: String? = null): Result<ReminderSeries> = runCatching {
        require(edit.title.trim().isNotEmpty()) { "Title is required" }
        require(edit.snoozeMinutes in 1..120)
        require(edit.maxRingMinutes == -1 || edit.maxRingMinutes in 1..30)
        if (edit.timezoneMode == com.shambac.remindme.domain.model.TimezoneMode.FIXED_ZONE) {
            java.time.ZoneId.of(edit.zoneId ?: error("Choose a timezone"))
        }
        val old = existingId?.let { reminderDao.find(it)?.toDomain() }
        val now = clock.instant()
        val series = ReminderSeries(
            id = old?.id ?: UUID.randomUUID().toString(), title = edit.title.trim(),
            description = edit.description?.trim()?.takeIf { it.isNotEmpty() }, enabled = old?.enabled ?: true,
            startDate = edit.startDate, startTime = edit.startTime, dateOnly = edit.dateOnly,
            timezoneMode = edit.timezoneMode, zoneId = edit.zoneId, recurrenceRule = edit.recurrenceRule,
            alarmSoundUri = edit.alarmSoundUri, vibrate = edit.vibrate, speakReminder = edit.speakReminder,
            snoozeMinutes = edit.snoozeMinutes, maxRingMinutes = edit.maxRingMinutes,
            createdAt = old?.createdAt ?: now, updatedAt = now,
        )
        database.withTransaction { reminderDao.upsert(series.toEntity()) }
        if (old != null) scheduler.cancelSeries(series.id)
        scheduler.scheduleNext(series.id)
        series
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        reminderDao.setEnabled(id, enabled, clock.instant().toEpochMilli())
        if (enabled) scheduler.scheduleNext(id) else scheduler.cancelSeries(id)
    }

    suspend fun delete(id: String) {
        scheduler.cancelSeries(id)
        database.withTransaction {
            exceptionDao.deleteForSeries(id)
            reminderDao.delete(id)
        }
    }

    suspend fun skipOccurrence(seriesId: String, occurrence: LocalDateTime) {
        exceptionDao.upsert(OccurrenceException(seriesId = seriesId, originalOccurrenceLocalDateTime = occurrence, type = ExceptionType.SKIP).toEntity())
        scheduler.scheduleNext(seriesId)
    }

    suspend fun overrideOccurrence(seriesId: String, occurrence: LocalDateTime, edit: ReminderEdit) {
        exceptionDao.upsert(
            OccurrenceException(
                seriesId = seriesId, originalOccurrenceLocalDateTime = occurrence, type = ExceptionType.OVERRIDE,
                overrideDate = edit.startDate, overrideTime = edit.startTime,
                overrideTitle = edit.title, overrideDescription = edit.description,
                overrideAlarmSoundUri = edit.alarmSoundUri, overrideVibrate = edit.vibrate,
                overrideSpeakReminder = edit.speakReminder, overrideSnoozeMinutes = edit.snoozeMinutes,
                overrideMaxRingMinutes = edit.maxRingMinutes,
            ).toEntity(),
        )
        scheduler.scheduleNext(seriesId)
    }
    suspend fun splitSeries(seriesId: String, occurrence: LocalDateTime, edit: ReminderEdit): Result<ReminderSeries> = runCatching {
        val original = reminderDao.find(seriesId)?.toDomain() ?: error("Reminder not found")
        val previousRule = original.recurrenceRule?.let { rule ->
            val until = occurrence.minusNanos(1).toLocalDate().toString().replace("-", "") + "T235959"
            (rule.split(";").filterNot { it.startsWith("UNTIL=") || it.startsWith("COUNT=") } + "UNTIL=$until").joinToString(";")
        }
        database.withTransaction {
            reminderDao.upsert(original.copy(recurrenceRule = previousRule, updatedAt = clock.instant()).toEntity())
        }
        save(edit).getOrThrow()
    }
}
