package com.shambac.remindme.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(state: HealthUiState, actions: HealthViewModel, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Alarm health") }, navigationIcon = { IconButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusRow("Exact alarms", state.exact, "Alarm permission unavailable")
                StatusRow("Notifications", state.notifications, "Notifications disabled")
                StatusRow("Full-screen alarms", state.fullScreen, "Full-screen alarm permission disabled")
                Text("Alarm volume: ${state.alarmVolume}%${if (state.alarmVolume == 0) " — Alarm volume appears to be muted." else ""}")
                if (state.alarmVolume == 0) Button(onClick = actions::openSoundSettings) { Text("Open sound settings") }
                Text("Android Do Not Disturb settings control whether alarm audio may sound; RemindMe does not bypass them.")
                Text("Next scheduled alarm: ${state.nextAlarm?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)) } ?: "None"}")
                Text("Last alarm fired: ${state.lastResult}")
            } }
            if (!state.notifications) Button(onClick = actions::openNotifications, modifier = Modifier.fillMaxWidth()) { Text("Allow notifications") }
            if (!state.fullScreen) Button(onClick = actions::openFullScreen, modifier = Modifier.fillMaxWidth()) { Text("Allow full-screen alarms") }
            Button(onClick = actions::reconcile, modifier = Modifier.fillMaxWidth()) { Text("Test alarm in 10 seconds") }
            Text("If Android Settings > Force stop was used, open this app once to restore reminders.")
        }
    }
}

@Composable private fun StatusRow(label: String, ok: Boolean, problem: String) { Text("$label: ${if (ok) "OK" else "Problem — $problem"}") }
