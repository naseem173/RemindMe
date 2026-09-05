package com.shambac.remindme.alarm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.provider.Settings
import com.shambac.remindme.domain.model.AlarmAudioConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

interface AlarmAudioController {
    suspend fun start(config: AlarmAudioConfig)
    fun stop()
}

@Singleton
class AndroidAlarmAudioController @Inject constructor(@ApplicationContext private val context: Context) : AlarmAudioController {
    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val stopped = AtomicBoolean(false)

    override suspend fun start(config: AlarmAudioConfig) {
        stopped.set(false)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val manager = context.getSystemService(AudioManager::class.java)
        audioManager = manager
        val focusResult = if (android.os.Build.VERSION.SDK_INT >= 26) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) stop()
            }.build()
            manager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }
        check(focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Alarm audio focus unavailable" }

        val uri = listOfNotNull(config.soundUri?.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse), Settings.System.DEFAULT_ALARM_ALERT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).distinct().firstOrNull()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, uri ?: error("No alarm sound available"))
                isLooping = true
                prepare()
                if (!stopped.get()) start()
            }
        } catch (failure: Exception) {
            stop()
            throw IllegalStateException("Selected ringtone unavailable", failure)
        }
    }

    override fun stop() {
        stopped.set(true)
        player?.runCatching { stop() }
        player?.release()
        player = null
        audioManager?.let { manager ->
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            @Suppress("DEPRECATION") manager.abandonAudioFocus(null)
        }
        focusRequest = null
    }
}
