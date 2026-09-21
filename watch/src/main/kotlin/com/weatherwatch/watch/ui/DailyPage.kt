package com.weatherwatch.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weatherwatch.core.model.Day
import com.weatherwatch.core.model.localToday
import com.weatherwatch.core.model.upcomingDays
import com.weatherwatch.watch.ui.components.WeatherGlyph

/**
 * 七日预报。刻意设计成**一屏放得下、不滚动** ——
 * 表盘上滚动列表很难精确停位，看一眼就走的场景不该要求操作。
 * 7 行 x 23dp + 表头 = 175dp，正好塞进 183dp 的内容区。
 */
@Composable
fun DailyPage(state: UiState, nowMillis: Long) {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        val b = state.bundle
        if (b == null) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText("暂无数据", style = TextStyle(color = C.T3, fontSize = F.Body))
            }
        } else {
            val days = b.upcomingDays(nowMillis)
            val todayIso = b.localToday(nowMillis)
            // 温度条按整周的极值归一化，这样各行之间可以横向比较
            val weekLo = days.minOfOrNull { it.loC } ?: 0f
            val weekHi = days.maxOfOrNull { it.hiC } ?: 1f

            Column(Modifier.fillMaxSize().padding(Dim.Pad)) {
                BasicText(
                    "未来 " + days.size + " 天",
                    style = TextStyle(color = C.T3, fontSize = F.Label),
                )
                Spacer(Modifier.height(2.dp))
                days.forEach { d ->
                    DayRow(d, todayIso, weekLo, weekHi)
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: Day, todayIso: String, weekLo: Float, weekHi: Float) {
    Row(
        Modifier.fillMaxWidth().height(23.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            weekdayLabel(day.dateIso, todayIso),
            modifier = Modifier.width(26.dp),
            style = TextStyle(color = C.T2, fontSize = F.Label),
        )
        WeatherGlyph(day.icon, size = 16.dp)
        Spacer(Modifier.width(4.dp))
        BasicText(
            tempLabel(day.loC),
            modifier = Modifier.width(17.dp),
            style = TextStyle(color = C.T3, fontSize = F.Tiny, textAlign = TextAlign.End),
        )
        Spacer(Modifier.width(3.dp))
        TempBar(
            day.loC, day.hiC, weekLo, weekHi,
            Modifier.weight(1f).height(4.dp),
        )
        Spacer(Modifier.width(3.dp))
        BasicText(
            tempLabel(day.hiC),
            modifier = Modifier.width(17.dp),
            style = TextStyle(color = C.T1, fontSize = F.Tiny, textAlign = TextAlign.End),
        )
    }
}

/**
 * 温度区间条。渐变定义在整条轨道上而不是每段自己算，
 * 所以同一个温度在任何一行都是同一个颜色，行与行之间才能直接比。
 */
@Composable
private fun TempBar(lo: Float, hi: Float, weekLo: Float, weekHi: Float, modifier: Modifier) {
    Canvas(modifier) {
        val span = (weekHi - weekLo).coerceAtLeast(1f)
        val r = CornerRadius(size.height / 2f)

        drawRoundRect(
            Color.White.copy(alpha = 0.10f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = r,
        )

        val x0 = ((lo - weekLo) / span) * size.width
        val x1 = ((hi - weekLo) / span) * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(C.Rain, C.Sun),
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset(x0, 0f),
            size = Size((x1 - x0).coerceAtLeast(size.height), size.height),
            cornerRadius = r,
        )
    }
}
