package com.shambac.remindme.ui.home

import androidx.lifecycle.ViewModel
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewModelScope
import com.shambac.remindme.data.repository.ReminderRepository
import com.shambac.remindme.domain.model.ReminderOccurrence
import com.shambac.remindme.domain.model.ReminderSeries
import com.shambac.remindme.domain.recurrence.RecurrenceCalculator
import com.shambac.remindme.alarm.scheduling.ExactAlarmCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class HomeUiState(val reminders: List<ReminderSeries> = emptyList(), val upcoming: List<ReminderOccurrence> = emptyList(), val search: String = "", val error: String? = null, val setupIncomplete: Boolean = false)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val calculator: RecurrenceCalculator,
    private val clock: Clock,
    private val exact: ExactAlarmCapability,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val query = MutableStateFlow("")
    val state: StateFlow<HomeUiState> = query.flatMapReminders(repository).combine(query) { reminders: List<ReminderSeries>, text: String ->
        val upcoming = reminders.flatMap { series -> calculator.nextOccurrence(series, clock.instant())?.let(::listOf).orEmpty() }.sortedBy { it.instant }
        val setupIncomplete = !exact.isAvailable() || !NotificationManagerCompat.from(context).areNotificationsEnabled()
        HomeUiState(reminders, upcoming, text, setupIncomplete = setupIncomplete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun search(value: String) { query.value = value }
    fun setEnabled(id: String, enabled: Boolean) { viewModelScope.launch { repository.setEnabled(id, enabled) } }
    fun delete(id: String) { viewModelScope.launch { repository.delete(id) } }
    fun deleteAll(ids: Set<String>) { viewModelScope.launch { repository.deleteAll(ids) } }
    fun clearError() { }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<String>.flatMapReminders(repository: ReminderRepository): Flow<List<ReminderSeries>> =
    flatMapLatest { q: String -> if (q.isBlank()) repository.observeReminders() else repository.search(q) }
