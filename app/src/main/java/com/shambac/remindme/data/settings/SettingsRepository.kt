package com.shambac.remindme.data.settings

import android.content.Context
import android.media.RingtoneManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("settings")

data class AppSettings(
    val defaultAlarmSoundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val maxRingMinutes: Int = 10,
    val speakReminder: Boolean = false,
    val dateOnlyAlarmTime: String = "09:00",
    val theme: String = "SYSTEM",
    val dynamicColor: Boolean = false,
    val onboardingComplete: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val sound = stringPreferencesKey("default_alarm_sound_uri")
        val vibrate = booleanPreferencesKey("vibrate")
        val snooze = intPreferencesKey("snooze_minutes")
        val ring = intPreferencesKey("ring_minutes")
        val speak = booleanPreferencesKey("speak_reminder")
        val dateOnlyTime = stringPreferencesKey("date_only_alarm_time")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color_v2")
        val onboarding = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            defaultAlarmSoundUri = prefs[Keys.sound], vibrate = prefs[Keys.vibrate] ?: true,
            snoozeMinutes = prefs[Keys.snooze] ?: 10, maxRingMinutes = prefs[Keys.ring] ?: 10,
            speakReminder = prefs[Keys.speak] ?: false, dateOnlyAlarmTime = prefs[Keys.dateOnlyTime] ?: "09:00",
            theme = prefs[Keys.theme] ?: "SYSTEM", dynamicColor = prefs[Keys.dynamic] ?: false,
            onboardingComplete = prefs[Keys.onboarding] ?: false,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { p ->
            val current = AppSettings(
                defaultAlarmSoundUri = p[Keys.sound],
                vibrate = p[Keys.vibrate] ?: true,
                snoozeMinutes = p[Keys.snooze] ?: 10,
                maxRingMinutes = p[Keys.ring] ?: 10,
                speakReminder = p[Keys.speak] ?: false,
                dateOnlyAlarmTime = p[Keys.dateOnlyTime] ?: "09:00",
                theme = p[Keys.theme] ?: "SYSTEM",
                dynamicColor = p[Keys.dynamic] ?: false,
                onboardingComplete = p[Keys.onboarding] ?: false,
            )
            val next = transform(current)
            p[Keys.sound] = next.defaultAlarmSoundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
            p[Keys.vibrate] = next.vibrate; p[Keys.snooze] = next.snoozeMinutes; p[Keys.ring] = next.maxRingMinutes
            p[Keys.speak] = next.speakReminder; p[Keys.dateOnlyTime] = next.dateOnlyAlarmTime; p[Keys.theme] = next.theme
            p[Keys.dynamic] = next.dynamicColor; p[Keys.onboarding] = next.onboardingComplete
        }
    }
}
