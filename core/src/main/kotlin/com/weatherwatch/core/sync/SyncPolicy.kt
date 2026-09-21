package com.weatherwatch.core.sync

/** 缓存新鲜度分级。离线 App 必须把这个诚实地显示出来。 */
enum class Freshness {
    /** < 1h，正常显示 */
    FRESH,

    /** 1~6h，底部灰字提示 */
    RECENT,

    /** 6~24h，顶部橙条 + 橙字"数据较旧" */
    STALE,

    /** > 24h，温度降为灰色"已离线 N 天" */
    VERY_STALE,
}

/**
 * 同步触发源。
 *
 * **本 App 不做后台同步** —— 没有 AlarmManager、没有 WorkManager、没有开机自启。
 * 只有这两个触发点，两者都由用户的动作直接引发。
 */
enum class SyncTrigger {
    /** 用户点击屏幕刷新：永远放行，这是确定性出口 */
    MANUAL,

    /** 打开 App 的那一刻：受节流约束，避免频繁开关时重复请求 */
    APP_OPEN,
}

/**
 * 同步节流策略，全部是纯函数，便于单测。
 *
 * 设计前提（来自 M0 实测）：手表经蓝牙隧道上网，一次请求 RTT 约 0.5~1s，
 * 整包 2.2KB。代价不在流量而在唤醒开销，所以按时间节流而非按流量。
 */
object SyncPolicy {

    /** 白天：打开 App 时，缓存超过 45 分钟才重新拉取 */
    const val DAY_INTERVAL_MS = 45 * 60_000L

    /** 夜里没人看天气，放宽到 3 小时 */
    const val NIGHT_INTERVAL_MS = 3 * 60 * 60_000L

    /** 夜间时段 [NIGHT_START_HOUR, NIGHT_END_HOUR)，跨零点 */
    const val NIGHT_START_HOUR = 23
    const val NIGHT_END_HOUR = 7

    /** 低于此电量，打开 App 不再自动刷新；手动点击仍然放行 */
    const val LOW_BATTERY_PCT = 15

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS

    fun classify(ageMillis: Long): Freshness = when {
        ageMillis < HOUR_MS -> Freshness.FRESH
        ageMillis < 6 * HOUR_MS -> Freshness.RECENT
        ageMillis < DAY_MS -> Freshness.STALE
        else -> Freshness.VERY_STALE
    }

    fun isNight(localHour: Int): Boolean =
        localHour >= NIGHT_START_HOUR || localHour < NIGHT_END_HOUR

    fun intervalFor(localHour: Int): Long =
        if (isNight(localHour)) NIGHT_INTERVAL_MS else DAY_INTERVAL_MS

    fun shouldSync(
        ageMillis: Long,
        localHour: Int,
        batteryPct: Int,
        trigger: SyncTrigger,
    ): Boolean {
        if (trigger == SyncTrigger.MANUAL) return true
        if (batteryPct < LOW_BATTERY_PCT) return false
        return ageMillis >= intervalFor(localHour)
    }
}
