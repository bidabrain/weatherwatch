package com.weatherwatch.core.source.openmeteo

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.GeoPoint
import com.weatherwatch.core.net.HttpFetcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * 城市搜索（Open-Meteo Geocoding，免费免 key）。
 *
 * 输入用英文/拼音，`language=zh` 让**返回结果是中文** ——
 * 打 "hangzhou" 看到的是"杭州 · 浙江"。
 */
class GeocodingSource(
    private val http: HttpFetcher,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<City> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return emptyList()

        val dto = json.decodeFromString(GeoResponse.serializer(), http.get(buildUrl(q, limit)))
        return dto.results
            .map { it.toCity() }
            // 居民点排在机场/地标前面：搜 hangzhou 时"杭州"应该在"杭州萧山国际机场"之前
            .sortedBy { if (it.id.startsWith(POPULATED_PREFIX)) 0 else 1 }
    }

    internal fun buildUrl(query: String, limit: Int): String = buildString {
        append(baseUrl)
        append("?name=").append(URLEncoder.encode(query, "UTF-8"))
        append("&count=").append(limit)
        append("&language=zh&format=json")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"

        /** 少于 2 个字符不发请求：前缀太短返回的全是噪音 */
        const val MIN_QUERY_LENGTH = 2

        private const val DEFAULT_LIMIT = 8
        private const val POPULATED_PREFIX = "omp-"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * 搜不到结果时响应是 `{"generationtime_ms":0.06}` —— **没有 results 字段**。
 * 所以必须给默认值，否则"搜不到"会变成解析崩溃。
 */
@Serializable
internal data class GeoResponse(
    val results: List<GeoResult> = emptyList(),
)

@Serializable
internal data class GeoResult(
    val id: Long = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val country: String? = null,
    val admin1: String? = null,
    @SerialName("feature_code") val featureCode: String? = null,
)

/** id 前缀区分居民点(omp-)和其他地物(omf-)，同时充当快照文件名 */
internal fun GeoResult.toCity(): City {
    val prefix = if (featureCode?.startsWith("PPL") == true) "omp-" else "omf-"
    return City(
        id = prefix + id,
        name = name,
        point = GeoPoint(latitude, longitude),
        region = listOfNotNull(admin1, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .ifBlank { null },
    )
}
