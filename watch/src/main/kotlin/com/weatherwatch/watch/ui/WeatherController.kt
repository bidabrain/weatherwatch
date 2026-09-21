package com.weatherwatch.watch.ui

import com.weatherwatch.core.model.City
import com.weatherwatch.core.model.WeatherBundle
import com.weatherwatch.core.source.openmeteo.GeocodingSource
import com.weatherwatch.core.sync.SyncTrigger
import com.weatherwatch.watch.data.CityStore
import com.weatherwatch.watch.data.SyncOutcome
import com.weatherwatch.watch.data.WeatherRepository
import com.weatherwatch.watch.data.describeNetworkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SearchState(
    val visible: Boolean = false,
    val results: List<City> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

data class UiState(
    val city: City? = null,
    val bundle: WeatherBundle? = null,
    val syncing: Boolean = false,
    /** 一次性提示，如"无网络"。成功同步后清空。 */
    val notice: String? = null,
    val search: SearchState = SearchState(),
)

/**
 * 应用级状态持有者。
 *
 * 没有用 ViewModel：手表不会横竖屏切换，这个 App 也只有一个 Activity，
 * 引 lifecycle-viewmodel 只是多一个依赖。状态挂在 Application 上更简单，
 * 而且 Activity 重建时缓存数据不会丢。
 */
class WeatherController(
    private val repo: WeatherRepository,
    private val geocoder: GeocodingSource,
    private val cityStore: CityStore,
    private val scope: CoroutineScope,
) {
    private val _ui: MutableStateFlow<UiState>

    /**
     * 用 val 而不是 get()：getter 每次访问都会新建一个 asStateFlow 包装，
     * collectAsState 会因此在每次重组时重新订阅一遍。
     */
    val ui: StateFlow<UiState>

    private var searchJob: Job? = null

    init {
        val saved = cityStore.load()
        _ui = MutableStateFlow(
            UiState(
                city = saved,
                // 首次启动没有城市，直接进搜索页 —— 比先显示一个假的默认城市诚实
                search = SearchState(visible = saved == null),
            ),
        )
        ui = _ui.asStateFlow()

        repo.bundle
            .onEach { b -> _ui.value = _ui.value.copy(bundle = b) }
            .launchIn(scope)
    }

    /** 打开 App 的那一刻。受节流约束，45 分钟内重复开不会重复请求。 */
    fun onAppOpen() = launchSync(SyncTrigger.APP_OPEN)

    fun onManualRefresh() = launchSync(SyncTrigger.MANUAL)

    private fun launchSync(trigger: SyncTrigger) {
        val city = _ui.value.city ?: return
        scope.launch { runSync(city, trigger) }
    }

    private suspend fun runSync(city: City, trigger: SyncTrigger) {
        // 先把缓存渲染出来，再去碰网络
        repo.loadCache(city)

        if (_ui.value.syncing) return
        _ui.value = _ui.value.copy(syncing = true)

        val outcome = repo.sync(city, trigger)

        _ui.value = _ui.value.copy(
            syncing = false,
            notice = when (outcome) {
                is SyncOutcome.Updated -> null
                is SyncOutcome.Skipped -> _ui.value.notice
                // 有缓存时失败不打扰用户，页面上的"更新于…"已经说明了问题
                is SyncOutcome.Failed -> if (_ui.value.bundle == null) outcome.reason else null
            },
        )
    }

    // ---- 城市搜索 ----

    fun openSearch() {
        _ui.value = _ui.value.copy(search = SearchState(visible = true))
    }

    /** 没有选过城市时不允许关闭：关掉也没东西可看 */
    fun closeSearch() {
        if (_ui.value.city == null) return
        searchJob?.cancel()
        _ui.value = _ui.value.copy(search = SearchState(visible = false))
    }

    /**
     * 输入变化。防抖 400ms —— 表上每敲一个字母就发一次请求的话，
     * 蓝牙链路（RTT 约 1s）会被打满，结果还会乱序回来。
     */
    fun onQueryChange(query: String) {
        searchJob?.cancel()
        updateSearch { it.copy(message = null) }

        if (query.trim().length < GeocodingSource.MIN_QUERY_LENGTH) {
            updateSearch { it.copy(results = emptyList(), loading = false) }
            return
        }

        searchJob = scope.launch {
            delay(DEBOUNCE_MS)
            updateSearch { it.copy(loading = true) }
            try {
                val results = geocoder.search(query)
                updateSearch {
                    it.copy(
                        results = results,
                        loading = false,
                        message = if (results.isEmpty()) "没有找到" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateSearch { it.copy(loading = false, message = describeNetworkError(e)) }
            }
        }
    }

    fun selectCity(city: City) {
        searchJob?.cancel()
        cityStore.save(city)
        _ui.value = _ui.value.copy(
            city = city,
            notice = null,
            search = SearchState(visible = false),
        )
        scope.launch {
            repo.switchTo(city)
            // 换城市必然要立刻拉数据，用 MANUAL 绕过节流
            runSync(city, SyncTrigger.MANUAL)
        }
    }

    private inline fun updateSearch(block: (SearchState) -> SearchState) {
        _ui.value = _ui.value.copy(search = block(_ui.value.search))
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
