package com.weatherwatch.watch.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 屏幕实测 372x430 px / density 2.0 -> 186x215 dp，圆角矩形。
 * 四角 16dp 内不放内容，否则会被圆角切掉。
 */
object Dim {
    val Pad = 16.dp

    /** 最小可点区域。44dp 在 320dpi 下只有 7.1mm，偏小，提到 48dp。 */
    val Touch = 48.dp
}

object C {
    val Bg = Color(0xFF000000)

    // 纯黑底 + 三级白色透明度。不用 Material 的色阶和阴影：
    // 手表上看不出层次，只会把黑底变成灰底，白白耗电。
    val T1 = Color(0xFFFFFFFF)
    val T2 = Color(0xB3FFFFFF)
    val T3 = Color(0x73FFFFFF)

    val Stale = Color(0xFFE8A33D)
    val Offline = Color(0xFF8A9199)

    // 天气强调色。只用在图标和数据点上，文字保持白色。
    val Sun = Color(0xFFFFB340)
    val Moon = Color(0xFFE8EDF2)
    val Cloud = Color(0xFF9AA5B1)
    val Rain = Color(0xFF5AA9E6)
    val Snow = Color(0xFFBFD9F2)
    val Bolt = Color(0xFFFFD166)
    val Fog = Color(0xFFAEB6BE)
}

object F {
    val Temp = 54.sp
    val TempRange = 34.sp
    val TempDeg = 20.sp
    val Body = 13.sp
    val Label = 11.sp
    val Tiny = 10.sp
}
