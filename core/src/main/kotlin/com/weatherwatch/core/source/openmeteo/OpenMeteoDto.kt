package com.weatherwatch.core.source.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 所有数值数组的元素都声明成可空：Open-Meteo 在预报区间边缘偶尔会返回 null。
// 手表上一次解析崩溃 = 用户看到白屏，防御成本几乎为零，值得。

@Serializable
internal data class OmResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val timezone: String? = null,
    val current: OmCurrent = OmCurrent(),
    val hourly: OmHourly = OmHourly(),
    val daily: OmDaily = OmDaily(),
)

@Serializable
internal data class OmCurrent(
    val time: Long = 0,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparent: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("is_day") val isDay: Int = 1,
    @SerialName("weather_code") val weatherCode: Int = -1,
    @SerialName("surface_pressure") val pressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDir: Int? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
)

@Serializable
internal data class OmHourly(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("precipitation_probability") val pop: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
)

@Serializable
internal data class OmDaily(
    val time: List<Long> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val tMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val tMin: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val popMax: List<Int?> = emptyList(),
    val sunrise: List<Long?> = emptyList(),
    val sunset: List<Long?> = emptyList(),
    @SerialName("uv_index_max") val uvMax: List<Double?> = emptyList(),
)
