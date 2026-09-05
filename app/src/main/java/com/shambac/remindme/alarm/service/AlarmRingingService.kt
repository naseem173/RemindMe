package com.shambac.remindme.alarm.service

import android.annotation.SuppressLint
import android.app.Service
import android.util.Log
import android.content.Intent
import android.os.IBinder
import android.os.UserManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import com.shambac.remindme.alarm.audio.AlarmAudioController
import com.shambac.remindme.alarm.directboot.ArmedAlarmMirrorEntity
import com.shambac.remindme.alarm.directboot.DirectBootMirrorDatabase
import com.shambac.remindme.alarm.notification.AlarmNotifications
import com.shambac.remindme.alarm.receiver.AlarmReceiver
import com.shambac.remindme.alarm.receiver.AlarmActionReceiver
import com.shambac.remindme.alarm.scheduling.ReminderScheduler
import com.shambac.remindme.data.local.AlarmInstanceDao
import com.shambac.remindme.domain.model.AlarmAudioConfig
import com.shambac.remindme.domain.scheduler.MissedAlarmOutcome
import com.shambac.remindme.domain.scheduler.MissedAlarmPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import dagger.Lazy

@AndroidEntryPoint
class AlarmRingingService : Service() {
    @Inject lateinit var audio: AlarmAudioController
    @Inject lateinit var scheduler: Lazy<ReminderScheduler>
    @Inject lateinit var instances: Lazy<AlarmInstanceDao>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var vibrator: Vibrator? = null
    private var current: ArmedAlarmMirrorEntity? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val instanceId = intent?.getStringExtra(AlarmReceiver.EXTRA_INSTANCE_ID)
            ?: intent?.getStringExtra(AlarmActionReceiver.EXTRA_INSTANCE_ID)
            ?: return START_NOT_STICKY
        val action = intent?.action
        if (action == AlarmRingingService.ACTION_FIRE && !activeInstances.add(instanceId)) return START_NOT_STICKY
        val mirrorDb = DirectBootMirrorDatabase.get(this)
        // Promotion happens before DB, audio, vibration, or TTS work.
        startForeground(AlarmNotifications.NOTIFICATION_ID, AlarmNotifications.ringing(this, instanceId, "Reminder", null, 10))
        scope.launch(Dispatchers.IO) {
            val mirror = mirrorDb.alarmDao().all().firstOrNull { it.instanceId == instanceId }
            if (mirror == null) { stopSelfResult(startId); return@launch }
            current = mirror
            val lateBy = (System.currentTimeMillis() - mirror.triggerAtMillis).coerceAtLeast(0L)
            if (action == ACTION_FIRE && lateBy > 0L) {
                when (MissedAlarmPolicy.classify(java.time.Duration.ofMillis(lateBy))) {
                    MissedAlarmOutcome.RING_IMMEDIATELY -> beginRinging(mirror, startId)
                    MissedAlarmOutcome.MISSED_NOTIFICATION, MissedAlarmOutcome.MISSED -> finishAsMissed(mirror, startId, showNotification = true)
                }
            } else {
                when (action) {
                    AlarmNotifications.ACTION_STOP -> dismiss(mirror, startId)
                    AlarmNotifications.ACTION_SNOOZE -> snooze(mirror, startId)
                    else -> beginRinging(mirror, startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun beginRinging(mirror: ArmedAlarmMirrorEntity, startId: Int) {
        if (getSystemService(UserManager::class.java).isUserUnlocked) {
            instances.get().markFiringIfPending(mirror.instanceId, firedAt = System.currentTimeMillis())
            runCatching { scheduler.get().scheduleNext(mirror.seriesId) }
                .onFailure { Log.e("RemindMeAlarm", "Could not schedule next occurrence", it) }
        }
        val notification = AlarmNotifications.ringing(this@AlarmRingingService, mirror.instanceId, mirror.title, mirror.description, mirror.snoozeMinutes)
        postNotification(AlarmNotifications.NOTIFICATION_ID, notification)
        runCatching { audio.start(AlarmAudioConfig(mirror.alarmSoundUri, mirror.vibrate, mirror.speakReminder, mirror.title, mirror.description, mirror.maxRingMinutes)) }
            .onFailure {
                Log.e("RemindMeAlarm", "Could not start alarm audio", it)
                postNotification(AlarmNotifications.NOTIFICATION_ID, AlarmNotifications.audioFailure(this@AlarmRingingService, mirror.title))
            }
        if (mirror.maxRingMinutes != -1) {
            delay(mirror.maxRingMinutes * 60_000L)
            finishAsMissed(mirror, startId)
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 700, 500, 700, 1_500)
        vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect)
        }
    }

    private fun speakOnce(mirror: ArmedAlarmMirrorEntity) {
        // TTS kept optional and isolated; a missing engine never interrupts ordinary alarm audio.
        runCatching {
            lateinit var tts: android.speech.tts.TextToSpeech
            tts = android.speech.tts.TextToSpeech(this) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) tts.speak("Reminder. ${mirror.title}. ${mirror.description.orEmpty()}", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "remindme-${mirror.instanceId}")
            }
            scope.launch { delay(30_000); tts.shutdown() }
        }
    }

