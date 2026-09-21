package com.weatherwatch.core

import com.weatherwatch.core.sync.Freshness
import com.weatherwatch.core.sync.SyncPolicy
import com.weatherwatch.core.sync.SyncTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPolicyTest {

    private val min = 60_000L
    private val hour = 3_600_000L

    @Test
    fun `freshness boundaries`() {
        assertEquals(Freshness.FRESH, SyncPolicy.classify(0))
        assertEquals(Freshness.FRESH, SyncPolicy.classify(hour - 1))
        assertEquals(Freshness.RECENT, SyncPolicy.classify(hour))
        assertEquals(Freshness.RECENT, SyncPolicy.classify(6 * hour - 1))
        assertEquals(Freshness.STALE, SyncPolicy.classify(6 * hour))
        assertEquals(Freshness.STALE, SyncPolicy.classify(24 * hour - 1))
        assertEquals(Freshness.VERY_STALE, SyncPolicy.classify(24 * hour))
    }

    @Test
    fun `night window wraps midnight`() {
        assertTrue(SyncPolicy.isNight(23))
        assertTrue(SyncPolicy.isNight(0))
        assertTrue(SyncPolicy.isNight(6))
        assertFalse(SyncPolicy.isNight(7))
        assertFalse(SyncPolicy.isNight(22))
    }

    @Test
    fun `manual refresh always goes through`() {
        // 用户点了刷新就必须真的去拉 —— 缓存再新、电量再低都不拦
        assertTrue(SyncPolicy.shouldSync(0, localHour = 3, batteryPct = 2, trigger = SyncTrigger.MANUAL))
    }

    @Test
    fun `low battery only blocks the automatic open refresh`() {
        assertFalse(SyncPolicy.shouldSync(10 * hour, 12, batteryPct = 10, trigger = SyncTrigger.APP_OPEN))
        assertTrue(SyncPolicy.shouldSync(10 * hour, 12, batteryPct = 10, trigger = SyncTrigger.MANUAL))
        assertTrue(SyncPolicy.shouldSync(10 * hour, 12, batteryPct = 50, trigger = SyncTrigger.APP_OPEN))
    }

    @Test
    fun `reopening the app within 45 min does not refetch`() {
        assertFalse(SyncPolicy.shouldSync(44 * min, localHour = 12, batteryPct = 80, trigger = SyncTrigger.APP_OPEN))
        assertTrue(SyncPolicy.shouldSync(45 * min, localHour = 12, batteryPct = 80, trigger = SyncTrigger.APP_OPEN))
    }

    @Test
    fun `night widens the interval to three hours`() {
        // 同样 45 分钟，夜里不放行
        assertFalse(SyncPolicy.shouldSync(45 * min, localHour = 2, batteryPct = 80, trigger = SyncTrigger.APP_OPEN))
        assertTrue(SyncPolicy.shouldSync(3 * hour, localHour = 2, batteryPct = 80, trigger = SyncTrigger.APP_OPEN))
    }
}
