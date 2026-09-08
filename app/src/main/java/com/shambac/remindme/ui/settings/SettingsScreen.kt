package com.shambac.remindme.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shambac.remindme.data.settings.AppSettings
import com.shambac.remindme.data.settings.SettingsRepository
import com.shambac.remindme.domain.format.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val state = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun update(transform: (AppSettings) -> AppSettings) { viewModelScope.launch { repository.update(transform) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    var timePickerOpen by remember { mutableStateOf(false) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
            vm.update { it.copy(defaultAlarmSoundUri = uri.toString()) }
        }
    }
    LaunchedEffect(timePickerOpen) {
        if (timePickerOpen) {
            val current = runCatching { LocalTime.parse(state.dateOnlyAlarmTime) }.getOrDefault(LocalTime.of(9, 0))
            TimePickerDialog(context, { _, hour, minute ->
                vm.update { it.copy(dateOnlyAlarmTime = "%02d:%02d".format(hour, minute)) }
                timePickerOpen = false
            }, current.hour, current.minute, is24Hour).apply {
                setOnDismissListener { timePickerOpen = false }
            }.show()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Make alarms work the way you expect.", style = MaterialTheme.typography.bodyLarge)
            }
            item {
                SettingsCard("Alarm defaults") {
                    Button(
                        onClick = {
                            soundPicker.launch(
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Default alarm sound")
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.defaultAlarmSoundUri?.let(android.net.Uri::parse)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose alarm sound") }
                    ToggleRow("Vibrate", state.vibrate) { vm.update { it.copy(vibrate = !it.vibrate) } }
                    ToggleRow("Read reminder aloud", state.speakReminder) { vm.update { it.copy(speakReminder = !it.speakReminder) } }
                    Text("Snooze: ${state.snoozeMinutes} minutes", style = MaterialTheme.typography.titleSmall)
                    Slider(value = state.snoozeMinutes.toFloat(), onValueChange = { vm.update { s -> s.copy(snoozeMinutes = it.toInt().coerceIn(1, 120)) } }, valueRange = 1f..120f, steps = 118)
                    ToggleRow("Ring until dismissed", state.maxRingMinutes == -1) { vm.update { it.copy(maxRingMinutes = if (it.maxRingMinutes == -1) 10 else -1) } }
                    if (state.maxRingMinutes != -1) {
                        Text("Ring duration: ${state.maxRingMinutes} minutes", style = MaterialTheme.typography.titleSmall)
                        Slider(value = state.maxRingMinutes.toFloat(), onValueChange = { vm.update { s -> s.copy(maxRingMinutes = it.toInt().coerceIn(1, 30)) } }, valueRange = 1f..30f, steps = 28)
                    }
                }
            }
            item {
                SettingsCard("Date-only reminders") {
                    val dateOnlyTime = runCatching { LocalTime.parse(state.dateOnlyAlarmTime) }.getOrDefault(LocalTime.of(9, 0))
                    Text("Date-only reminders ring at ${formatTime(dateOnlyTime, is24Hour)}.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { timePickerOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose default time") }
                }
            }
            item {
                SettingsCard("Appearance") {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (value, label) ->
                            Button(onClick = { vm.update { it.copy(theme = value) } }, enabled = state.theme != value) { Text(label) }
                        }
                    }
                    ToggleRow("Use device dynamic colors", state.dynamicColor) { vm.update { it.copy(dynamicColor = !it.dynamicColor) } }
                }
            }
            item {
                SettingsCard("About") {
                    Text("RemindMe is offline-first. Your reminders stay on this phone.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onAbout) { Text("About RemindMe") }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
                        Text("Open notification settings")
                    }
            }
        }
    }
}
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
