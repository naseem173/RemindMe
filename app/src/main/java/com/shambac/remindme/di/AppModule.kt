package com.shambac.remindme.di

import android.content.Context
import androidx.room.Room
import com.shambac.remindme.data.local.AlarmInstanceDao
import com.shambac.remindme.data.local.ExceptionDao
import com.shambac.remindme.data.local.RemindMeDatabase
import com.shambac.remindme.data.local.ReminderDao
import com.shambac.remindme.domain.recurrence.Ical4jRecurrenceCalculator
import com.shambac.remindme.domain.recurrence.RecurrenceCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = Clock.systemUTC()

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): RemindMeDatabase = Room.databaseBuilder(context, RemindMeDatabase::class.java, "remindme.db").build()

    @Provides fun reminders(database: RemindMeDatabase): ReminderDao = database.reminderDao()
    @Provides fun exceptions(database: RemindMeDatabase): ExceptionDao = database.exceptionDao()
    @Provides fun instances(database: RemindMeDatabase): AlarmInstanceDao = database.alarmInstanceDao()
    @Provides @Singleton fun recurrenceCalculator(): RecurrenceCalculator = Ical4jRecurrenceCalculator()

    @Provides @Singleton
    fun exactCapability(implementation: com.shambac.remindme.alarm.scheduling.AndroidExactAlarmCapability): com.shambac.remindme.alarm.scheduling.ExactAlarmCapability = implementation

    @Provides @Singleton
    fun scheduler(implementation: com.shambac.remindme.alarm.scheduling.AndroidReminderScheduler): com.shambac.remindme.alarm.scheduling.ReminderScheduler = implementation

    @Provides @Singleton
    fun alarmAudio(implementation: com.shambac.remindme.alarm.audio.AndroidAlarmAudioController): com.shambac.remindme.alarm.audio.AlarmAudioController = implementation
}
