package com.shambac.remindme.ui.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.lifecycleScope
import com.shambac.remindme.alarm.directboot.ArmedAlarmMirrorEntity
import com.shambac.remindme.alarm.directboot.DirectBootMirrorDatabase
import com.shambac.remindme.alarm.notification.AlarmNotifications
import com.shambac.remindme.alarm.receiver.AlarmActionReceiver
import com.shambac.remindme.alarm.receiver.AlarmReceiver
import com.shambac.remindme.ui.theme.RemindMeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AlarmActivity : ComponentActivity() {
    private var instanceId: String = ""
    private var mirror by mutableStateOf<ArmedAlarmMirrorEntity?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        instanceId = intent.getStringExtra(AlarmReceiver.EXTRA_INSTANCE_ID).orEmpty()
        lifecycleScope.launch {
            mirror = withContext(Dispatchers.IO) { DirectBootMirrorDatabase.get(this@AlarmActivity).alarmDao().all().firstOrNull { it.instanceId == instanceId } }
        }
        setContent { RemindMeTheme { AlarmScreen(mirror?.title ?: "Reminder", mirror?.description, mirror?.snoozeMinutes ?: 10, ::snooze, ::stop) } }
    }
    private fun snooze() = sendAction(AlarmNotifications.ACTION_SNOOZE)
    private fun stop() = sendAction(AlarmNotifications.ACTION_STOP)
    private fun sendAction(action: String) { startService(Intent(this, com.shambac.remindme.alarm.service.AlarmRingingService::class.java).setAction(action).putExtra(AlarmActionReceiver.EXTRA_INSTANCE_ID, instanceId)); finish() }
    @android.annotation.SuppressLint("MissingSuperCall")
    @Deprecated("Back must not dismiss a ringing alarm") override fun onBackPressed() { }
}

@Composable
fun AlarmScreen(title: String, description: String?, snoozeMinutes: Int, onSnooze: () -> Unit, onStop: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), style = MaterialTheme.typography.displayMedium)
            Text("REMINDER", style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.headlineLarge)
            description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Button(onClick = onSnooze, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(top = 24.dp).semantics { contentDescription = "Snooze reminder" }) { Text("SNOOZE $snoozeMinutes MIN") }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { contentDescription = "Stop reminder" }) { Text("STOP") }
        }
    }
}
