package com.shambac.remindme

import android.app.Application
import com.shambac.remindme.alarm.notification.AlarmNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RemindMeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.createChannels(this)
    }
}
