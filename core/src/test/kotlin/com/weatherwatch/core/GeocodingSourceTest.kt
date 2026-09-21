package com.weatherwatch.core

import com.weatherwatch.core.net.HttpFetcher
import com.weatherwatch.core.source.openmeteo.GeocodingSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeocodingSourceTest {

    private class FakeFetcher(private val body: String) : HttpFetcher {
        var lastUrl: String? = null
        var calls = 0
        override suspend fun get(url: String): String {
            lastUrl = url
            calls++
            return body
        }
    }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/" + name)!!.readBytes().decodeToString()

    @Test
    fun `pinyin query returns chinese names`() {
        val cities = runBlocking {
            GeocodingSource(FakeFetcher(fixture("geocoding_hangzhou.json"))).search("hangzhou")
        }
        assertEquals(3, cities.size)
        assertEquals("杭州", cities[0].name)
        assertEquals("浙江 · 中国", cities[0].region)
        assertEquals(30.29365, cities[0].point.lat, 1e-5)
        assertEquals(120.16142, cities[0].point.lon, 1e-5)
    }

    /** 同名城市靠 region 区分：浙江的杭州和四川的杭州 */
    @Test
    fun `same name cities are distinguished by region`() {
        val cities = runBlocking {
            GeocodingSource(FakeFetcher(fixture("geocoding_hangzhou.json"))).search("hangzhou")
        }
        val sameName = cities.filter { it.name == "杭州" }
        assertEquals(2, sameName.size)
        assertEquals(listOf("浙江 · 中国", "四川 · 中国"), sameName.map { it.region })
        // id 必须不同，否则两者的缓存快照会互相覆盖
        assertEquals(2, sameName.map { it.id }.toSet().size)
    }

    @Test
    fun `populated places rank above airports`() {
        val cities = runBlocking {
            GeocodingSource(FakeFetcher(fixture("geocoding_hangzhou.json"))).search("hangzhou")
        }
        assertEquals("杭州萧山国际机场", cities.last().name)
        assertTrue(cities.last().id.startsWith("omf-"))
        assertTrue(cities.dropLast(1).all { it.id.startsWith("omp-") })
    }

    /**
     * 搜不到时 API 返回 {"generationtime_ms":...}，连 results 字段都没有。
     * 这条如果不兜住，用户随便打几个字母 App 就崩。
     */
    @Test
    fun `no results does not crash`() {
        val cities = runBlocking {
            GeocodingSource(FakeFetcher(fixture("geocoding_empty.json"))).search("zzzzqqq")
        }
        assertTrue(cities.isEmpty())
    }

    @Test
    fun `short query skips the request entirely`() {
        val f = FakeFetcher(fixture("geocoding_empty.json"))
        val src = GeocodingSource(f)
        runBlocking {
            assertTrue(src.search("h").isEmpty())
            assertTrue(src.search(" ").isEmpty())
            assertTrue(src.search("").isEmpty())
        }
        assertEquals(0, f.calls, "过短的输入不该发请求")
    }

    @Test
    fun `query is url encoded`() {
        val f = FakeFetcher(fixture("geocoding_empty.json"))
        runBlocking { GeocodingSource(f).search("new york") }
        val url = f.lastUrl!!
        assertTrue("name=new+york" in url || "name=new%20york" in url, url)
        assertTrue("language=zh" in url, url)
    }
}
