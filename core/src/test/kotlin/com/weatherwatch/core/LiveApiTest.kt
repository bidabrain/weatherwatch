package com.weatherwatch.core

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.GeoPoint
import com.weatherwatch.core.model.WeatherIcon
import com.weatherwatch.core.net.HttpFetcher
import com.weatherwatch.core.net.HttpStatusException
import com.weatherwatch.core.source.openmeteo.GeocodingSource
import com.weatherwatch.core.source.openmeteo.OpenMeteoSource
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 真打 api.open-meteo.com，用来发现**线上 API 漂移**：
 * fixture 测试只能保证解析器对得上那份存档，保证不了 URL 参数在今天依然有效。
 *
 * 默认跳过（CI / 离线环境不应该因为网络抖动变红）。手动跑：
 *     .\gradlew.bat :core:test -Plive=true
 */
class LiveApiTest {

    private class JdkFetcher : HttpFetcher {
        override suspend fun get(url: String): String {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            try {
                val code = conn.responseCode
                val body = (if (code < 400) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText().orEmpty()
                if (code !in 200..299) throw HttpStatusException(code, body.take(200))
                return body
            } finally {
                conn.disconnect()
            }
        }
    }

    @Test
    fun `live open-meteo response still matches the parser`() {
        if (System.getProperty("ww.live") != "true") {
            println("LiveApiTest skipped (加 -Plive=true 开启)")
            return
        }

        val b = runBlocking {
            OpenMeteoSource(JdkFetcher()).fetch(
                City("hangzhou", "杭州", GeoPoint(30.2741, 120.1551)),
            )
        }

        assertEquals(7, b.daily.size, "daily 条数变了，检查 forecast_days")
        assertEquals(24, b.hourly.size, "hourly 条数变了，检查 forecast_hours")
        assertEquals(28800, b.tzOffsetSeconds, "时区解析异常")
        assertEquals("Asia/Shanghai", b.tzId)

        // 未知图标说明 WMO code 表出现了没覆盖的值
        assertTrue(b.current.icon != WeatherIcon.UNKNOWN, "current 天气码未识别")
        assertTrue(b.daily.none { it.icon == WeatherIcon.UNKNOWN }, "daily 出现未识别天气码")

        // 日期必须是杭州的今天 —— 这条能抓住时区回归
        val todayInShanghai = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        assertEquals(todayInShanghai, b.daily.first().dateIso, "daily 首日不是本地今天")

        assertTrue(b.current.uvIndex >= 0f)
        assertTrue(b.daily.all { it.sunriseUtc > 0 && it.sunsetUtc > it.sunriseUtc })

        println("LIVE OK  ${b.current.tempC}C ${b.current.text}  " +
            "today=${b.daily.first().dateIso} hi=${b.daily.first().hiC} lo=${b.daily.first().loC}")
    }

    @Test
    fun `live geocoding still returns chinese names for pinyin`() {
        if (System.getProperty("ww.live") != "true") {
            println("LiveApiTest(geocoding) skipped (加 -Plive=true 开启)")
            return
        }

        val cities = runBlocking { GeocodingSource(JdkFetcher()).search("hangzhou") }

        assertTrue(cities.isNotEmpty(), "搜索无结果，检查 geocoding 接口")
        val hz = cities.first()
        // language=zh 失效的话这里会变成 "Hangzhou"
        assertEquals("杭州", hz.name, "返回的不是中文名")
        assertTrue(hz.region?.contains("中国") == true, "region 缺国家: " + hz.region)
        assertEquals(30.3, hz.point.lat, 0.5)
        assertEquals(120.2, hz.point.lon, 0.5)

        // 搜不到时必须是空列表而不是抛异常
        val none = runBlocking { GeocodingSource(JdkFetcher()).search("zzzzqqqxxx") }
        assertTrue(none.isEmpty())

        println("LIVE GEOCODING OK  " + cities.joinToString { it.name + "(" + it.region + ")" })
    }
}
