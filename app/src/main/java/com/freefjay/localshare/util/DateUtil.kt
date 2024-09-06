package com.freefjay.localshare.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Date.format(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    val simpleDateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    return simpleDateFormat.format(this)
}

val weekDayMap = mapOf(
    1 to "一",
    2 to "二",
    3 to "三",
    4 to "四",
    5 to "吴",
    6 to "六",
    7 to "日"
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
    }${
        calendar.get(Calendar.HOUR)
    }时${
        calendar.get(Calendar.MINUTE)
    }分${
        calendar.get(Calendar.SECOND)
    }秒 周${
        weekDayMap[calendar.get(Calendar.DAY_OF_WEEK)]
    }"
}