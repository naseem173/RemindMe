package com.shambac.remindme.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shambac.remindme.data.settings.AppSettings
import com.shambac.remindme.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val state = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun update(transform: (AppSettings) -> AppSettings) { viewModelScope.launch { repository.update(transform) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri -> vm.update { it.copy(defaultAlarmSoundUri = uri.toString()) } } }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Alarm defaults") }
            item { Button(onClick = { soundPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Default alarm sound").putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.defaultAlarmSoundUri?.let(android.net.Uri::parse))) }, Modifier.fillMaxWidth()) { Text("Default alarm sound") } }
            item { ToggleRow("Vibrate", state.vibrate) { vm.update { it.copy(vibrate = !it.vibrate) } } }
            item { ToggleRow("Read reminder aloud", state.speakReminder) { vm.update { it.copy(speakReminder = !it.speakReminder) } } }
            item { Text("Snooze: ${state.snoozeMinutes} minutes"); Slider(value = state.snoozeMinutes.toFloat(), onValueChange = { vm.update { s -> s.copy(snoozeMinutes = it.toInt().coerceIn(1, 120)) } }, valueRange = 1f..120f, steps = 118) }
            item { Text("Ring duration: ${if (state.maxRingMinutes == -1) "Until dismissed" else "${state.maxRingMinutes} minutes"}"); Slider(value = state.maxRingMinutes.coerceAtLeast(1).toFloat(), onValueChange = { vm.update { s -> s.copy(maxRingMinutes = it.toInt().coerceIn(1, 30)) } }, valueRange = 1f..30f, steps = 28) }
            item { Text("Date-only alarm time: ${state.dateOnlyAlarmTime}") }
            item { Text("Appearance"); Text("System / Light / Dark · Dynamic color ${if (state.dynamicColor) "on" else "off"}") }
            item { Text("Calendar"); Text("Use device timezone by default") }
            item { Text("About"); Text("RemindMe keeps reminders on this device. No account or internet required.") }
        }
    }
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Checkbox(checked, { onToggle() }) } }