    private suspend fun snooze(mirror: ArmedAlarmMirrorEntity, startId: Int) {
        if (getSystemService(UserManager::class.java).isUserUnlocked) instances.get().markSnoozed(mirror.instanceId)
        stopOutput()
        val snoozeId = "snooze-${mirror.instanceId}-${System.currentTimeMillis()}"
        val at = System.currentTimeMillis() + mirror.snoozeMinutes * 60_000L
        val snoozed = mirror.copy(instanceId = snoozeId, triggerAtMillis = at)
        val db = DirectBootMirrorDatabase.get(this)
        db.alarmDao().delete(mirror.instanceId); db.alarmDao().upsert(snoozed)
        val pending = android.app.PendingIntent.getBroadcast(this, snoozeId.hashCode(), Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE).putExtra(AlarmReceiver.EXTRA_INSTANCE_ID, snoozeId), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val show = android.app.PendingIntent.getActivity(this, snoozeId.hashCode(), Intent(this, com.shambac.remindme.ui.alarm.AlarmActivity::class.java).putExtra(AlarmReceiver.EXTRA_INSTANCE_ID, snoozeId), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        getSystemService(android.app.AlarmManager::class.java).setAlarmClock(android.app.AlarmManager.AlarmClockInfo(at, show), pending)
        stopSelfResult(startId)
    }

    private suspend fun dismiss(mirror: ArmedAlarmMirrorEntity, startId: Int) {
        if (getSystemService(UserManager::class.java).isUserUnlocked) instances.get().markDismissed(mirror.instanceId, dismissedAt = System.currentTimeMillis())
        stopOutput()
        DirectBootMirrorDatabase.get(this).alarmDao().delete(mirror.instanceId)
        if (getSystemService(UserManager::class.java).isUserUnlocked) runCatching { scheduler.get().scheduleNext(mirror.seriesId) }
        stopSelfResult(startId)
    }

    private suspend fun finishAsMissed(mirror: ArmedAlarmMirrorEntity, startId: Int, showNotification: Boolean = true) {
        if (getSystemService(UserManager::class.java).isUserUnlocked) instances.get().setState(mirror.instanceId, "MISSED")
        stopOutput()
        if (showNotification) postNotification(mirror.instanceId.hashCode(), AlarmNotifications.missed(this, mirror.title, mirror.description))
        DirectBootMirrorDatabase.get(this).alarmDao().delete(mirror.instanceId)
        if (getSystemService(UserManager::class.java).isUserUnlocked) runCatching { scheduler.get().scheduleNext(mirror.seriesId) }
        stopSelfResult(startId)
    }
    private fun stopOutput() { audio.stop(); vibrator?.cancel(); NotificationManagerCompat.from(this).cancel(AlarmNotifications.NOTIFICATION_ID) }
    @SuppressLint("MissingPermission")
    private fun postNotification(id: Int, notification: android.app.Notification) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) NotificationManagerCompat.from(this).notify(id, notification)
    }
    override fun onDestroy() { current?.let { activeInstances.remove(it.instanceId) }; stopOutput(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_FIRE = "com.shambac.remindme.alarm.FIRE_SERVICE"
        private val activeInstances = ConcurrentHashMap.newKeySet<String>()
    }
}
