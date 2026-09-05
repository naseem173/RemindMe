package com.shambac.remindme

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shambac.remindme.ui.calendar.CalendarScreen as CalendarCalendarScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shambac.remindme.alarm.scheduling.ReminderScheduler
import com.shambac.remindme.data.settings.SettingsRepository
import com.shambac.remindme.ui.editor.EditorScreen
import com.shambac.remindme.ui.editor.EditorViewModel
import com.shambac.remindme.ui.health.HealthScreen
import com.shambac.remindme.ui.health.HealthViewModel
import com.shambac.remindme.ui.onboarding.OnboardingScreen
import com.shambac.remindme.ui.settings.SettingsScreen as AppSettingsScreen
import com.shambac.remindme.ui.theme.RemindMeTheme
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var scheduler: Lazy<ReminderScheduler>
    @Inject lateinit var settings: SettingsRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { runCatching { scheduler.get().reconcileAll() } }
        setContent {
            val appSettings by settings.settings.collectAsStateWithLifecycle(initialValue = com.shambac.remindme.data.settings.AppSettings())
            RemindMeTheme(
                darkTheme = when (appSettings.theme) {
                    "LIGHT" -> false
                    "DARK" -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
                dynamicColor = appSettings.dynamicColor,
            ) {
                if (appSettings.onboardingComplete) RemindMeApp() else OnboardingHost(settings, scheduler)
            }
        }
    }
}

@Composable
private fun OnboardingHost(settings: SettingsRepository, scheduler: Lazy<ReminderScheduler>) {
    var page by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { page = 2 }
    OnboardingScreen(
        page = page,
        onNext = { page++ },
        onRequestNotifications = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else page = 2 },
        onOpenFullScreen = {
            if (Build.VERSION.SDK_INT >= 34) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
            page = 3
        },
        onTest = { scope.launch { scheduler.get().scheduleTestAlarm() } },
    )
    if (page >= 4) LaunchedEffect(Unit) { settings.update { it.copy(onboardingComplete = true) } }
}

@Composable
fun RemindMeApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(onAdd = { nav.navigate("edit") }, onEdit = { nav.navigate("edit/$it") }, onCalendar = { nav.navigate("calendar") }, onHealth = { nav.navigate("health") }, onSettings = { nav.navigate("settings") }) }
        composable("edit") { val vm: EditorViewModel = hiltViewModel(); LaunchedEffect(Unit) { vm.load(null) }; EditorScreen(vm.state.collectAsStateWithLifecycle().value, vm, onDone = { nav.popBackStack() }) }
        composable("edit/{id}") { val vm: EditorViewModel = hiltViewModel(); val id = it.arguments?.getString("id"); LaunchedEffect(id) { vm.load(id) }; EditorScreen(vm.state.collectAsStateWithLifecycle().value, vm, onDone = { nav.popBackStack() }) }
        composable("calendar") { CalendarCalendarScreen(onBack = { nav.popBackStack() }) }
        composable("health") { val vm: HealthViewModel = hiltViewModel(); HealthScreen(vm.state.collectAsStateWithLifecycle().value, vm, onBack = { nav.popBackStack() }) }
        composable("settings") { AppSettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onCalendar: () -> Unit,
    onHealth: () -> Unit,
    onSettings: () -> Unit,
    vm: com.shambac.remindme.ui.home.HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RemindMe") },
                actions = {
                    IconButton(onClick = onCalendar) { Icon(Icons.Default.CalendarMonth, "Open calendar") }
                    IconButton(onClick = onHealth) { Icon(Icons.Default.HealthAndSafety, "Alarm health") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = "Add reminder" }) {
                Icon(Icons.Default.Add, "Add reminder")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your reminders", style = MaterialTheme.typography.headlineMedium)
                    Text("Simple alarms for the moments that matter.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = vm::search,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search reminders") },
                    singleLine = true,
                )
            }
            item {
                val next = state.upcoming.firstOrNull()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("NEXT ALARM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (next == null) {
                            Text("No upcoming alarms", style = MaterialTheme.typography.titleMedium)
                            Text("Tap + to create your first reminder.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(next.title, style = MaterialTheme.typography.titleLarge)
                            Text(next.instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)), style = MaterialTheme.typography.bodyMedium)
                            next.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
            if (state.setupIncomplete) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Alarm setup incomplete", style = MaterialTheme.typography.titleMedium)
                            Text("Check permissions before relying on reminders.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onHealth) { Text("Open alarm health") }
                        }
                    }
                }
            }
            item { Text("All reminders", style = MaterialTheme.typography.titleLarge) }
            if (state.reminders.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nothing planned yet.", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = onAdd) { Text("Add a reminder") }
                        }
                    }
                }
            } else {
                items(state.reminders, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(item.startDate.toString(), style = MaterialTheme.typography.bodyMedium)
                                    item.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                }
                                Switch(checked = item.enabled, onCheckedChange = { vm.setEnabled(item.id, it) })
                            }
                            Button(onClick = { onEdit(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Edit reminder") }
                        }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 88.dp)) }
        }
    }
}

