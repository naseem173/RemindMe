package com.shambac.remindme.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_series ORDER BY startDate ASC, startTime ASC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ReminderSeriesEntity>>

    @Query("SELECT * FROM reminder_series WHERE enabled = 1 ORDER BY startDate ASC, startTime ASC")
    suspend fun enabled(): List<ReminderSeriesEntity>

    @Query("SELECT * FROM reminder_series WHERE id = :id LIMIT 1")
    suspend fun find(id: String): ReminderSeriesEntity?

    @Query("SELECT * FROM reminder_series WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY startDate ASC, startTime ASC")
    fun search(query: String): Flow<List<ReminderSeriesEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderSeriesEntity)

    @Query("DELETE FROM reminder_series WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE reminder_series SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)
}

@Dao
interface ExceptionDao {
    @Query("SELECT * FROM occurrence_exceptions WHERE seriesId = :seriesId")
    suspend fun forSeries(seriesId: String): List<ReminderOccurrenceExceptionEntity>

    @Query("SELECT * FROM occurrence_exceptions WHERE seriesId = :seriesId AND originalOccurrence = :occurrence LIMIT 1")
    suspend fun find(seriesId: String, occurrence: String): ReminderOccurrenceExceptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderOccurrenceExceptionEntity)

    @Query("DELETE FROM occurrence_exceptions WHERE seriesId = :seriesId")
    suspend fun deleteForSeries(seriesId: String)

    @Query("DELETE FROM occurrence_exceptions WHERE seriesId = :seriesId AND originalOccurrence = :occurrence")
    suspend fun delete(seriesId: String, occurrence: String)
}

@Dao
interface AlarmInstanceDao {
    @Query("SELECT * FROM alarm_instances WHERE instanceId = :id LIMIT 1")
    suspend fun find(id: String): AlarmInstanceEntity?

    @Query("SELECT * FROM alarm_instances WHERE seriesId = :seriesId AND state IN ('SCHEDULED', 'SNOOZED') ORDER BY triggerInstant ASC")
    suspend fun scheduledForSeries(seriesId: String): List<AlarmInstanceEntity>

    @Query("SELECT * FROM alarm_instances WHERE state = 'SCHEDULED' ORDER BY triggerInstant ASC LIMIT 1")
    fun observeNext(): Flow<AlarmInstanceEntity?>

    @Query("SELECT * FROM alarm_instances ORDER BY COALESCE(firedAt, createdAt) DESC LIMIT 1")
    fun observeLast(): Flow<AlarmInstanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlarmInstanceEntity)

    @Query("UPDATE alarm_instances SET state = :state, firedAt = :firedAt WHERE instanceId = :id AND state IN ('SCHEDULED', 'SNOOZED')")
    suspend fun markFiringIfPending(id: String, state: String = "FIRING", firedAt: Long): Int

    @Query("UPDATE alarm_instances SET state = :state, dismissedAt = :dismissedAt WHERE instanceId = :id AND state != 'DISMISSED'")
    suspend fun markDismissed(id: String, state: String = "DISMISSED", dismissedAt: Long): Int

    @Query("UPDATE alarm_instances SET state = 'SNOOZED' WHERE instanceId = :id AND state = 'FIRING'")
    suspend fun markSnoozed(id: String): Int

    @Query("UPDATE alarm_instances SET state = :state WHERE instanceId = :id")
    suspend fun setState(id: String, state: String): Int

    @Query("UPDATE alarm_instances SET state = 'CANCELLED' WHERE seriesId = :seriesId AND state IN ('SCHEDULED', 'SNOOZED')")
    suspend fun cancelPendingForSeries(seriesId: String)
}

@androidx.room.Database(
    entities = [ReminderSeriesEntity::class, ReminderOccurrenceExceptionEntity::class, AlarmInstanceEntity::class],
    version = 1,
    exportSchema = true,
)
@androidx.room.TypeConverters(RoomConverters::class)
abstract class RemindMeDatabase : androidx.room.RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun exceptionDao(): ExceptionDao
    abstract fun alarmInstanceDao(): AlarmInstanceDao
}
