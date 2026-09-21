package com.weatherwatch.watch.ui

import java.time.LocalDate

/** 刚刚 / 8 分钟前 / 3 小时前 / 2 天前 */
fun relativeAge(ageMillis: Long): String {
    val min = ageMillis / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> min.toString() + " 分钟前"
        min < 60 * 24 -> (min / 60).toString() + " 小时前"
        else -> (min / (60 * 24)).toString() + " 天前"
    }
}

private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 头两天显示今天/明天，其余显示星期 —— 表盘上星期比日期好认。 */
fun weekdayLabel(dateIso: String, todayIso: String): String = try {
    val d = LocalDate.parse(dateIso)
    val today = LocalDate.parse(todayIso)
    when (d.toEpochDay() - today.toEpochDay()) {
        0L -> "今天"
        1L -> "明天"
        else -> WEEKDAYS[d.dayOfWeek.value - 1]
    }
} catch (e: Throwable) {
    dateIso.takeLast(5)
}

fun tempLabel(c: Float): String = Math.round(c).toString()
