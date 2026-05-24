package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class TtsService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val binder = TtsBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentSentenceIndex = MutableStateFlow(0)
    val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex

    private var sentencesList = emptyList<String>()
    private var bookTitle = ""
    private var currentPageNum = 0
    private var utteranceIdCounter = 0

    private val _turkishStatus = MutableStateFlow("UNKNOWN")
    val turkishStatus: StateFlow<String> = _turkishStatus

    private var speedRate = 1.0f
    private var voicePitch = 1.0f

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            tts = TextToSpeech(this, this)
        } catch (e: Throwable) {
            Log.e("TtsService", "Failed to construct TextToSpeech instance: ${e.message}")
        }
        createNotificationChannel()
    }

    override fun onInit(status: Int) {
        try {
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { ttsInst ->
                    ttsInst.language = Locale("tr", "TR")
                    val trResult = ttsInst.isLanguageAvailable(Locale("tr", "TR"))
                    val statusStr = when (trResult) {
                        TextToSpeech.LANG_AVAILABLE,
                        TextToSpeech.LANG_COUNTRY_AVAILABLE,
                        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "AVAILABLE"
                        TextToSpeech.LANG_MISSING_DATA -> "MISSING_DATA"
                        TextToSpeech.LANG_NOT_SUPPORTED -> "NOT_SUPPORTED"
                        else -> "NOT_AVAILABLE"
                    }
                    _turkishStatus.value = statusStr
                    Log.d("TtsService", "Turkish TTS Support Status: $statusStr")

                    ttsInst.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onDone(utteranceId: String?) {
                            scope.launch {
                                playbackFinishedForCurrentUtterance()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.e("TtsService", "Speech Error in utterance $utteranceId")
                        }
                    })
                }
            } else {
                Log.e("TtsService", "Failed to initialize TTS engine")
            }
        } catch (e: Throwable) {
            Log.e("TtsService", "Exception in onInit: ${e.message}")
        }
    }

    fun checkTurkishLanguagePack(): String {
        return try {
            val result = tts?.isLanguageAvailable(Locale("tr", "TR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            val status = when (result) {
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "AVAILABLE"
                TextToSpeech.LANG_MISSING_DATA -> "MISSING_DATA"
                TextToSpeech.LANG_NOT_SUPPORTED -> "NOT_SUPPORTED"
                else -> "NOT_AVAILABLE"
            }
            _turkishStatus.value = status
            status
        } catch (e: Throwable) {
            Log.e("TtsService", "Error checking Turkish support: ${e.message}")
            "NOT_SUPPORTED"
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    fun playText(title: String, page: Int, sentences: List<String>) {
        if (sentences.isEmpty()) return
        
        requestAudioFocus()
        bookTitle = title
        currentPageNum = page
        sentencesList = sentences
        _currentSentenceIndex.value = 0
        _isPlaying.value = true

        try {
            startForeground(NOTIFICATION_ID, buildNotification(title, page, true))
        } catch (e: Throwable) {
            Log.e("TtsService", "Failed to start foreground service: ${e.message}")
        }
        speakCurrentSentence()
    }

    fun pausePlayback() {
        _isPlaying.value = false
        try {
            tts?.stop()
        } catch (e: Throwable) {
            Log.e("TtsService", "Error stopping TTS: ${e.message}")
        }
        updateNotification()
    }

    fun resumePlayback() {
        if (sentencesList.isEmpty() || _isPlaying.value) return
        requestAudioFocus()
        _isPlaying.value = true
        updateNotification()
        speakCurrentSentence()
    }

    fun stopPlayback() {
        _isPlaying.value = false
        try {
            tts?.stop()
        } catch (e: Throwable) {
            Log.e("TtsService", "Error stopping TTS: ${e.message}")
        }
        abandonAudioFocus()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Throwable) {
            Log.e("TtsService", "Error stopping foreground: ${e.message}")
        }
        stopSelf()
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        speedRate = speed
        voicePitch = pitch
        try {
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
        } catch (e: Throwable) {
            Log.e("TtsService", "Failed to set speech rate or pitch: ${e.message}")
        }
    }

    private fun speakCurrentSentence() {
        try {
            if (!_isPlaying.value || sentencesList.isEmpty()) return
            val index = _currentSentenceIndex.value
            if (index >= sentencesList.size) {
                // Reached the end of page sentences
                stopPlayback()
                return
            }

            val textToSpeak = sentencesList[index]
            tts?.setSpeechRate(speedRate)
            tts?.setPitch(voicePitch)

            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sentence_${utteranceIdCounter++}")
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
        } catch (e: Throwable) {
            Log.e("TtsService", "Error speaking current sentence: ${e.message}")
            stopPlayback()
        }
    }

    private fun playbackFinishedForCurrentUtterance() {
        if (!_isPlaying.value) return
        val nextIndex = _currentSentenceIndex.value + 1
        if (nextIndex < sentencesList.size) {
            _currentSentenceIndex.value = nextIndex
            speakCurrentSentence()
        } else {
            // Finished page
            _isPlaying.value = false
            _currentSentenceIndex.value = 0
            stopPlayback()
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pausePlayback()
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pausePlayback()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(bookTitle, currentPageNum, _isPlaying.value))
    }

    private fun buildNotification(title: String, page: Int, playing: Boolean): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        } ?: Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 100, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val playPauseAction = if (playing) {
            val pauseIntent = Intent(this, TtsService::class.java).apply { action = ACTION_PAUSE }
            val pausePI = PendingIntent.getService(this, 101, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pausePI)
        } else {
            val playIntent = Intent(this, TtsService::class.java).apply { action = ACTION_PLAY }
            val playPI = PendingIntent.getService(this, 102, playIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", playPI)
        }

        val stopIntent = Intent(this, TtsService::class.java).apply { action = ACTION_STOP }
        val stopPI = PendingIntent.getService(this, 103, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reading: $title")
            .setContentText("Page ${page + 1} - Reading Aloud")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle())
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setOngoing(playing)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "DriveReader Audio Playback"
            val descriptionText = "Handles Foreground TTS Playback Notification"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            try {
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Throwable) {
                Log.e("TtsService", "Failed to create notification channel: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            tts?.shutdown()
        } catch (e: Throwable) {
            Log.e("TtsService", "Failed to shutdown TTS in onDestroy: ${e.message}")
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "drive_reader_tts_channel"
        const val NOTIFICATION_ID = 2468

        const val ACTION_PLAY = "com.example.service.action.PLAY"
        const val ACTION_PAUSE = "com.example.service.action.PAUSE"
        const val ACTION_STOP = "com.example.service.action.STOP"
    }
}
