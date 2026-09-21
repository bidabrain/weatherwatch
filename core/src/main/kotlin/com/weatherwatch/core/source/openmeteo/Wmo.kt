package com.weatherwatch.core.source.openmeteo

import com.weatherwatch.core.model.WeatherIcon

/**
 * WMO 4677 天气现象码 -> 本 App 的归一化图标 / 中文描述。
 *
 * Open-Meteo 只给 code 不给文案，中文描述必须本地出。
 * 好处是措辞完全可控，不会出现各家 API 中译不统一的问题。
 */
internal object Wmo {

    fun icon(code: Int, isDay: Boolean): WeatherIcon = when (code) {
        0 -> if (isDay) WeatherIcon.CLEAR_DAY else WeatherIcon.CLEAR_NIGHT
        1 -> if (isDay) WeatherIcon.MOSTLY_CLEAR_DAY else WeatherIcon.MOSTLY_CLEAR_NIGHT
        2 -> if (isDay) WeatherIcon.PARTLY_CLOUDY_DAY else WeatherIcon.PARTLY_CLOUDY_NIGHT
        3 -> WeatherIcon.OVERCAST
        45, 48 -> WeatherIcon.FOG
        51, 53, 55 -> WeatherIcon.DRIZZLE
        56, 57 -> WeatherIcon.FREEZING_DRIZZLE
        61 -> WeatherIcon.RAIN_LIGHT
        63 -> WeatherIcon.RAIN
        65 -> WeatherIcon.RAIN_HEAVY
        66, 67 -> WeatherIcon.FREEZING_RAIN
        71 -> WeatherIcon.SNOW_LIGHT
        73 -> WeatherIcon.SNOW
        75 -> WeatherIcon.SNOW_HEAVY
        77 -> WeatherIcon.SNOW_GRAINS
        80, 81, 82 -> WeatherIcon.SHOWERS
        85, 86 -> WeatherIcon.SNOW_SHOWERS
        95 -> WeatherIcon.THUNDERSTORM
        96, 99 -> WeatherIcon.THUNDERSTORM_HAIL
        else -> WeatherIcon.UNKNOWN
    }

    fun textZh(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "晴间多云"
        2 -> "多云"
        3 -> "阴"
        45 -> "雾"
        48 -> "雾凇"
        51 -> "毛毛雨"
        53 -> "细雨"
        55 -> "密集细雨"
        56 -> "冻毛毛雨"
        57 -> "强冻毛毛雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66 -> "冻雨"
        67 -> "强冻雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "米雪"
        80 -> "阵雨"
        81 -> "强阵雨"
        82 -> "暴雨"
        85 -> "阵雪"
        86 -> "强阵雪"
        95 -> "雷阵雨"
        96 -> "雷阵雨伴冰雹"
        99 -> "强雷阵雨伴冰雹"
        else -> "未知"
    }
}
