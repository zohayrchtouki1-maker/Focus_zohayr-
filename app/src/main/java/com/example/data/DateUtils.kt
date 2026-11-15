package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val offset = cal.timeZone.getOffset(cal.timeInMillis)
        return (cal.timeInMillis + offset) / MILLIS_PER_DAY
    }

    fun epochDayFromMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val offset = cal.timeZone.getOffset(millis)
        return (millis + offset) / MILLIS_PER_DAY
    }

    fun millisFromEpochDay(epochDay: Long, hour: Int = 9, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        val utcMillis = epochDay * MILLIS_PER_DAY
        val offset = cal.timeZone.getOffset(utcMillis)
        cal.timeInMillis = utcMillis - offset
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun formatEpochDay(epochDay: Long, isArabic: Boolean): String {
        val today = todayEpochDay()
        return when (epochDay) {
            today -> if (isArabic) "اليوم" else "Today"
            today + 1 -> if (isArabic) "غداً" else "Tomorrow"
            today - 1 -> if (isArabic) "أمس" else "Yesterday"
            else -> {
                val millis = millisFromEpochDay(epochDay)
                val locale = if (isArabic) Locale("ar") else Locale.ENGLISH
                SimpleDateFormat("d MMM", locale).format(Date(millis))
            }
        }
    }

    fun formatDateTime(millis: Long, isArabic: Boolean): String {
        val locale = if (isArabic) Locale("ar") else Locale.ENGLISH
        return SimpleDateFormat("d MMM • HH:mm", locale).format(Date(millis))
    }

    fun formatShortDayLabel(epochDay: Long, isArabic: Boolean): String {
        val millis = millisFromEpochDay(epochDay)
        val locale = if (isArabic) Locale("ar") else Locale.ENGLISH
        return SimpleDateFormat("EEE", locale).format(Date(millis))
    }

    fun formatTimeOnly(millis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(millis))
    }
}
