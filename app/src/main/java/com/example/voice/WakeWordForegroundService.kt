package com.example.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object WakeWordBus {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _statusText = MutableStateFlow("Inactive")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _wakeDetectedEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val wakeDetectedEvents: SharedFlow<String> = _wakeDetectedEvents.asSharedFlow()

    internal fun updateRunning(running: Boolean, status: String) {
        _serviceRunning.value = running
        _statusText.value = status
    }

    internal fun emitWakeDetected(transcript: String) {
        _wakeDetectedEvents.tryEmit(transcript)
    }
}

/**
 * Genuine Android Native Foreground Microphone Service for Automatic Wake-Word Mode.
 * Requires RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE + active persistent notification.
 */
class WakeWordForegroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wakeWord: String = "Hey AISHU"
    private var sensitivity: Int = 7
    private var languageTag: String = "en-US"
    private var isPausedForActiveChat: Boolean = false
    private var isStopping: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfAndCleanup()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_LISTENING -> {
                isPausedForActiveChat = true
                stopRecognizer()
                WakeWordBus.updateRunning(true, "Paused during active conversation")
                return START_STICKY
            }
            ACTION_RESUME_LISTENING -> {
                isPausedForActiveChat = false
                scheduleNextListenCycle(450L)
                return START_STICKY
            }
        }

        wakeWord = intent?.getStringExtra(EXTRA_WAKE_WORD)?.takeIf { it.isNotBlank() } ?: "Hey AISHU"
        sensitivity = intent?.getIntExtra(EXTRA_SENSITIVITY, 7) ?: 7
        languageTag = intent?.getStringExtra(EXTRA_LANGUAGE_TAG)?.takeIf { it.isNotBlank() } ?: "en-US"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            WakeWordBus.updateRunning(false, "Microphone permission required")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isStopping = false
        isPausedForActiveChat = false
        WakeWordBus.updateRunning(true, "Listening for \"$wakeWord\"")
        scheduleNextListenCycle(150L)

        return START_STICKY
    }

    private fun scheduleNextListenCycle(delayMs: Long) {
        if (isStopping || isPausedForActiveChat) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ startWakeListenCycle() }, delayMs)
    }

    private fun startWakeListenCycle() {
        if (isStopping || isPausedForActiveChat) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            WakeWordBus.updateRunning(true, "Waiting: SpeechRecognizer unavailable on device")
            return
        }

        stopRecognizer()
        val sr = runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull() ?: return
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                WakeWordBus.updateRunning(true, "Standby — listening for \"$wakeWord\"")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (!isStopping && !isPausedForActiveChat) {
                    val backoff = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 550L
                    scheduleNextListenCycle(backoff)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank() && WakeWordMatcher.matches(text, wakeWord, sensitivity)) {
                    onWakeWordDetected(text)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank() && WakeWordMatcher.matches(text, wakeWord, sensitivity)) {
                    onWakeWordDetected(text)
                } else if (!isStopping && !isPausedForActiveChat) {
                    scheduleNextListenCycle(350L)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { sr.startListening(listenIntent) }.onFailure {
            scheduleNextListenCycle(1500L)
        }
    }

    private fun onWakeWordDetected(transcript: String) {
        isPausedForActiveChat = true
        stopRecognizer()
        WakeWordBus.updateRunning(true, "Wake word detected!")
        WakeWordBus.emitWakeDetected(transcript)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ACTIVATED_BY_WAKE_WORD, true)
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun stopRecognizer() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun stopSelfAndCleanup() {
        isStopping = true
        mainHandler.removeCallbacksAndMessages(null)
        stopRecognizer()
        WakeWordBus.updateRunning(false, "Stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isStopping = true
        mainHandler.removeCallbacksAndMessages(null)
        stopRecognizer()
        WakeWordBus.updateRunning(false, "Stopped")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_wake_word),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(getString(R.string.wake_service_title))
            .setContentText("Listening for \"$wakeWord\" (Sensitivity $sensitivity/10)")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.wake_service_stop), stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "aishu_wake_word_channel"
        private const val NOTIFICATION_ID = 4401

        const val ACTION_START = "com.example.voice.ACTION_START_WAKE_WORD"
        const val ACTION_STOP = "com.example.voice.ACTION_STOP_WAKE_WORD"
        const val ACTION_PAUSE_LISTENING = "com.example.voice.ACTION_PAUSE_WAKE_WORD"
        const val ACTION_RESUME_LISTENING = "com.example.voice.ACTION_RESUME_WAKE_WORD"

        const val EXTRA_WAKE_WORD = "extra_wake_word"
        const val EXTRA_SENSITIVITY = "extra_sensitivity"
        const val EXTRA_LANGUAGE_TAG = "extra_language_tag"
        const val EXTRA_ACTIVATED_BY_WAKE_WORD = "extra_activated_by_wake_word"

        fun start(context: Context, wakeWord: String, sensitivity: Int, languageTag: String) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_WAKE_WORD, wakeWord)
                putExtra(EXTRA_SENSITIVITY, sensitivity)
                putExtra(EXTRA_LANGUAGE_TAG, languageTag)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_PAUSE_LISTENING
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_RESUME_LISTENING
            }
            context.startService(intent)
        }
    }
}
