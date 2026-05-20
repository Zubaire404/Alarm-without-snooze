package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var autoStopJob: Job? = null

    companion object {
        const val CHANNEL_ID = "no_snooze_alarm_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_DISMISS = "com.example.ACTION_DISMISS"

        private val _isRinging = MutableStateFlow(false)
        val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

        private val _ringingAlarmId = MutableStateFlow<Int?>(null)
        val ringingAlarmId: StateFlow<Int?> = _ringingAlarmId.asStateFlow()

        private val _ringingAlarmLabel = MutableStateFlow("")
        val ringingAlarmLabel: StateFlow<String> = _ringingAlarmLabel.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        val label = intent?.getStringExtra("ALARM_LABEL") ?: "Alarm"

        if (action == ACTION_DISMISS) {
            Log.d("AlarmService", "Dismiss action clicked.")
            dismissAlarm(alarmId)
            stopSelf()
        } else if (action == ACTION_START) {
            Log.d("AlarmService", "Starting alarm sound & vibration for ID: $alarmId")
            _isRinging.value = true
            _ringingAlarmId.value = alarmId
            _ringingAlarmLabel.value = label

            // Update database and reschedule if repeating
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val alarmDao = db.alarmDao()
                val alarm = alarmDao.getAlarmById(alarmId)
                if (alarm != null) {
                    if (alarm.isRepeating()) {
                        // Reschedule the next repeatable trigger
                        val scheduler = AlarmScheduler(applicationContext)
                        scheduler.schedule(alarm)
                    } else {
                        // Deactivate one-off alarm in DB
                        alarmDao.updateAlarm(alarm.copy(isEnabled = false))
                    }
                }
            }

            // Start sound and vibration
            startRinging()
            startVibration()

            // Run in foreground
            val notification = createNotification(label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Auto-stop alarm after exactly 1 minute (60 seconds)
            autoStopJob?.cancel()
            autoStopJob = serviceScope.launch {
                delay(60000) // Exactly 1 minute (60,000 ms)
                Log.d("AlarmService", "Alarm timed out after 1 minute of ringing.")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startRinging() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to start audio playback", e)
        }
    }

    private fun startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibrator = vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val pattern = longArrayOf(0, 500, 500) // Vibrate 500ms, pause 500ms
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to start vibration", e)
        }
    }

    private fun stopRingingAndVibration() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {
                Log.e("AlarmService", "Error stopping player", e)
            } finally {
                it.release()
            }
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    private fun dismissAlarm(alarmId: Int) {
        stopRingingAndVibration()
    }

    override fun onDestroy() {
        Log.d("AlarmService", "Service destroyed. Resetting ringing state.")
        stopRingingAndVibration()
        autoStopJob?.cancel()
        serviceJob.cancel()
        _isRinging.value = false
        _ringingAlarmId.value = null
        _ringingAlarmLabel.value = ""
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(label: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("No-Snooze Alarm Ringing!")
            .setContentText("Label: $label (Rings for 1 min max)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "DISMISS (No Snooze)",
                dismissPendingIntent
            )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "No-Snooze Alarm Notification",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent channel for firing alarms with absolutely no snooze"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
