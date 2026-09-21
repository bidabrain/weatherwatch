package com.weatherwatch.watch.data

import com.weatherwatch.core.net.HttpStatusException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把异常翻译成表盘上能看懂的一行字。
 *
 * 未知异常保留类名而不是笼统写"出错了" —— gzip 那个 bug
 * 就是靠界面上显示的 JsonDecodingException 定位到的。
 */
fun describeNetworkError(e: Throwable): String = when (e) {
    is SocketTimeoutException -> "连接超时"
    is UnknownHostException -> "无法解析域名"
    is HttpStatusException -> "服务返回 " + e.statusCode
    else -> e.javaClass.simpleName
}
