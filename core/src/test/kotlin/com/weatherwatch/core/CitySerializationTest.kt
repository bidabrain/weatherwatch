package com.weatherwatch.core

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 选中的城市要序列化进 SharedPreferences，重启后再读回来。
 * 这条链一旦断掉，表现就是"每次打开都要重选城市"。
 */
class CitySerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun roundTrip(city: City): City =
        json.decodeFromString(City.serializer(), json.encodeToString(City.serializer(), city))

    @Test
    fun `city with region round trips`() {
        val city = City("omp-1808926", "杭州", GeoPoint(30.29365, 120.16142), "浙江 · 中国")
        assertEquals(city, roundTrip(city))
    }

    @Test
    fun `city without region round trips`() {
        val city = City("omf-6301383", "某地", GeoPoint(-33.8688, 151.2093))
        assertEquals(city, roundTrip(city))
    }

    @Test
    fun `coordinates survive at full precision`() {
        // 坐标被截断会安静地查到隔壁城市的天气
        val city = City("x", "x", GeoPoint(30.293650123, 120.161420987))
        val back = roundTrip(city)
        assertEquals(30.293650123, back.point.lat, 1e-9)
        assertEquals(120.161420987, back.point.lon, 1e-9)
    }

    /**
     * 向前兼容：以后给 City 加字段时，老的已存 JSON 必须还能读出来，
     * 否则升级一次 App 用户就得重选城市。
     */
    @Test
    fun `old json without region still decodes`() {
        val old = """{"id":"omp-1","name":"北京","point":{"lat":39.9,"lon":116.4}}"""
        val city = json.decodeFromString(City.serializer(), old)
        assertEquals("北京", city.name)
        assertEquals(null, city.region)
    }

    @Test
    fun `future json with unknown fields still decodes`() {
        val future = """{"id":"omp-1","name":"北京","point":{"lat":39.9,"lon":116.4},"tz":"Asia/Shanghai"}"""
        assertEquals("北京", json.decodeFromString(City.serializer(), future).name)
    }
}
