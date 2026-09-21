package com.weatherwatch.watch

import com.weatherwatch.watch.ui.relativeAge
import com.weatherwatch.watch.ui.tempLabel
import com.weatherwatch.watch.ui.weekdayLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {

    private val min = 60_000L
    private val hour = 60 * min

    @Test
    fun `relative age switches unit at the right boundaries`() {
        assertEquals("刚刚", relativeAge(0))
        assertEquals("刚刚", relativeAge(min - 1))
        assertEquals("1 分钟前", relativeAge(min))
        assertEquals("59 分钟前", relativeAge(59 * min))
        assertEquals("1 小时前", relativeAge(hour))
        assertEquals("23 小时前", relativeAge(23 * hour))
        assertEquals("1 天前", relativeAge(24 * hour))
        assertEquals("3 天前", relativeAge(3 * 24 * hour + 5 * hour))
    }

    @Test
    fun `weekday label prefers today and tomorrow`() {
        // 2026-09-22 是周二
        assertEquals("今天", weekdayLabel("2026-09-22", "2026-09-22"))
        assertEquals("明天", weekdayLabel("2026-09-23", "2026-09-22"))
        assertEquals("周四", weekdayLabel("2026-09-24", "2026-09-22"))
        assertEquals("周日", weekdayLabel("2026-09-27", "2026-09-22"))
        assertEquals("周一", weekdayLabel("2026-09-28", "2026-09-22"))
    }

    @Test
    fun `weekday label survives garbage input`() {
        // 解析失败也不能崩 —— 表上一次崩溃就是白屏
        assertEquals("not-a", weekdayLabel("not-a", "2026-09-22"))
    }

    @Test
    fun `temperature rounds to integer`() {
        assertEquals("23", tempLabel(23.4f))
        assertEquals("24", tempLabel(23.5f))
        assertEquals("-3", tempLabel(-2.6f))
        assertEquals("0", tempLabel(-0.2f))
    }
}
