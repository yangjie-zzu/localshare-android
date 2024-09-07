package com.freefjay.localshare.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Date.format(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    val simpleDateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    return simpleDateFormat.format(this)
}

val weekDayMap = mapOf(
    1 to "日",
    2 to "一",
    3 to "二",
    4 to "三",
    5 to "四",
    6 to "五",
    7 to "六"
)

fun Date.friendly(): String {
    val now = Date()
    val nowCalendar = Calendar.getInstance().also { it.time = now }
    val nowYear = nowCalendar.get(Calendar.YEAR)
    val nowMonth = nowCalendar.get(Calendar.MONTH)
    val nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH)
    val calendar = Calendar.getInstance().also { it.time = this }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return "${
        if (year == nowYear) "" else "${year}年"
    }${
        if (year == nowYear && month == nowMonth) "" else "${month + 1}月"
    }${
        if (year == nowYear && month == nowMonth && day == nowDay) "" else "${day}日"
    }周${
        weekDayMap[kotlin.run {
            val weekDay = calendar.get(Calendar.DAY_OF_WEEK)
            if (calendar.firstDayOfWeek == Calendar.SUNDAY) {
                if (weekDay == 1) {
                    7
                } else {
                    weekDay - 1
                }
            } else {
                weekDay
            }
        }]
    }${
        calendar.get(Calendar.HOUR_OF_DAY)
    }:${
        calendar.get(Calendar.MINUTE)
    }:${
        calendar.get(Calendar.SECOND)
    }"
}