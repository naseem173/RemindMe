package com.shambac.remindme.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import com.shambac.remindme.alarm.scheduling.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReconcileReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: Lazy<ReminderScheduler>
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { scheduler.get().reconcileAll() }
            pending.finish()
        }
    }
}
