package com.shambac.remindme.ui.health

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shambac.remindme.alarm.scheduling.ExactAlarmCapability
import com.shambac.remindme.alarm.scheduling.ReminderScheduler
import com.shambac.remindme.data.local.AlarmInstanceDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthUiState(val exact: Boolean, val notifications: Boolean, val fullScreen: Boolean, val alarmVolume: Int, val nextAlarm: Long? = null, val lastResult: String = "No alarms yet")

@HiltViewModel
class HealthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exact: ExactAlarmCapability,
    private val scheduler: ReminderScheduler,
    instances: AlarmInstanceDao,
): ViewModel() {
    val state: StateFlow<HealthUiState> = instances.observeNext().map { next ->
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        HealthUiState(exact.isAvailable(), NotificationManagerCompatShim.enabled(context), fullScreenAllowed(context), current * 100 / max, next?.triggerInstant)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState(false, false, false, 0))

    fun openNotifications() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openFullScreen() {
        if (Build.VERSION.SDK_INT >= 34) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun reconcile() { viewModelScope.launch { scheduler.scheduleTestAlarm() } }
    fun openExact() = exact.openSettingsIfRequired()
    fun openSoundSettings() {
        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private object NotificationManagerCompatShim { fun enabled(context: Context) = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
private fun fullScreenAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
