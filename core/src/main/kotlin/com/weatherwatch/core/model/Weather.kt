package com.weatherwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class City(
    val id: String,
    val name: String,
    val point: GeoPoint,
    /** 省份·国家，仅用于搜索结果消歧。"杭州"在浙江和四川各有一个。 */
    val region: String? = null,
)

/**
 * 一次同步的完整快照。整体替换，不做增量合并 —— 简单，且不可能出现半新半旧的脏数据。
 *
 * 所有时间戳都是 UTC 毫秒/秒，绝不存本地时间。
 * 展示时用 [tzOffsetSeconds] 换算成**城市所在时区**的时间，
 * 而不是手表时区 —— 否则看外地城市的日出时间会是错的。
 */
@Serializable
data class WeatherBundle(
    val cityId: String,
    val sourceId: String,
    /** 拉取成功的时刻，UTC 毫秒。用于算"更新于 N 分钟前"和新鲜度分级。 */
    val fetchedAtUtcMillis: Long,
    val tzOffsetSeconds: Int,
    val tzId: String?,
    val current: Current,
    val hourly: List<Hour>,
    val daily: List<Day>,
    val alerts: List<Alert> = emptyList(),
)

@Serializable
data class Current(
    /** 观测时刻，UTC 秒 */
    val timeUtc: Long,
    val tempC: Float,
    val feelsLikeC: Float,
    val icon: WeatherIcon,
    val text: String,
    val humidityPct: Int,
    val windKph: Float,
    val windDirDeg: Int,
    val pressureHpa: Float,
    val uvIndex: Float,
    /** Open-Meteo 的 current 不含能见度，故可空。换源后可能有值。 */
    val visibilityKm: Float? = null,
)

@Serializable
data class Hour(
    val timeUtc: Long,
    val tempC: Float,
    val icon: WeatherIcon,
    val popPct: Int,
    /** 默认值保证老快照升级后仍能解码，不会让用户丢缓存 */
    val text: String = "",
)

@Serializable
data class Day(
    /** 该日在城市本地时区的日期，ISO yyyy-MM-dd */
    val dateIso: String,
    /** 该日本地零点对应的 UTC 秒，用于排序 */
    val startUtc: Long,
    val hiC: Float,
    val loC: Float,
    val icon: WeatherIcon,
    val text: String,
    val popPct: Int,
    val uvIndexMax: Float,
    val sunriseUtc: Long,
    val sunsetUtc: Long,
)

@Serializable
data class Alert(
    val title: String,
    val severity: Int,
    val startUtc: Long,
    val endUtc: Long,
)
