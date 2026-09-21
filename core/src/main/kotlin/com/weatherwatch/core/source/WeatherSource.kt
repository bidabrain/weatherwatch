package com.weatherwatch.core.source

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.WeatherBundle

/**
 * 数据源抽象。新增一个源 = 实现这一个方法，UI / 缓存 / 同步策略全都不用动。
 *
 * 约定：一次调用必须返回**全量** bundle。蓝牙链路 RTT 实测约 500ms~1s，
 * 拆成多次请求会让同步耗时线性放大。
 */
interface WeatherSource {
    val id: String

    suspend fun fetch(city: City): WeatherBundle
}
