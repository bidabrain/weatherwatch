package com.weatherwatch.core.net

import java.io.IOException

/**
 * core 不依赖任何具体 HTTP 实现 —— 手表侧注入 OkHttp/HttpURLConnection，
 * 单测侧注入读本地 fixture 的假实现。解析逻辑因此可以完全离线验证。
 */
fun interface HttpFetcher {
    /** 返回响应体文本。非 2xx 应抛 [HttpStatusException]。 */
    suspend fun get(url: String): String
}

class HttpStatusException(
    val statusCode: Int,
    val bodySnippet: String?,
) : IOException("HTTP $statusCode${bodySnippet?.let { ": $it" } ?: ""}")
