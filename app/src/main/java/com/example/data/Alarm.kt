package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Int = 0 // Bitmask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64
) {
    fun isRepeating(): Boolean = repeatDays != 0

    fun isDayEnabled(dayOfWeek: Int): Boolean {
        // Calendar day of week: Calendar.MONDAY is 2, Calendar.SUNDAY is 1, etc.
        // Let's map Calendar.MONDAY (2) -> 1, MONDAY..SATURDAY(7)->6, SUNDAY(1) -> 7
        val bitPosition = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }
        return (repeatDays and (1 shl bitPosition)) != 0
    }
}
