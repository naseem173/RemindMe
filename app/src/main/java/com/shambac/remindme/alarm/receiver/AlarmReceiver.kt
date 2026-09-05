package com.shambac.remindme.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.shambac.remindme.alarm.service.AlarmRingingService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: return
        val serviceIntent = Intent(context, AlarmRingingService::class.java)
            .setAction(AlarmRingingService.ACTION_FIRE)
            .putExtra(EXTRA_INSTANCE_ID, instanceId)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ACTION_FIRE = "com.shambac.remindme.FIRE"
        const val EXTRA_INSTANCE_ID = "extra_instance_id"
    }
}
