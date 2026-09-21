package com.weatherwatch.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weatherwatch.core.model.NowReading
import com.weatherwatch.core.model.NowSource
import com.weatherwatch.core.model.ageMillis
import com.weatherwatch.core.model.nowReading
import com.weatherwatch.watch.ui.components.WeatherGlyph

/**
 * 主屏。**永远先渲染缓存，从不等待网络** —— 这是离线可看的实现方式：
 * 不是一条降级分支，而是唯一的一条路径。
 *
 * 显示的"现在"由 [nowReading] 从缓存里挑：实测值过期后会自动退到
 * 逐时预报、再退到今日区间，所以不联网放一两天，界面依然跟着真实日期走。
 */
@Composable
fun HomePage(
    state: UiState,
    nowMillis: Long,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val pageInteraction = remember { MutableInteractionSource() }
    val cityInteraction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            // 整页可点即刷新：表盘上没有余裕放按钮，点击区域越大越好
            .clickable(pageInteraction, indication = null, onClick = onRefresh),
    ) {
        val b = state.bundle
        val now = b?.nowReading(nowMillis)
        val age = b?.ageMillis(nowMillis) ?: 0L

        // 警示条的依据是"已经说不清此刻什么天气了"，而不是"抓取时间很久以前"。
        // 缓存放了 8 小时但逐时预报还覆盖得到当前小时时，数据其实是准的。
        val degraded = now?.source == NowSource.DAILY_FORECAST ||
            now?.source == NowSource.LAST_KNOWN
        if (degraded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(C.Stale)
                    .align(Alignment.TopCenter),
            )
        }

        Column(
            Modifier.fillMaxSize().padding(Dim.Pad),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CityHeader(state, cityInteraction, onOpenSearch)

            if (b == null || now == null) {
                EmptyBody(state)
            } else {
                Spacer(Modifier.height(2.dp))
                WeatherGlyph(now.icon, size = 38.dp)
                Spacer(Modifier.height(2.dp))

                Temperature(now)

                BasicText(
                    subtitleFor(now),
                    style = TextStyle(color = C.T2, fontSize = F.Body),
                )

                Spacer(Modifier.weight(1f))
                ProvenanceLine(state.syncing, now.source, age)
            }
        }
    }
}

@Composable
private fun Temperature(now: NowReading) {
    // 已离线到只剩最后已知值时降为灰色：数字还在，但明确表示它不可信
    val color = if (now.source == NowSource.LAST_KNOWN) C.Offline else C.T1
    val lo = now.rangeLoC
    val hi = now.rangeHiC

    Row(verticalAlignment = Alignment.Top) {
        if (lo != null && hi != null) {
            // 日预报没有"此刻温度"这个概念，只能给区间
            BasicText(
                tempLabel(lo) + "~" + tempLabel(hi),
                style = TextStyle(
                    color = color,
                    fontSize = F.TempRange,
                    fontWeight = FontWeight.Light,
                ),
            )
            BasicText(
                "°",
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(color = color, fontSize = F.Tiny, fontWeight = FontWeight.Light),
            )
        } else {
            BasicText(
                tempLabel(now.tempC),
                style = TextStyle(color = color, fontSize = F.Temp, fontWeight = FontWeight.Light),
            )
            BasicText(
                "°",
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(color = color, fontSize = F.TempDeg, fontWeight = FontWeight.Light),
            )
        }
    }
}

/** 体感只有实测值有，其余档位不显示 —— 拿旧体感配新气温是造假 */
private fun subtitleFor(now: NowReading): String {
    val feels = now.feelsLikeC
    return if (feels != null) {
        now.text + " · 体感 " + tempLabel(feels) + "°"
    } else {
        now.text
    }
}

/**
 * 这一行同时回答"数据多旧"和"这个数字是哪来的"。
 * 离线 App 谎报数据来源等于骗人。
 */
@Composable
private fun ProvenanceLine(syncing: Boolean, source: NowSource, ageMillis: Long) {
    val offline = "已离线 " + relativeAge(ageMillis).removeSuffix("前")
    val text: String
    val color: Color
    when {
        syncing -> {
            text = "同步中…"
            color = C.T3
        }
        source == NowSource.OBSERVED -> {
            text = "更新于 " + relativeAge(ageMillis)
            color = C.T3
        }
        source == NowSource.HOURLY_FORECAST -> {
            text = "预报 · 更新于 " + relativeAge(ageMillis)
            color = C.T3
        }
        source == NowSource.DAILY_FORECAST -> {
            text = "今日预报 · " + offline
            color = C.Stale
        }
        else -> {
            text = offline
            color = C.Stale
        }
    }
    BasicText(text, style = TextStyle(color = color, fontSize = F.Tiny))
}

@Composable
private fun CityHeader(
    state: UiState,
    interaction: MutableInteractionSource,
    onOpenSearch: () -> Unit,
) {
    BasicText(
        (state.city?.name ?: "选择城市") + "  ▾",
        modifier = Modifier
            .clickable(interaction, indication = null, onClick = onOpenSearch)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        style = TextStyle(color = C.T3, fontSize = F.Label),
    )
}

@Composable
private fun EmptyBody(state: UiState) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            if (state.syncing) "正在获取…" else "暂无数据",
            style = TextStyle(color = C.T2, fontSize = F.Body),
        )
        if (!state.syncing) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                (state.notice?.plus("\n") ?: "") + "点击屏幕重试",
                style = TextStyle(color = C.T3, fontSize = F.Tiny, textAlign = TextAlign.Center),
            )
        }
    }
}
