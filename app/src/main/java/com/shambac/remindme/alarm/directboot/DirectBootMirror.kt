package com.shambac.remindme.alarm.directboot

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "armed_alarm_mirror")
data class ArmedAlarmMirrorEntity(
    @androidx.room.PrimaryKey val instanceId: String,
    val seriesId: String,
    val triggerAtMillis: Long,
    val title: String,
    val description: String?,
    val alarmSoundUri: String?,
    val vibrate: Boolean,
    val speakReminder: Boolean,
    val snoozeMinutes: Int,
    val maxRingMinutes: Int,
)

@Dao
interface DirectBootMirrorDao {
    @Query("SELECT * FROM armed_alarm_mirror")
    suspend fun all(): List<ArmedAlarmMirrorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ArmedAlarmMirrorEntity)

    @Query("DELETE FROM armed_alarm_mirror WHERE instanceId = :instanceId")
    suspend fun delete(instanceId: String)

    @Query("DELETE FROM armed_alarm_mirror")
    suspend fun clear()
}

@Database(entities = [ArmedAlarmMirrorEntity::class], version = 1, exportSchema = false)
abstract class DirectBootMirrorDatabase : RoomDatabase() {
    abstract fun alarmDao(): DirectBootMirrorDao

    companion object {
        @Volatile private var instance: DirectBootMirrorDatabase? = null
        fun get(context: Context): DirectBootMirrorDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.createDeviceProtectedStorageContext(),
                DirectBootMirrorDatabase::class.java,
                "armed_alarm_mirror.db",
            ).build().also { instance = it }
        }
    }
}
