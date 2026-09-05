package com.shambac.remindme.alarm.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.shambac.remindme.R
import com.shambac.remindme.alarm.receiver.AlarmActionReceiver
import com.shambac.remindme.ui.alarm.AlarmActivity

object AlarmNotifications {
    const val CHANNEL_ID = "alarms_v1"
    const val NOTIFICATION_ID = 701
    const val ACTION_STOP = "com.shambac.remindme.STOP"
    const val ACTION_SNOOZE = "com.shambac.remindme.SNOOZE"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.alarm_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    fun ringing(
        context: Context,
        instanceId: String,
        title: String,
        description: String?,
        snoozeMinutes: Int,
    ): Notification {
        val alarmIntent = Intent(context, AlarmActivity::class.java).putExtra(AlarmActionReceiver.EXTRA_INSTANCE_ID, instanceId)
        val fullScreen = PendingIntent.getActivity(context, instanceId.hashCode(), alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = action(context, ACTION_STOP, instanceId)
        val snooze = action(context, ACTION_SNOOZE, instanceId)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Snooze ${snoozeMinutes} min", snooze)
            .addAction(0, "Stop", stop)
            .build()
    }

    fun missed(context: Context, title: String, description: String?): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Missed reminder: $title").setContentText(description)
        .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
    fun audioFailure(context: Context, title: String): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("Alarm sound unavailable")
        .setContentText(title)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .build()

    private fun action(context: Context, action: String, instanceId: String): PendingIntent = PendingIntent.getBroadcast(
        context, "$action|$instanceId".hashCode(), Intent(context, AlarmActionReceiver::class.java).setAction(action).putExtra(AlarmActionReceiver.EXTRA_INSTANCE_ID, instanceId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
