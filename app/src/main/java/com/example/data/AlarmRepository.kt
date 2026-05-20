package com.example.data

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmDao: AlarmDao) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun getAlarmById(id: Int): Alarm? = alarmDao.getAlarmById(id)

    suspend fun insert(alarm: Alarm): Long = alarmDao.insertAlarm(alarm)

    suspend fun update(alarm: Alarm) = alarmDao.updateAlarm(alarm)

    suspend fun delete(alarm: Alarm) = alarmDao.deleteAlarm(alarm)
}
