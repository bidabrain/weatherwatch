package com.weatherwatch.core

import com.weatherwatch.core.model.WeatherIcon
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WmoTest {

    @Test
    fun `precipitation flag covers every wet icon`() {
        listOf(
            WeatherIcon.DRIZZLE, WeatherIcon.RAIN, WeatherIcon.RAIN_HEAVY,
            WeatherIcon.SHOWERS, WeatherIcon.SNOW, WeatherIcon.SNOW_SHOWERS,
            WeatherIcon.THUNDERSTORM, WeatherIcon.THUNDERSTORM_HAIL,
            WeatherIcon.FREEZING_RAIN, WeatherIcon.FREEZING_DRIZZLE,
        ).forEach { assertTrue(it.isPrecipitation, it.name) }

        listOf(
            WeatherIcon.CLEAR_DAY, WeatherIcon.CLEAR_NIGHT, WeatherIcon.OVERCAST,
            WeatherIcon.FOG, WeatherIcon.PARTLY_CLOUDY_NIGHT, WeatherIcon.UNKNOWN,
        ).forEach { assertFalse(it.isPrecipitation, it.name) }
    }
}
