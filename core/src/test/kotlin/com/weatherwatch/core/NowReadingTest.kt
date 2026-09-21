package com.weatherwatch.core

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.GeoPoint
import com.weatherwatch.core.model.NowSource
import com.weatherwatch.core.model.OBSERVED_MAX_AGE_MS
import com.weatherwatch.core.model.WeatherIcon
import com.weatherwatch.core.model.nowReading
import com.weatherwatch.core.net.HttpFetcher
import com.weatherwatch.core.source.openmeteo.OpenMeteoSource
import com.weatherwatch.core.sync.Freshness
import com.weatherwatch.core.sync.SyncPolicy
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 主屏"现在"的降级阶梯。
 *
 * 真实场景：手表两天没联网。缓存里的 current 是两天前凌晨的实测值，
 * 但同一份缓存里还躺着 24 小时逐时预报和 7 天日预报。
 * 主屏应该用得上它们，而不是一直显示两天前凌晨的温度配月亮图标。
 */
class NowReadingTest {

    /** 1790020800 UTC = 杭州 2026-09-22 04:00 */
    private val fetchedAt = 1_790_020_800_000L
    private val hour = 3_600_000L

    private fun bundle() = runBlocking {
        val body = javaClass.getResourceAsStream("/openmeteo_hangzhou.json")!!
            .readBytes().decodeToString()
        OpenMeteoSource(HttpFetcher { body }, now = { fetchedAt })
            .fetch(City("hangzhou", "杭州", GeoPoint(30.2741, 120.1551)))
    }

    @Test
    fun `fresh cache uses the real observation`() {
        val n = bundle().nowReading(fetchedAt + 30 * 60_000)
        assertEquals(NowSource.OBSERVED, n.source)
        assertEquals(23.5f, n.tempC, 0.01f)
        assertEquals(26.9f, n.feelsLikeC!!, 0.01f)
        assertEquals(WeatherIcon.OVERCAST, n.icon)
        assertNull(n.rangeLoC)
    }

    @Test
    fun `after an hour it switches to the hourly forecast`() {
        val n = bundle().nowReading(fetchedAt + 5 * hour)
        assertEquals(NowSource.HOURLY_FORECAST, n.source)
        // 缓存里 09:00 这一小时的预报值，而不是 04:00 的实测值
        assertEquals(25.9f, n.tempC, 0.01f)
        assertEquals("阴", n.text)
        // 体感只有实测有，这一档必须为空
        assertNull(n.feelsLikeC)
    }

    /** 图标跟着那一小时的昼夜属性走 —— 这是"白天不该显示月亮"的反面保证 */
    @Test
    fun `hourly forecast carries the right day night icon`() {
        val b = bundle()

        val daytime = b.nowReading(fetchedAt + 9 * hour) // 本地 13:00
        assertEquals(WeatherIcon.PARTLY_CLOUDY_DAY, daytime.icon)

        val nighttime = b.nowReading(fetchedAt + 14 * hour) // 本地 18:00 之后
        assertEquals(WeatherIcon.MOSTLY_CLEAR_NIGHT, nighttime.icon)
        assertEquals(28.2f, nighttime.tempC, 0.01f)
    }

    @Test
    fun `beyond the hourly window it falls back to today's daily range`() {
        // +30h = 本地 09-23 10:00，逐时数据只覆盖到 09-23 03:00
        val n = bundle().nowReading(fetchedAt + 30 * hour)
        assertEquals(NowSource.DAILY_FORECAST, n.source)
        // 09-23 的日预报区间
        assertEquals(23.3f, n.rangeLoC!!, 0.01f)
        assertEquals(32.5f, n.rangeHiC!!, 0.01f)
        assertNull(n.feelsLikeC)
    }

    @Test
    fun `when today falls out of the cache it shows the last known value`() {
        // 缓存只到 09-28，这里已经是 09-30
        val n = bundle().nowReading(fetchedAt + 8 * 24 * hour)
        assertEquals(NowSource.LAST_KNOWN, n.source)
        assertEquals(23.5f, n.tempC, 0.01f)
        assertNull(n.feelsLikeC)
    }

    @Test
    fun `observation window ends exactly at one hour`() {
        val b = bundle()
        assertEquals(NowSource.OBSERVED, b.nowReading(fetchedAt + hour - 1).source)
        assertEquals(NowSource.HOURLY_FORECAST, b.nowReading(fetchedAt + hour).source)
    }

    /** 两个常量代表同一个概念，必须一起改，别悄悄漂移 */
    @Test
    fun `observed window matches the FRESH boundary`() {
        assertEquals(Freshness.FRESH, SyncPolicy.classify(OBSERVED_MAX_AGE_MS - 1))
        assertEquals(Freshness.RECENT, SyncPolicy.classify(OBSERVED_MAX_AGE_MS))
    }

    @Test
    fun `every rung yields a usable icon and text`() {
        val b = bundle()
        listOf(0L, 5 * hour, 30 * hour, 8 * 24 * hour).forEach { age ->
            val n = b.nowReading(fetchedAt + age)
            assertNotNull(n.icon)
            assertEquals(true, n.text.isNotBlank(), "age=" + age + " 文案为空")
        }
    }
}
