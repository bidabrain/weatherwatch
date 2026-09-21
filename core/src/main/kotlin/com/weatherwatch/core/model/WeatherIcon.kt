package com.weatherwatch.core.model

/**
 * 归一化图标集合。**各数据源的原始 code 一律不进入这一层。**
 *
 * 理由：weather.com、和风、Open-Meteo 的 code 体系互不兼容，
 * 如果让原始 code 渗进 UI，换数据源时整套图标资源和判断分支都要重写。
 */
enum class WeatherIcon {
    CLEAR_DAY,
    CLEAR_NIGHT,
    MOSTLY_CLEAR_DAY,
    MOSTLY_CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    OVERCAST,
    FOG,
    DRIZZLE,
    FREEZING_DRIZZLE,
    RAIN_LIGHT,
    RAIN,
    RAIN_HEAVY,
    FREEZING_RAIN,
    SHOWERS,
    SNOW_LIGHT,
    SNOW,
    SNOW_HEAVY,
    SNOW_GRAINS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    THUNDERSTORM_HAIL,
    UNKNOWN,
    ;

    val isPrecipitation: Boolean
        get() = this in PRECIPITATION

    private companion object {
        val PRECIPITATION = setOf(
            DRIZZLE, FREEZING_DRIZZLE, RAIN_LIGHT, RAIN, RAIN_HEAVY, FREEZING_RAIN,
            SHOWERS, SNOW_LIGHT, SNOW, SNOW_HEAVY, SNOW_GRAINS, SNOW_SHOWERS,
            THUNDERSTORM, THUNDERSTORM_HAIL,
        )
    }
}
