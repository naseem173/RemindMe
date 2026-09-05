package com.shambac.remindme.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    page: Int,
    onNext: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenFullScreen: () -> Unit,
    onTest: () -> Unit,
) {
    val content = when (page) {
        0 -> "A calmer way to remember" to "RemindMe turns important moments into clear, audible alarms. Everything stays on this phone."
        1 -> "Let reminders reach you" to "Notifications let Android show an alarm while RemindMe is closed."
        2 -> "Wake the screen for alarms" to "Allow full-screen alarms so a reminder is visible over the lock screen."
        else -> "Hear a real alarm" to "Run a ten-second test. You can change sound, vibration, and snooze later in Settings."
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("REMINDME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(content.first, style = MaterialTheme.typography.headlineMedium)
                Text(content.second, style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator({ ((page + 1) / 4f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                when (page) {
                    0 -> Button(onClick = onNext, Modifier.fillMaxWidth()) { Text("Get started") }
                    1 -> Button(onClick = onRequestNotifications, Modifier.fillMaxWidth()) { Text("Allow notifications") }
                    2 -> Button(onClick = onOpenFullScreen, Modifier.fillMaxWidth()) { Text("Allow full-screen alarms") }
                    else -> {
                        Button(onClick = onTest, Modifier.fillMaxWidth()) { Text("Test alarm") }
                        TextButton(onClick = onNext, Modifier.fillMaxWidth()) { Text("I heard it — finish setup") }
                    }
                }
            }
        }
    }
}
