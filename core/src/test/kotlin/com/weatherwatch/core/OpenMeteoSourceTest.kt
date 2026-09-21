package com.weatherwatch.core

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.GeoPoint
import com.weatherwatch.core.model.WeatherIcon
import com.weatherwatch.core.net.HttpFetcher
import com.weatherwatch.core.source.openmeteo.OpenMeteoSource
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 基于 2026-09-22 从 api.open-meteo.com 抓的真实响应（杭州）。
 * 用真响应而不是手搓 JSON：字段名、null 位置、时间语义都以线上为准。
 */
class OpenMeteoSourceTest {

    private val hangzhou = City("hangzhou", "杭州", GeoPoint(30.2741, 120.1551))

    private fun fixture(): String =
        javaClass.getResourceAsStream("/openmeteo_hangzhou.json")!!
            .readBytes().decodeToString()

    private class FakeFetcher(private val body: String) : HttpFetcher {
        var lastUrl: String? = null
        override suspend fun get(url: String): String {
            lastUrl = url
            return body
        }
    }

    private fun parse() = runBlocking {
        OpenMeteoSource(FakeFetcher(fixture()), now = { 1_790_020_900_000L }).fetch(hangzhou)
    }

    @Test
    fun `bundle carries source city and timezone metadata`() {
        val b = parse()
        assertEquals("hangzhou", b.cityId)
        assertEquals("open-meteo", b.sourceId)
        assertEquals(28800, b.tzOffsetSeconds)
        assertEquals("Asia/Shanghai", b.tzId)
        assertEquals(1_790_020_900_000L, b.fetchedAtUtcMillis)
    }

    @Test
    fun `current block is mapped field by field`() {
        val c = parse().current
        assertEquals(1_790_020_800L, c.timeUtc)
        assertEquals(23.5f, c.tempC, 0.01f)
        assertEquals(26.9f, c.feelsLikeC, 0.01f)
        assertEquals(85, c.humidityPct)
        assertEquals(1013.2f, c.pressureHpa, 0.01f)
        assertEquals(6.1f, c.windKph, 0.01f)
        assertEquals(45, c.windDirDeg)
        assertEquals(0f, c.uvIndex, 0.01f)
        // weather_code=3 -> 阴，且阴天没有昼夜变体
        assertEquals(WeatherIcon.OVERCAST, c.icon)
        assertEquals("阴", c.text)
        // Open-Meteo 的 current 不给能见度，必须是 null 而不是 0
        assertEquals(null, c.visibilityKm)
    }

    /**
     * 这是整个解析里最容易错的一处。
     * daily.time[0] 的 UTC 值是 2026-09-21T16:00Z，直接按 UTC 取日期会得到 09-21，
     * 整个七日预报会错一天。必须先加 utc_offset_seconds。
     */
    @Test
    fun `daily dates are in city local timezone not utc`() {
        val b = parse()
        assertEquals(7, b.daily.size)
        assertEquals(
            listOf(
                "2026-09-22", "2026-09-23", "2026-09-24", "2026-09-25",
                "2026-09-26", "2026-09-27", "2026-09-28",
            ),
            b.daily.map { it.dateIso },
        )
    }

    @Test
    fun `sunrise and sunset are real utc instants`() {
        val b = parse()
        val d0 = b.daily[0]
        // Open-Meteo 的日出日落精确到秒，这里只关心时分
        fun localHhmm(utc: Long) =
            LocalDateTime.ofEpochSecond(utc + b.tzOffsetSeconds, 0, ZoneOffset.UTC)
                .toLocalTime()
                .let { "%02d:%02d".format(it.hour, it.minute) }
        // 杭州 9/22：日出 05:47，日落 17:56
        assertEquals("05:47", localHhmm(d0.sunriseUtc))
        assertEquals("17:56", localHhmm(d0.sunsetUtc))
    }

    @Test
    fun `daily values and icons are mapped`() {
        val b = parse()
        val d0 = b.daily[0]
        assertEquals(31.1f, d0.hiC, 0.01f)
        assertEquals(22.9f, d0.loC, 0.01f)
        assertEquals(2, d0.popPct)
        assertEquals(WeatherIcon.OVERCAST, d0.icon)

        assertEquals(WeatherIcon.DRIZZLE, b.daily[3].icon)
        assertEquals(WeatherIcon.THUNDERSTORM, b.daily[4].icon)
        assertEquals("雷阵雨", b.daily[4].text)
        assertEquals(86, b.daily[4].popPct)
        assertEquals(WeatherIcon.THUNDERSTORM_HAIL, b.daily[6].icon)
    }

