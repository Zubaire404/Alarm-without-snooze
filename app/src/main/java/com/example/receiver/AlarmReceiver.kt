package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.scheduler.AlarmScheduler
import com.example.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("AlarmReceiver", "Broadcast received with action: $action")

        if (action == "com.example.ACTION_ALARM_TRIGGER") {
            val alarmId = intent.getIntExtra("ALARM_ID", -1)
            val label = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
            
            Log.d("AlarmReceiver", "Triggering alarm id: $alarmId")

            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                this.action = AlarmService.ACTION_START
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", label)
            }
            
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to start AlarmService in foreground", e)
            }
        } else if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("AlarmReceiver", "Device reboot finished. Rescheduling active alarms.")
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val alarmDao = db.alarmDao()
                    val scheduler = AlarmScheduler(context.applicationContext)
                    
                    // Retrieve alarms and reschedule active ones
                    alarmDao.getAllAlarms().collect { alarms ->
                        for (alarm in alarms) {
                            if (alarm.isEnabled) {
                                scheduler.schedule(alarm)
                            }
                        }
                        // Stop collecting since we only need the snapshot once
                        pendingResult.finish()
                    }
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error rescheduling alarms after boot", e)
                    pendingResult.finish()
                }
            }
        }
    }
}
