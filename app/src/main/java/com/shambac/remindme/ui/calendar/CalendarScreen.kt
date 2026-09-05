package com.shambac.remindme.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shambac.remindme.data.repository.ReminderRepository
import com.shambac.remindme.domain.model.ReminderOccurrence
import com.shambac.remindme.domain.recurrence.RecurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class CalendarUiState(val month: YearMonth = YearMonth.now(), val selected: LocalDate = LocalDate.now(), val reminders: List<ReminderOccurrence> = emptyList())

@HiltViewModel
class CalendarViewModel @Inject constructor(repository: ReminderRepository, calculator: RecurrenceCalculator, clock: Clock) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())
    val state = combine(month, selected, repository.observeReminders()) { currentMonth, day, series ->
        val from = currentMonth.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val to = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        CalendarUiState(currentMonth, day, series.flatMap { calculator.occurrencesBetween(it, from, to) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())
    fun previous() { month.value = month.value.minusMonths(1); selected.value = month.value.atDay(1) }
    fun next() { month.value = month.value.plusMonths(1); selected.value = month.value.atDay(1) }
    fun select(date: LocalDate) { selected.value = date }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onBack: () -> Unit, vm: CalendarViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val leading = (state.month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val days = (1..state.month.lengthOfMonth()).map { state.month.atDay(it) }
    Scaffold(topBar = { TopAppBar(title = { Text("Calendar") }, navigationIcon = { IconButton(onClick = onBack) { Text("Back") } }, actions = { IconButton(onClick = vm::previous) { Text("Previous month") }; IconButton(onClick = vm::next) { Text("Next month") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(state.month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())), fontWeight = FontWeight.Bold) }
            item { LazyVerticalGrid(columns = GridCells.Fixed(7), userScrollEnabled = false, modifier = Modifier.fillMaxWidth()) { gridItems(List(leading) { null } + days) { date -> Card(Modifier.padding(2.dp).clickable(enabled = date != null) { date?.let(vm::select) }) { Column(Modifier.padding(8.dp)) { Text(date?.dayOfMonth?.toString().orEmpty()); if (date != null && state.reminders.any { it.localDateTime.toLocalDate() == date }) Text("•") } } } } }
            item { Text("Reminders on ${state.selected}", fontWeight = FontWeight.Bold) }
            listItems(state.reminders.filter { it.localDateTime.toLocalDate() == state.selected }) { occurrence -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(occurrence.title, fontWeight = FontWeight.Bold); occurrence.description?.let { Text(it) }; Text(occurrence.localDateTime.toLocalTime().toString()) } } }
        }
    }
}
