package com.weatherwatch.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val PAGE_COUNT = 2

@Composable
fun WatchApp(controller: WeatherController) {
    val state by controller.ui.collectAsState()

    // 更新于 N 分钟前要自己走字。30 秒一跳，够准确又不会频繁重组。
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (state.search.visible) {
            // 还没选过城市时不响应返回：退出去也没东西可看
            BackHandler(enabled = state.city != null) { controller.closeSearch() }
            SearchPage(
                state = state.search,
                currentCityId = state.city?.id,
                onQueryChange = controller::onQueryChange,
                onPick = controller::selectCity,
            )
        } else {
            WeatherPages(state, now, controller)
        }
    }
}

/**
 * 垂直分页而不是长列表：表盘上滑动列表很难精确停位，
 * 分页的每一屏都是一个完整的信息单元。
 */
@Composable
private fun WeatherPages(state: UiState, now: Long, controller: WeatherController) {
    val pager = rememberPagerState { PAGE_COUNT }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> HomePage(
                    state = state,
                    nowMillis = now,
                    onRefresh = controller::onManualRefresh,
                    onOpenSearch = controller::openSearch,
                )
                else -> DailyPage(state, now)
            }
        }
        PageDots(
            current = pager.currentPage,
            total = PAGE_COUNT,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp),
        )
    }
}

@Composable
private fun PageDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == current) 4.dp else 3.dp)
                    .clip(CircleShape)
                    .background(if (i == current) C.T2 else C.T3),
            )
        }
    }
}
