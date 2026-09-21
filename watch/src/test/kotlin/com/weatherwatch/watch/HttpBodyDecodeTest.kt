package com.weatherwatch.watch

import com.weatherwatch.watch.data.AndroidHttpFetcher
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 回归测试：手动设置 Accept-Encoding: gzip 会关闭 HttpURLConnection 的
 * 透明解压，读到原始 gzip 字节，线上表现为 JsonDecodingException。
 * 现在按魔数解压，两种情况都能还原。
 */
class HttpBodyDecodeTest {

    private fun gzip(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(s.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    @Test
    fun `plain utf8 body passes through`() {
        val json = """{"timezone":"Asia/Shanghai","text":"阴"}"""
        assertEquals(json, AndroidHttpFetcher.decodeBody(json.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `gzipped body is inflated`() {
        val json = """{"timezone":"Asia/Shanghai","text":"雷阵雨"}"""
        assertEquals(json, AndroidHttpFetcher.decodeBody(gzip(json)))
    }

    @Test
    fun `empty body does not crash`() {
        assertEquals("", AndroidHttpFetcher.decodeBody(ByteArray(0)))
    }

    @Test
    fun `truncated body is not mistaken for gzip`() {
        // 只有 1 个字节凑不成魔数，应原样当文本返回，
        // 让后续 JSON 解析报出明确错误，而不是在这里抛 ZipException
        assertEquals(0x1f.toChar().toString(), AndroidHttpFetcher.decodeBody(byteArrayOf(0x1f)))
    }

    @Test
    fun `json starting with brace is never mistaken for gzip`() {
        val json = "{}"
        assertEquals(json, AndroidHttpFetcher.decodeBody(json.toByteArray()))
    }
}
