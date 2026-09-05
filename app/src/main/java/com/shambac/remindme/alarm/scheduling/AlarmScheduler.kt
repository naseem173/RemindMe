package com.shambac.remindme.alarm.scheduling

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.shambac.remindme.alarm.directboot.ArmedAlarmMirrorEntity
import com.shambac.remindme.alarm.directboot.DirectBootMirrorDatabase
import com.shambac.remindme.alarm.receiver.AlarmReceiver
import com.shambac.remindme.data.local.AlarmInstanceDao
import com.shambac.remindme.data.local.ExceptionDao
import com.shambac.remindme.data.local.ReminderDao
import com.shambac.remindme.data.local.toDomain
import com.shambac.remindme.data.local.toEntity
import com.shambac.remindme.data.settings.SettingsRepository
import com.shambac.remindme.domain.model.AlarmInstance
import com.shambac.remindme.domain.model.AlarmState
import com.shambac.remindme.domain.model.ReminderId
import com.shambac.remindme.domain.recurrence.RecurrenceCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ExactAlarmCapability {
    fun isAvailable(): Boolean
    fun openSettingsIfRequired()
}

@Singleton
class AndroidExactAlarmCapability @Inject constructor(@ApplicationContext private val context: Context) : ExactAlarmCapability {
    override fun isAvailable(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    } else true

    override fun openSettingsIfRequired() {
        if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

interface ReminderScheduler {
    suspend fun reconcileAll()
    suspend fun scheduleNext(seriesId: ReminderId)
    suspend fun cancelSeries(seriesId: ReminderId)
    suspend fun scheduleSnooze(instanceId: String, at: java.time.Instant)
    suspend fun scheduleTestAlarm()
}

@Singleton
class AndroidReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: ReminderDao,
    private val exceptionDao: ExceptionDao,
    private val settings: SettingsRepository,
    private val alarmInstanceDao: AlarmInstanceDao,
    private val calculator: RecurrenceCalculator,
    private val exactAlarmCapability: ExactAlarmCapability,
    private val clock: Clock,
) : ReminderScheduler {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun reconcileAll() = withContext(Dispatchers.IO) {
        reminderDao.enabled().forEach { scheduleNext(it.id) }
    }

    override suspend fun scheduleNext(seriesId: ReminderId) = withContext(Dispatchers.IO) {
        if (!exactAlarmCapability.isAvailable()) return@withContext
        val seriesEntity = reminderDao.find(seriesId) ?: return@withContext
        val storedSeries = seriesEntity.toDomain()
        val dateOnlyTime = settings.settings.first().dateOnlyAlarmTime
        val series = if (storedSeries.dateOnly) storedSeries.copy(startTime = java.time.LocalTime.parse(dateOnlyTime)) else storedSeries
        val exceptions = exceptionDao.forSeries(seriesId).map { it.toDomain() }
        val occurrence = calculator.nextOccurrence(series, clock.instant(), exceptions) ?: return@withContext
        val instance = AlarmInstance(
            instanceId = UUID.nameUUIDFromBytes("$seriesId|${occurrence.localDateTime}".toByteArray()).toString(),
            seriesId = seriesId, originalOccurrence = occurrence.localDateTime,
            triggerInstant = occurrence.instant, state = AlarmState.SCHEDULED, createdAt = clock.instant(),
        )
        alarmInstanceDao.upsert(instance.toEntity())
        updateMirror(series, instance, occurrence)
        scheduleExact(instance.instanceId, occurrence.instant)
    }

    override suspend fun scheduleTestAlarm() = withContext(Dispatchers.IO) {
        if (!exactAlarmCapability.isAvailable()) return@withContext
        val instanceId = "test-${UUID.randomUUID()}"
        val trigger = clock.instant().plusSeconds(10)
        DirectBootMirrorDatabase.get(context).alarmDao().upsert(
            ArmedAlarmMirrorEntity(instanceId, "test", trigger.toEpochMilli(), "Test alarm", "This is a RemindMe alarm test.", null, true, false, 10, 1),
        )
        scheduleExact(instanceId, trigger)
    }

    override suspend fun scheduleSnooze(instanceId: String, at: java.time.Instant) = withContext(Dispatchers.IO) {
        if (!exactAlarmCapability.isAvailable()) return@withContext
        val id = "snooze-$instanceId-${at.toEpochMilli()}"
        scheduleExact(id, at)
    }

    override suspend fun cancelSeries(seriesId: ReminderId) = withContext(Dispatchers.IO) {
        alarmInstanceDao.scheduledForSeries(seriesId).forEach { cancelPendingIntent(it.instanceId) }
        alarmInstanceDao.cancelPendingForSeries(seriesId)
        DirectBootMirrorDatabase.get(context).alarmDao().all().filter { it.seriesId == seriesId }.forEach { mirror ->
            DirectBootMirrorDatabase.get(context).alarmDao().delete(mirror.instanceId)
        }
    }

    private fun scheduleExact(id: String, instant: java.time.Instant) {
        val pending = receiverPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT)
        val showIntent = PendingIntent.getActivity(
            context, id.hashCode(), Intent(context, com.shambac.remindme.MainActivity::class.java).putExtra(AlarmReceiver.EXTRA_INSTANCE_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmClockInfo(instant.toEpochMilli(), showIntent), pending)
    }

    private fun cancelPendingIntent(id: String) {
        alarmManager.cancel(receiverPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun receiverPendingIntent(id: String, flags: Int): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(), Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE).putExtra(AlarmReceiver.EXTRA_INSTANCE_ID, id),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun updateMirror(
        series: com.shambac.remindme.domain.model.ReminderSeries,
        instance: AlarmInstance,
        occurrence: com.shambac.remindme.domain.model.ReminderOccurrence,
) {
        DirectBootMirrorDatabase.get(context).alarmDao().upsert(
            ArmedAlarmMirrorEntity(instance.instanceId, series.id, occurrence.instant.toEpochMilli(), occurrence.title, occurrence.description, occurrence.alarmSoundUri, occurrence.vibrate, occurrence.speakReminder, occurrence.snoozeMinutes, occurrence.maxRingMinutes),
        )
    }
}
