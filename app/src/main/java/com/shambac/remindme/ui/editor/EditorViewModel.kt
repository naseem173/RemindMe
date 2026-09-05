package com.shambac.remindme.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shambac.remindme.data.repository.ReminderRepository
import com.shambac.remindme.data.settings.AppSettings
import com.shambac.remindme.data.settings.SettingsRepository
import com.shambac.remindme.domain.model.ReminderEdit
import com.shambac.remindme.domain.model.TimezoneMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class EditorUiState(
    val id: String? = null, val title: String = "", val description: String = "", val date: LocalDate = LocalDate.now(), val time: LocalTime = LocalTime.of(9, 0),
    val dateOnly: Boolean = false, val repeat: String? = null, val timezoneMode: TimezoneMode = TimezoneMode.DEVICE_LOCAL, val zoneId: String? = null,
    val soundUri: String? = null, val vibrate: Boolean = true, val speak: Boolean = false, val snooze: Int = 10, val ring: Int = 10,
    val error: String? = null, val savedMessage: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(private val repository: ReminderRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun load(id: String?) = viewModelScope.launch {
        if (id == null) {
            val defaults = settings.settings.first()
            _state.value = _state.value.copy(soundUri = defaults.defaultAlarmSoundUri, vibrate = defaults.vibrate, snooze = defaults.snoozeMinutes, ring = defaults.maxRingMinutes, speak = defaults.speakReminder, time = LocalTime.parse(defaults.dateOnlyAlarmTime))
        } else repository.get(id)?.let { item -> _state.value = EditorUiState(item.id, item.title, item.description.orEmpty(), item.startDate, item.startTime ?: LocalTime.of(9, 0), item.dateOnly, item.recurrenceRule, item.timezoneMode, item.zoneId, item.alarmSoundUri, item.vibrate, item.speakReminder, item.snoozeMinutes, item.maxRingMinutes) }
    }
    fun title(v: String) { _state.value = _state.value.copy(title = v, error = null) }
    fun description(v: String) { _state.value = _state.value.copy(description = v) }
    fun date(v: LocalDate) { _state.value = _state.value.copy(date = v) }
    fun dateOnly(v: Boolean) { _state.value = _state.value.copy(dateOnly = v) }
    fun time(v: LocalTime) { _state.value = _state.value.copy(time = v) }
    fun timezone(mode: TimezoneMode, zoneId: String? = null) { _state.value = _state.value.copy(timezoneMode = mode, zoneId = zoneId) }
    fun repeat(v: String?) { _state.value = _state.value.copy(repeat = v) }
    fun advanced(vibrate: Boolean = _state.value.vibrate, speak: Boolean = _state.value.speak) { _state.value = _state.value.copy(vibrate = vibrate, speak = speak) }
    fun save() = viewModelScope.launch {
        val s = _state.value
        repository.save(ReminderEdit(s.title, s.description, s.date, s.time.takeUnless { s.dateOnly }, s.repeat, s.dateOnly, s.timezoneMode, s.zoneId, s.soundUri, s.vibrate, s.speak, s.snooze, s.ring), s.id)
            .onSuccess { _state.value = s.copy(savedMessage = "Saved") }.onFailure { _state.value = s.copy(error = it.message ?: "Could not save reminder") }
    }
}
