package com.weatherwatch.watch.data

import android.util.Log
import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.WeatherBundle
import com.weatherwatch.core.model.ageMillis
import com.weatherwatch.core.model.localHour
import com.weatherwatch.core.source.WeatherSource
import com.weatherwatch.core.sync.SyncPolicy
import com.weatherwatch.core.sync.SyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime

sealed interface SyncOutcome {
    data object Updated : SyncOutcome
    /** 被节流策略挡住，缓存还够新 */
    data object Skipped : SyncOutcome
    data class Failed(val reason: String) : SyncOutcome
}

/**
 * 缓存是唯一真相来源。UI 只订阅 [bundle]，永远不等网络。
 * "不联网也能看"因此是架构的自然结果，而不是一条需要单独处理的降级分支。
 */
class WeatherRepository(
    private val store: SnapshotStore,
    private val source: WeatherSource,
    private val device: DeviceState,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _bundle = MutableStateFlow<WeatherBundle?>(null)
    val bundle: StateFlow<WeatherBundle?> = _bundle.asStateFlow()

    /** 串行化同步，避免 onStart 和手动刷新撞车发两次请求 */
    private val mutex = Mutex()

    private var currentCityId: String? = null

    suspend fun loadCache(city: City) {
        if (currentCityId == city.id && _bundle.value != null) return
        switchTo(city)
    }

    /**
     * 切换城市。必须先清空再读 —— 否则在新城市的缓存读出来之前，
     * 界面会把上一个城市的温度挂在新城市的名字下面。
     */
    suspend fun switchTo(city: City) {
        currentCityId = city.id
        _bundle.value = null
        _bundle.value = store.read(city.id)
    }

    suspend fun sync(city: City, trigger: SyncTrigger): SyncOutcome = mutex.withLock {
        val now = clock()
        val cached = _bundle.value

        val age = cached?.ageMillis(now) ?: Long.MAX_VALUE
        // 没有缓存时还不知道城市时区，退回设备本地小时 —— 只用于夜间降频，误差可接受
        val localHour = cached?.localHour(now) ?: LocalTime.now().hour

        if (!SyncPolicy.shouldSync(age, localHour, device.batteryPct(), trigger)) {
            return SyncOutcome.Skipped
        }
        if (!device.hasUsableNetwork()) {
            return SyncOutcome.Failed("无网络")
        }

        return try {
            val fresh = source.fetch(city)
            store.write(fresh)
            _bundle.value = fresh
            SyncOutcome.Updated
        } catch (e: Throwable) {
            Log.w(TAG, "sync failed", e)
            SyncOutcome.Failed(describeNetworkError(e))
        }
    }

    private companion object {
        const val TAG = "WeatherRepo"
    }
}
