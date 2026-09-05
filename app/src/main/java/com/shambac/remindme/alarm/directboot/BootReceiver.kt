package com.shambac.remindme.alarm.directboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.shambac.remindme.alarm.receiver.AlarmReceiver
import com.shambac.remindme.alarm.receiver.ReconcileReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || !context.getSystemService(UserManager::class.java).isUserUnlocked) {
                    val db = DirectBootMirrorDatabase.get(context)
                    val manager = context.getSystemService(android.app.AlarmManager::class.java)
                    db.alarmDao().all().forEach { mirror ->
                        if (mirror.triggerAtMillis <= System.currentTimeMillis()) return@forEach
                        val alarm = android.app.PendingIntent.getBroadcast(context, mirror.instanceId.hashCode(), Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE).putExtra(AlarmReceiver.EXTRA_INSTANCE_ID, mirror.instanceId), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                        val show = android.app.PendingIntent.getActivity(context, mirror.instanceId.hashCode(), Intent(context, com.shambac.remindme.ui.alarm.AlarmActivity::class.java).putExtra(AlarmReceiver.EXTRA_INSTANCE_ID, mirror.instanceId), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                        manager.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(mirror.triggerAtMillis, show), alarm)
                    }
                } else {
                    context.sendBroadcast(Intent(context, ReconcileReceiver::class.java).setAction(intent.action))
                }
            } finally { pending.finish() }
        }
    }
}
