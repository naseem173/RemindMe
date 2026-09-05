package com.shambac.remindme

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.shambac.remindme.ui.alarm.AlarmScreen
import org.junit.Rule
import org.junit.Test

class ReminderEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ringingScreenKeepsStopAndSnoozeVisible() {
        compose.setContent { AlarmScreen("Take medicine", "After breakfast", 10, {}, {}) }
        compose.onNodeWithText("Take medicine").assertIsDisplayed()
        compose.onNodeWithText("After breakfast").assertIsDisplayed()
        compose.onNodeWithText("SNOOZE 10 MIN").assertIsDisplayed()
        compose.onNodeWithText("STOP").assertIsDisplayed()
    }
}
