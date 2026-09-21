package com.weatherwatch.watch.data

import com.weatherwatch.core.net.HttpFetcher
import com.weatherwatch.core.net.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 基于 HttpURLConnection —— 不引 OkHttp。
 * 整个 App 只有一个 GET 请求，OkHttp 会让 APK 大 800KB 且毫无收益。
 *
 * 两个超时值来自 M0 实测：手表经蓝牙隧道上网，
 * 空响应 RTT 约 490ms，2.2KB 的 JSON 约 960ms。
 * 手机侧信号差时会显著劣化，所以留足余量。
 */
class AndroidHttpFetcher : HttpFetcher {

    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            // 千万不要在这里设 Accept-Encoding: gzip。
            // HttpURLConnection 只在**它自己**加这个头时才做透明解压；
            // 一旦手动设置，解压责任就转移给调用方，
            // 读到的是原始 gzip 字节，表现为 JsonDecodingException。
            // 不设置时它会自动协商 gzip 并解压，带宽一样省。
        }
        try {
            val code = conn.responseCode
            val stream = if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
                conn.inputStream
            } else {
                conn.errorStream
            }
            val body = decodeBody(stream?.use { it.readBytes() } ?: ByteArray(0))
            if (code !in 200..299) throw HttpStatusException(code, body.take(200))
            body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT = "weatherwatch/0.1 (Android; OPPO Watch)"

        /**
         * 按魔数判断是否 gzip，而不是看 Content-Encoding 头。
         *
         * 头部不可信：透明解压发生时 Android 会把该头摘掉，
         * 而中间代理（这里流量还要过手机）可能压缩却不声明。
         * 魔数是字节事实，两种情况都不会误判。
         */
        internal fun decodeBody(raw: ByteArray): String =
            if (isGzip(raw)) {
                GZIPInputStream(raw.inputStream()).use { it.readBytes() }
                    .toString(Charsets.UTF_8)
            } else {
                raw.toString(Charsets.UTF_8)
            }

        private fun isGzip(b: ByteArray): Boolean =
            b.size >= 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte()
    }
}
