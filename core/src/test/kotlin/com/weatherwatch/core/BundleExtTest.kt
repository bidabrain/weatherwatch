package com.weatherwatch.core

import com.weatherwatch.core.model.ageMillis
import com.weatherwatch.core.model.localHour
import com.weatherwatch.core.model.localToday
import com.weatherwatch.core.model.upcomingDays
import com.weatherwatch.core.model.upcomingHours
import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.GeoPoint
import com.weatherwatch.core.net.HttpFetcher
import com.weatherwatch.core.source.openmeteo.OpenMeteoSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class BundleExtTest {

    private fun bundle() = runBlocking {
        val body = javaClass.getResourceAsStream("/openmeteo_hangzhou.json")!!
            .readBytes().decodeToString()
        OpenMeteoSource(HttpFetcher { body }, now = { FETCHED_AT })
            .fetch(City("hangzhou", "杭州", GeoPoint(30.2741, 120.1551)))
    }

    @Test
    fun `age never goes negative on clock skew`() {
        val b = bundle()
        assertEquals(0L, b.ageMillis(FETCHED_AT - 60_000))
        assertEquals(60_000L, b.ageMillis(FETCHED_AT + 60_000))
    }

    @Test
    fun `local hour uses city timezone not device timezone`() {
        val b = bundle()
        // 1790020800 UTC = 2026-09-21 20:00Z = 杭州 2026-09-22 04:00
        assertEquals(4, b.localHour(1_790_020_800_000L))
        assertEquals("2026-09-22", b.localToday(1_790_020_800_000L))
    }

    @Test
    fun `stale cache does not render past hours`() {
        val b = bundle()
        assertEquals(24, b.upcomingHours(FETCHED_AT).size)

        // 缓存放了 5 小时后再看，前 5 条已经过去
        val after5h = FETCHED_AT + 5 * 3_600_000L
        val rest = b.upcomingHours(after5h)
        assertEquals(19, rest.size)
        assertEquals(1_790_020_800L + 5 * 3600, rest.first().timeUtc)
    }

    @Test
    fun `upcoming days drops yesterday when cache is old`() {
        val b = bundle()
        assertEquals(7, b.upcomingDays(FETCHED_AT).size)

        // 隔了两天没同步：前两天应当被丢掉
        val after2d = FETCHED_AT + 2 * 24 * 3_600_000L
        val days = b.upcomingDays(after2d)
        assertEquals(5, days.size)
        assertEquals("2026-09-24", days.first().dateIso)
    }

    private companion object {
        /** 1790020800 UTC = 杭州 2026-09-22 04:00 */
        const val FETCHED_AT = 1_790_020_800_000L
    }
}
