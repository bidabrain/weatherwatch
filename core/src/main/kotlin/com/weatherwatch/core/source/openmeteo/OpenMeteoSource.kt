package com.weatherwatch.core.source.openmeteo

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.Current
import com.weatherwatch.core.model.Day
import com.weatherwatch.core.model.GeoPoint
import com.weatherwatch.core.model.Hour
import com.weatherwatch.core.model.WeatherBundle
import com.weatherwatch.core.net.HttpFetcher
import com.weatherwatch.core.source.WeatherSource
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Open-Meteo 实现。免费、免 key、免注册，实测整包响应约 2.2 KB。
 *
 * 关键参数说明（改动前请先读）：
 *  - `timeformat=unixtime`：所有时间都变成**真正的 UTC 秒**，彻底绕开
 *    ISO 字符串解析和本地时间歧义。
 *  - `timezone=auto`：只为了拿 `utc_offset_seconds` 和时区名，
 *    不影响上面那些时间戳的语义。
 *  - `forecast_hours=24`：不要去掉。默认会返回 7*24=168 条逐时数据，
 *    payload 涨到 10KB 以上，蓝牙链路上是实打实的等待时间。
 */
class OpenMeteoSource(
    private val http: HttpFetcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : WeatherSource {

    override val id: String = ID

    override suspend fun fetch(city: City): WeatherBundle {
        val dto = json.decodeFromString(OmResponse.serializer(), http.get(buildUrl(city.point)))
        return dto.toBundle(city, now())
    }

    internal fun buildUrl(p: GeoPoint): String = buildString {
        append(baseUrl)
        append("?latitude=").append(fmt(p.lat))
        append("&longitude=").append(fmt(p.lon))
        append("&timezone=auto&timeformat=unixtime")
        append("&forecast_days=").append(FORECAST_DAYS)
        append("&forecast_hours=").append(FORECAST_HOURS)
        append("&current=").append(CURRENT_FIELDS)
        append("&hourly=").append(HOURLY_FIELDS)
        append("&daily=").append(DAILY_FIELDS)
    }

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.4f", v)

    companion object {
        const val ID = "open-meteo"
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/forecast"

        private const val FORECAST_DAYS = 7
        private const val FORECAST_HOURS = 24

        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,is_day," +
                "weather_code,surface_pressure,wind_speed_10m,wind_direction_10m,uv_index"
        private const val HOURLY_FIELDS =
            "temperature_2m,weather_code,precipitation_probability,is_day"
        private const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min," +
                "precipitation_probability_max,sunrise,sunset,uv_index_max"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

internal fun OmResponse.toBundle(city: City, fetchedAtMillis: Long): WeatherBundle {
    val off = utcOffsetSeconds
    val cur = current

    return WeatherBundle(
        cityId = city.id,
        sourceId = OpenMeteoSource.ID,
        fetchedAtUtcMillis = fetchedAtMillis,
        tzOffsetSeconds = off,
        tzId = timezone,
        current = Current(
            timeUtc = cur.time,
            tempC = (cur.temperature ?: 0.0).toFloat(),
            feelsLikeC = (cur.apparent ?: cur.temperature ?: 0.0).toFloat(),
            icon = Wmo.icon(cur.weatherCode, cur.isDay == 1),
            text = Wmo.textZh(cur.weatherCode),
            humidityPct = cur.humidity ?: 0,
            windKph = (cur.windSpeed ?: 0.0).toFloat(),
            windDirDeg = cur.windDir ?: 0,
            pressureHpa = (cur.pressure ?: 0.0).toFloat(),
            uvIndex = (cur.uvIndex ?: 0.0).toFloat(),
            visibilityKm = null, // Open-Meteo 的 current 不提供
        ),
        hourly = hourly.time.indices.map { i ->
            val code = hourly.weatherCode.getOrNull(i) ?: -1
            Hour(
                timeUtc = hourly.time[i],
                tempC = (hourly.temperature.getOrNull(i) ?: 0.0).toFloat(),
                icon = Wmo.icon(code, (hourly.isDay.getOrNull(i) ?: 1) == 1),
                popPct = hourly.pop.getOrNull(i) ?: 0,
                text = Wmo.textZh(code),
            )
        },
        daily = daily.time.indices.map { i ->
            val code = daily.weatherCode.getOrNull(i) ?: -1
            val start = daily.time[i]
            Day(
                dateIso = localDateIso(start, off),
                startUtc = start,
                hiC = (daily.tMax.getOrNull(i) ?: 0.0).toFloat(),
                loC = (daily.tMin.getOrNull(i) ?: 0.0).toFloat(),
                // 日视图恒用白天图标：晚上看明天的预报，显示月亮是错的
                icon = Wmo.icon(code, isDay = true),
                text = Wmo.textZh(code),
                popPct = daily.popMax.getOrNull(i) ?: 0,
                uvIndexMax = (daily.uvMax.getOrNull(i) ?: 0.0).toFloat(),
                sunriseUtc = daily.sunrise.getOrNull(i) ?: 0L,
                sunsetUtc = daily.sunset.getOrNull(i) ?: 0L,
            )
        },
        alerts = emptyList(), // Open-Meteo 无预警接口
    )
}

/**
 * 取 UTC 瞬时值在**城市本地时区**的日历日期。
 *
 * 实测确认：`timeformat=unixtime` 下 daily.time 是真 UTC 秒
 * （杭州 GMT+8 返回 09-21T16:00Z，即本地 09-22 零点）。
 * 直接按 UTC 格式化会整体错一天，必须先加 offset。
 */
private fun localDateIso(utcSeconds: Long, offsetSeconds: Int): String =
    LocalDateTime.ofEpochSecond(utcSeconds + offsetSeconds, 0, ZoneOffset.UTC)
        .toLocalDate()
        .toString()
