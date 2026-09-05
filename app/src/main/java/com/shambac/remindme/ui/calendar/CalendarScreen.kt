package com.shambac.remindme.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    val cells = List(leading) { null } + days
    val weekdays = (0..6).map { firstDay.plus(it.toLong()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = vm::previous) { Text("Prev") }
                    TextButton(onClick = vm::next) { Text("Next") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    state.month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            weekdays.forEach { day ->
                                Text(day.name.take(2), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    val selected = date == state.selected
                                    val hasReminder = date != null && state.reminders.any { it.localDateTime.toLocalDate() == date }
                                    Box(
                                        Modifier.weight(1f).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .semantics { if (date != null) { role = Role.Button } }
                                            .clickable(enabled = date != null) { date?.let(vm::select) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = if (selected) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text(date?.dayOfMonth?.toString().orEmpty(), color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                                if (hasReminder) Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                repeat(7 - week.size) { Box(Modifier.weight(1f).sizeIn(minWidth = 48.dp, minHeight = 48.dp)) }
                            }
                        }
                    }
                }
            }
            item { Text("Reminders on ${state.selected}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            listItems(state.reminders.filter { it.localDateTime.toLocalDate() == state.selected }) { occurrence ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(occurrence.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        occurrence.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(occurrence.localDateTime.toLocalTime().toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
