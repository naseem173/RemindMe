package com.shambac.remindme.ui.editor

import android.app.DatePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import android.app.TimePickerDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.DayOfWeek
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: EditorUiState, actions: EditorViewModel, onDone: () -> Unit) {
    var advanced by remember { mutableStateOf(false) }
    var repeatExpanded by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var customInterval by remember { mutableStateOf("1") }
    var customUnit by remember { mutableStateOf("day") }
    var customWeekdays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var customEnd by remember { mutableStateOf("Never") }
    var customEndDate by remember { mutableStateOf("") }
    var monthlyPattern by remember { mutableStateOf("day") }
    var monthlyDay by remember { mutableStateOf(state.date.dayOfMonth.toString()) }
    var monthlyOrdinal by remember { mutableStateOf("2") }
    var monthlyWeekday by remember { mutableStateOf(state.date.dayOfWeek) }
    var customCount by remember { mutableStateOf("10") }
    val context = LocalContext.current
    LaunchedEffect(datePickerOpen) {
        if (datePickerOpen) {
            DatePickerDialog(context, { _, year, month, day ->
                actions.date(LocalDate.of(year, month + 1, day))
                datePickerOpen = false
            }, state.date.year, state.date.monthValue - 1, state.date.dayOfMonth).apply {
                setOnDismissListener { datePickerOpen = false }
            }.show()
        }
    }
    LaunchedEffect(timePickerOpen) {
        if (timePickerOpen) {
            TimePickerDialog(context, { _, hour, minute ->
                actions.time(LocalTime.of(hour, minute))
                timePickerOpen = false
            }, state.time.hour, state.time.minute, true).apply {
                setOnDismissListener { timePickerOpen = false }
            }.show()
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(if (state.id == null) "Add reminder" else "Edit reminder") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.title, actions::title, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, isError = state.error != null)
            OutlinedTextField(state.description, actions::description, Modifier.fillMaxWidth(), label = { Text("Description (optional)") }, minLines = 2)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.date.toString(), onValueChange = {}, modifier = Modifier.weight(1f), label = { Text("Date") }, readOnly = true)
                Button(onClick = { datePickerOpen = true }, modifier = Modifier.sizeIn(minWidth = 96.dp, minHeight = 48.dp)) { Text("Choose") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Date-only reminder")
                Checkbox(checked = state.dateOnly, onCheckedChange = actions::dateOnly, modifier = Modifier.semantics { contentDescription = "Date-only reminder" })
            }
            if (!state.dateOnly) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = state.time.format(DateTimeFormatter.ofPattern("HH:mm")), onValueChange = {}, modifier = Modifier.weight(1f), label = { Text("Time") }, readOnly = true)
                    Button(onClick = { timePickerOpen = true }, modifier = Modifier.sizeIn(minWidth = 96.dp, minHeight = 48.dp)) { Text("Choose") }
                }
            }
            ExposedDropdownMenuBox(expanded = repeatExpanded, onExpandedChange = { repeatExpanded = !repeatExpanded }) {
                OutlinedTextField(value = repeatLabel(state.repeat), onValueChange = {}, readOnly = true, label = { Text("Repeat") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(repeatExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                DropdownMenu(expanded = repeatExpanded, onDismissRequest = { repeatExpanded = false }) {
                    listOf("Does not repeat" to null, "Every day" to "FREQ=DAILY", "Every weekday" to "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", "Every week" to "FREQ=WEEKLY", "Every month" to "FREQ=MONTHLY", "Every year" to "FREQ=YEARLY").forEach { (label, rule) -> DropdownMenuItem(text = { Text(label) }, onClick = { if (label == "Custom…") customOpen = true else actions.repeat(rule); repeatExpanded = false }) }
                    DropdownMenuItem(text = { Text("Custom…") }, onClick = { customOpen = true; repeatExpanded = false })
                }
            }
            if (customOpen) {
                OutlinedTextField(customInterval, { customInterval = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("Repeat every N") }, singleLine = true)
                Text("Unit: $customUnit")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("day", "week", "month", "year").forEach { unit ->
                        Button(onClick = { customUnit = unit }, enabled = customUnit != unit) { Text(unit) }
                    }
                }
                if (customUnit == "month") {
                    Text("Monthly pattern")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { monthlyPattern = "day" }, enabled = monthlyPattern != "day") { Text("Day of month") }
                        Button(onClick = { monthlyPattern = "ordinal" }, enabled = monthlyPattern != "ordinal") { Text("Weekday ordinal") }
                    }
                    if (monthlyPattern == "day") {
                        OutlinedTextField(monthlyDay, { monthlyDay = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("Day (1–31)") }, singleLine = true)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1", "2", "3", "4", "-1").forEach { ordinal ->
                                Button(onClick = { monthlyOrdinal = ordinal }, enabled = monthlyOrdinal != ordinal) { Text(ordinal) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DayOfWeek.values().forEach { day ->
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(day.name.take(2))
                                    Checkbox(checked = monthlyWeekday == day, onCheckedChange = { if (it) monthlyWeekday = day }, modifier = Modifier.semantics { contentDescription = day.name })
                                }
                            }
                        }
                    }
                }
                if (customUnit == "week") {
                    Text("Weekdays")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DayOfWeek.values().forEach { day ->
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(day.name.take(2))
                                Checkbox(checked = day in customWeekdays, onCheckedChange = { checked -> customWeekdays = if (checked) customWeekdays + day else customWeekdays - day }, modifier = Modifier.semantics { contentDescription = day.name })
                            }
                        }
                    }
                }
                Text("Ends: $customEnd")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Never", "On date", "After count").forEach { end ->
                        Button(onClick = { customEnd = end }, enabled = customEnd != end) { Text(end) }
                    }
                }
                if (customEnd == "On date") OutlinedTextField(customEndDate, { customEndDate = it }, Modifier.fillMaxWidth(), label = { Text("End date (YYYY-MM-DD)") }, singleLine = true)
                if (customEnd == "After count") OutlinedTextField(customCount, { customCount = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Number of occurrences") }, singleLine = true)
                Button(onClick = {
                    val interval = customInterval.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val endDate = customEndDate.takeIf { customEnd == "On date" }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val count = customCount.toIntOrNull()?.takeIf { customEnd == "After count" && it > 0 }
                    actions.repeat(
                        when (customUnit) {
                            "week" -> com.shambac.remindme.domain.recurrence.RecurrenceRules.weekly(interval, customWeekdays.ifEmpty { setOf(DayOfWeek.from(state.date)) }, count, endDate)
                            "month" -> if (monthlyPattern == "day") com.shambac.remindme.domain.recurrence.RecurrenceRules.monthlyOnDay(monthlyDay.toIntOrNull()?.coerceIn(1, 31) ?: state.date.dayOfMonth, interval, count, endDate) else com.shambac.remindme.domain.recurrence.RecurrenceRules.monthlyOnWeekday(monthlyOrdinal.toIntOrNull() ?: 2, monthlyWeekday, interval, count, endDate)
                            "year" -> com.shambac.remindme.domain.recurrence.RecurrenceRules.yearly(interval, count, endDate)
                            else -> com.shambac.remindme.domain.recurrence.RecurrenceRules.daily(interval, count, endDate)
                        },
                    )
                    customOpen = false
                }, modifier = Modifier.fillMaxWidth()) { Text("Apply custom recurrence") }
            }
            Button(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) { Text(if (advanced) "Hide more options" else "More options") }
            if (advanced) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Vibrate"); Checkbox(state.vibrate, { actions.advanced(vibrate = it) }) }
                Text("Timezone")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.timezone(com.shambac.remindme.domain.model.TimezoneMode.DEVICE_LOCAL) }, enabled = state.timezoneMode != com.shambac.remindme.domain.model.TimezoneMode.DEVICE_LOCAL) { Text("Device local") }
                    Button(onClick = { actions.timezone(com.shambac.remindme.domain.model.TimezoneMode.FIXED_ZONE, "UTC") }, enabled = state.zoneId != "UTC") { Text("UTC") }
                    Button(onClick = { actions.timezone(com.shambac.remindme.domain.model.TimezoneMode.FIXED_ZONE, "America/New_York") }, enabled = state.zoneId != "America/New_York") { Text("New York") }
                }
                Text("Snooze: ${state.snooze} minutes · Ring: ${if (state.ring == -1) "until dismissed" else "${state.ring} minutes"}")
            }
            state.savedMessage?.let {
                Text(it)
                LaunchedEffect(it) { onDone() }
            }
            Button(onClick = actions::save, enabled = state.title.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text("Save") }
        }
    }
}

private fun repeatLabel(rule: String?): String = when {
    rule == null -> "Does not repeat"
    rule == "FREQ=DAILY" -> "Every day"
    rule?.contains("BYDAY=MO,TU,WE,TH,FR") == true -> "Every weekday"
    rule == "FREQ=WEEKLY" -> "Every week"
    rule == "FREQ=MONTHLY" -> "Every month"
    rule == "FREQ=YEARLY" -> "Every year"
    else -> "Custom"
}
