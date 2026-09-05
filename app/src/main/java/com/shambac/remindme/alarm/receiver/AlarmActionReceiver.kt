package com.shambac.remindme.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.shambac.remindme.alarm.service.AlarmRingingService
import com.shambac.remindme.alarm.notification.AlarmNotifications

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: return
        val serviceIntent = Intent(context, AlarmRingingService::class.java).setAction(action).putExtra(EXTRA_INSTANCE_ID, instanceId)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object { const val EXTRA_INSTANCE_ID = "extra_instance_id" }
}
