package com.weatherwatch.core.model

/** 主屏"现在"这个数字是从哪来的。界面必须据此标注，拿预报冒充实况是不能接受的。 */
enum class NowSource {
    /** 抓取时的实测值。只在缓存足够新时可用。 */
    OBSERVED,

    /** 缓存里覆盖当前这一小时的逐时预报。 */
    HOURLY_FORECAST,

    /** 逐时数据已耗尽，退到今天的日预报（只有区间，没有此刻温度）。 */
    DAILY_FORECAST,

    /** 连今天都不在缓存里了，只能显示最后一次已知值。 */
    LAST_KNOWN,
}

data class NowReading(
    val source: NowSource,
    val icon: WeatherIcon,
    val text: String,
    /** 单值温度。[rangeLoC] / [rangeHiC] 非空时界面显示区间，忽略此值。 */
    val tempC: Float,
    val rangeLoC: Float? = null,
    val rangeHiC: Float? = null,
    /** 体感只有实测值有。拿旧体感配新气温是造假，所以其余档位一律为空。 */
    val feelsLikeC: Float? = null,
)

/** 超过这个年龄就不再把 current 当作"实况"。与 Freshness.FRESH 的边界一致。 */
const val OBSERVED_MAX_AGE_MS: Long = 3_600_000L

/**
 * 从缓存里挑出最能代表"现在"的读数，按可用性降级。
 *
 * 动机：`current` 是抓取那一刻的实测值，永远冻结。缓存放两天后，
 * 主屏会拿两天前凌晨的温度配月亮图标当作此刻天气，而缓存里
 * 明明还躺着 24 小时逐时预报和 7 天日预报没被用。
 *
 * 每一档的来源都会通过 [NowReading.source] 暴露给界面去标注。
 */
fun WeatherBundle.nowReading(nowUtcMillis: Long): NowReading {
    if (ageMillis(nowUtcMillis) < OBSERVED_MAX_AGE_MS) {
        return NowReading(
            source = NowSource.OBSERVED,
            icon = current.icon,
            text = current.text,
            tempC = current.tempC,
            feelsLikeC = current.feelsLikeC,
        )
    }

    val nowSec = nowUtcMillis / 1000
    val hour = hourly.firstOrNull { nowSec >= it.timeUtc && nowSec < it.timeUtc + 3600 }
    if (hour != null) {
        return NowReading(
            source = NowSource.HOURLY_FORECAST,
            // 图标取那一小时的昼夜属性，所以白天不会再显示月亮
            icon = hour.icon,
            text = hour.text.ifBlank { current.text },
            tempC = hour.tempC,
        )
    }

    val today = daily.firstOrNull { it.dateIso == localToday(nowUtcMillis) }
    if (today != null) {
        return NowReading(
            source = NowSource.DAILY_FORECAST,
            icon = today.icon,
            text = today.text,
            // 日预报没有"此刻温度"这个概念，界面显示区间
            tempC = (today.hiC + today.loC) / 2f,
            rangeLoC = today.loC,
            rangeHiC = today.hiC,
        )
    }

    return NowReading(
        source = NowSource.LAST_KNOWN,
        icon = current.icon,
        text = current.text,
        tempC = current.tempC,
    )
}
