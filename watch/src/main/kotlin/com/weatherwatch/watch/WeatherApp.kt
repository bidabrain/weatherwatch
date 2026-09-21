package com.weatherwatch.watch

import android.app.Application
import com.weatherwatch.core.source.openmeteo.GeocodingSource
import com.weatherwatch.core.source.openmeteo.OpenMeteoSource
import com.weatherwatch.watch.data.AndroidHttpFetcher
import com.weatherwatch.watch.data.CityStore
import com.weatherwatch.watch.data.DeviceState
import com.weatherwatch.watch.data.SnapshotStore
import com.weatherwatch.watch.data.WeatherRepository
import com.weatherwatch.watch.ui.WeatherController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.io.File

/** 手搓依赖注入。这个规模的 App 引 Hilt 纯属负担。 */
class WeatherApp : Application() {

    private val appScope: CoroutineScope by lazy { MainScope() }

    val controller: WeatherController by lazy {
        // 天气和城市搜索共用一个 fetcher：同样的超时设置、同样的 gzip 处理
        val http = AndroidHttpFetcher()
        val repo = WeatherRepository(
            store = SnapshotStore(File(filesDir, "snapshots")),
            source = OpenMeteoSource(http),
            device = DeviceState(this),
        )
        WeatherController(
            repo = repo,
            geocoder = GeocodingSource(http),
            cityStore = CityStore(this),
            scope = appScope,
        )
    }
}
