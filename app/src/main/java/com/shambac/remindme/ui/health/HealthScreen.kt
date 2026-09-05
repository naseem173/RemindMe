package com.shambac.remindme.ui.health

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(state: HealthUiState, actions: HealthViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm health") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ready when you need it.", style = MaterialTheme.typography.headlineMedium)
                    Text("These checks explain whether Android can deliver an alarm.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusRow("Exact alarms", state.exact, "Allow exact alarms")
                        StatusRow("Notifications", state.notifications, "Allow notifications")
                        StatusRow("Full-screen alarms", state.fullScreen, "Allow full-screen alarms")
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Delivery details", style = MaterialTheme.typography.titleLarge)
                        Text("Alarm volume: ${state.alarmVolume}%${if (state.alarmVolume == 0) " — appears muted." else ""}")
                        Text("Next scheduled alarm: ${state.nextAlarm?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)) } ?: "None"}")
                        Text("Full-screen UI appears when the device is locked or the screen is off; Android uses a heads-up alarm notification while you are actively using the phone.", style = MaterialTheme.typography.bodyMedium)
                        Text("Android Do Not Disturb controls whether alarm audio may sound. RemindMe does not bypass it.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (!state.exact) item { Button(onClick = actions::openExact, modifier = Modifier.fillMaxWidth()) { Text("Allow exact alarms") } }
            if (!state.notifications) item { Button(onClick = actions::openNotifications, modifier = Modifier.fillMaxWidth()) { Text("Allow notifications") } }
            if (!state.fullScreen) item { Button(onClick = actions::openFullScreen, modifier = Modifier.fillMaxWidth()) { Text("Allow full-screen alarms") } }
            if (state.alarmVolume == 0) item { Button(onClick = actions::openSoundSettings, modifier = Modifier.fillMaxWidth()) { Text("Open sound settings") } }
            item { Button(onClick = actions::reconcile, modifier = Modifier.fillMaxWidth()) { Text("Test alarm in 10 seconds") } }
            item { Text("If Android Settings > Force stop was used, open RemindMe once to restore reminders.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, problem: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(if (ok) "Ready" else problem, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    }
}