    /** 同一个 weather_code=0，白天该是太阳，夜里该是月亮。 */
    @Test
    fun `hourly icons follow is_day flag`() {
        val h = parse().hourly
        assertEquals(24, h.size)
        assertEquals(1_790_020_800L, h[0].timeUtc)
        assertEquals(23.5f, h[0].tempC, 0.01f)

        assertEquals(WeatherIcon.MOSTLY_CLEAR_NIGHT, h[1].icon) // code=1 is_day=0
        assertEquals(WeatherIcon.PARTLY_CLOUDY_DAY, h[2].icon)  // code=2 is_day=1
        assertEquals(WeatherIcon.CLEAR_DAY, h[7].icon)          // code=0 is_day=1
        assertEquals(WeatherIcon.CLEAR_NIGHT, h[16].icon)       // code=0 is_day=0
    }

    /** 晚上看明天的预报，日视图显示月亮是错的。 */
    @Test
    fun `daily icons always use day variant`() {
        val b = runBlocking {
            val json = fixture().replace("\"weather_code\":[3,3,3,51,95,95,96]", "\"weather_code\":[0,0,0,0,0,0,0]")
            OpenMeteoSource(FakeFetcher(json), now = { 0L }).fetch(hangzhou)
        }
        assertTrue(b.daily.all { it.icon == WeatherIcon.CLEAR_DAY }, "daily 不应出现夜间图标")
    }

    @Test
    fun `url carries the params the parser depends on`() {
        val f = FakeFetcher(fixture())
        runBlocking { OpenMeteoSource(f).fetch(hangzhou) }
        val url = f.lastUrl!!
        // 去掉任何一个，解析结果都会静默变错而不是报错
        assertTrue("timeformat=unixtime" in url, url)
        assertTrue("timezone=auto" in url, url)
        assertTrue("forecast_hours=24" in url, url)
        assertTrue("forecast_days=7" in url, url)
        assertTrue("is_day" in url, url)
        assertTrue("uv_index_max" in url, url)
    }

    /**
     * 德语等 Locale 的小数点是逗号，String.format 不锁 Locale 的话
     * 经纬度会变成 "30,2741"，服务端直接 400。手表系统语言不可控，必须防。
     */
    @Test
    fun `coordinates use dot regardless of default locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val f = FakeFetcher(fixture())
            runBlocking { OpenMeteoSource(f).fetch(hangzhou) }
            val url = f.lastUrl!!
            assertTrue("latitude=30.2741" in url, url)
            assertTrue("longitude=120.1551" in url, url)
        } finally {
            Locale.setDefault(saved)
        }
    }

    /** 预报区间边缘 Open-Meteo 会返回 null，解析不能崩。 */
    @Test
    fun `null values in arrays fall back instead of crashing`() {
        val json = """
            {"utc_offset_seconds":28800,"timezone":"Asia/Shanghai",
             "current":{"time":100,"weather_code":0,"is_day":1},
             "hourly":{"time":[100,200],"temperature_2m":[null,21.0],
                       "weather_code":[null,0],"precipitation_probability":[null,5],"is_day":[1,null]},
             "daily":{"time":[100],"weather_code":[null],"temperature_2m_max":[null],
                      "temperature_2m_min":[null],"precipitation_probability_max":[null],
                      "sunrise":[null],"sunset":[null],"uv_index_max":[null]}}
        """.trimIndent()
        val b = runBlocking { OpenMeteoSource(FakeFetcher(json), now = { 0L }).fetch(hangzhou) }

        assertEquals(2, b.hourly.size)
        assertEquals(0f, b.hourly[0].tempC, 0.01f)
        assertEquals(WeatherIcon.UNKNOWN, b.hourly[0].icon)
        assertEquals(WeatherIcon.CLEAR_DAY, b.hourly[1].icon) // is_day 缺失时默认白天
        assertEquals(1, b.daily.size)
        assertEquals(WeatherIcon.UNKNOWN, b.daily[0].icon)
        assertEquals(0L, b.daily[0].sunriseUtc)
        // current 缺 apparent_temperature 时回落到实际气温
        assertEquals(b.current.tempC, b.current.feelsLikeC)
    }
}
