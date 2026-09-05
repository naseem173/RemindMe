package com.shambac.remindme.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(page: Int, onNext: () -> Unit, onRequestNotifications: () -> Unit, onOpenFullScreen: () -> Unit, onTest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when (page) {
            0 -> { Text("Simple reminders that ring like an alarm."); Button(onClick = onNext, Modifier.fillMaxWidth()) { Text("Continue") } }
            1 -> { Text("To ring your reminders, allow notifications."); Button(onClick = onRequestNotifications, Modifier.fillMaxWidth()) { Text("Allow notifications") } }
            2 -> { Text("Full-screen alarms show your reminder over the lock screen."); Button(onClick = onOpenFullScreen, Modifier.fillMaxWidth()) { Text("Allow full-screen alarms") } }
            else -> { Text("Test your alarm"); Button(onClick = onTest, Modifier.fillMaxWidth()) { Text("Test alarm in 10 seconds") }; Button(onClick = onNext, Modifier.fillMaxWidth()) { Text("I heard it") } }
        }
    }
}
