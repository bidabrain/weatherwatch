package com.weatherwatch.core.model

import java.time.LocalDateTime
import java.time.ZoneOffset

/** 缓存年龄。UI 用它显示"更新于 N 分钟前"，同步策略用它判断是否该拉新数据。 */
fun WeatherBundle.ageMillis(nowUtcMillis: Long): Long =
    (nowUtcMillis - fetchedAtUtcMillis).coerceAtLeast(0L)

/** 当前时刻在**城市本地时区**的小时数 (0..23)，用于夜间降频等策略。 */
fun WeatherBundle.localHour(nowUtcMillis: Long): Int =
    LocalDateTime.ofEpochSecond(nowUtcMillis / 1000 + tzOffsetSeconds, 0, ZoneOffset.UTC).hour

/** 城市本地时区的今天，ISO yyyy-MM-dd。 */
fun WeatherBundle.localToday(nowUtcMillis: Long): String =
    LocalDateTime.ofEpochSecond(nowUtcMillis / 1000 + tzOffsetSeconds, 0, ZoneOffset.UTC)
        .toLocalDate().toString()

/**
 * 还未过去的逐时数据。
 *
 * 缓存可能已经放了几个小时，直接渲染 [hourly] 会把过去的时间画进曲线。
 * 当前所在的那一小时算"未来"（它还没走完）。
 */
fun WeatherBundle.upcomingHours(nowUtcMillis: Long, limit: Int = 24): List<Hour> {
    val nowSec = nowUtcMillis / 1000
    return hourly.asSequence()
        .filter { it.timeUtc + 3600 > nowSec }
        .take(limit)
        .toList()
}

/** 还未过去的日预报，今天算在内。 */
fun WeatherBundle.upcomingDays(nowUtcMillis: Long, limit: Int = 7): List<Day> {
    val today = localToday(nowUtcMillis)
    return daily.asSequence()
        .filter { it.dateIso >= today }
        .take(limit)
        .toList()
}
