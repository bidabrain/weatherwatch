package com.weatherwatch.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherwatch.core.model.City
import com.weatherwatch.core.source.openmeteo.GeocodingSource

/**
 * 城市搜索页。输入拼音（hangzhou），结果显示中文（杭州 · 浙江）。
 * 同名城市很常见，所以每行必须带省份，否则没法选。
 */
@Composable
fun SearchPage(
    state: SearchState,
    currentCityId: String?,
    onQueryChange: (String) -> Unit,
    onPick: (City) -> Unit,
) {
    /*
     * 文本框状态必须由文本框自己同步持有。
     *
     * 之前把 value 绑到 Controller 的 StateFlow 上，链路是
     *   onValueChange -> Controller -> StateFlow -> collectAsState -> 重组
     * StateFlow 的发射不是同步的，value 至少滞后一帧；
     * 输入法靠 composing region 跟踪文本，值一滞后就整个错位 ——
     * 表现为打字和退格在输入栏上完全不更新。
     *
     * 用 TextFieldValue 而不是 String：它同时携带光标和选区，
     * 退格、中间插入这些操作才不会跳位。
     */
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }

    // 自动聚焦拉起输入法：表上那个输入框很小，让用户自己点太别扭
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = field,
            onValueChange = { updated ->
                field = updated              // 当帧生效，输入法始终看到最新值
                onQueryChange(updated.text)  // 搜索是异步的，与显示解耦
            },
            singleLine = true,
            textStyle = TextStyle(color = C.T1, fontSize = 14.sp),
            cursorBrush = SolidColor(C.Sun),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    if (field.text.isEmpty()) {
                        BasicText(
                            "拼音，如 hangzhou",
                            style = TextStyle(color = C.T3, fontSize = 13.sp),
                        )
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.height(6.dp))

        val typed = field.text.trim()
        when {
            typed.length < GeocodingSource.MIN_QUERY_LENGTH -> StatusLine("输入至少 2 个字母")
            state.loading -> StatusLine("搜索中…")
            state.message != null -> StatusLine(state.message)
            state.results.isEmpty() -> StatusLine("搜索中…")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(state.results, key = { it.id }) { city ->
                    CityRow(city, selected = city.id == currentCityId, onPick = onPick)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        BasicText(text, style = TextStyle(color = C.T3, fontSize = F.Label))
    }
}

@Composable
private fun CityRow(city: City, selected: Boolean, onPick: (City) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable { onPick(city) }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            city.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = if (selected) C.Sun else C.T1, fontSize = 14.sp),
        )
        val region = city.region
        if (!region.isNullOrBlank()) {
            BasicText(
                region,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = C.T3, fontSize = F.Tiny),
            )
        }
    }
}
