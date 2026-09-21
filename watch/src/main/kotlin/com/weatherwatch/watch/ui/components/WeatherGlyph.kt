package com.weatherwatch.watch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.weatherwatch.core.model.WeatherIcon
import com.weatherwatch.watch.ui.C
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 天气图标全部用 Canvas 由基本形状拼出，不放位图资源。
 *
 * 理由：任意尺寸都锐利（表盘 DPI 高，位图要备好几套密度），
 * 颜色可跟着天气变，APK 里一个字节图片都不占。
 */
@Composable
fun WeatherGlyph(icon: WeatherIcon, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(icon) }
}

private fun DrawScope.drawGlyph(icon: WeatherIcon) {
    val s = size.minDimension
    when (icon) {
        WeatherIcon.CLEAR_DAY -> sun(0.5f, 0.5f, 0.20f, s)
        WeatherIcon.CLEAR_NIGHT -> moon(0.5f, 0.5f, 0.28f, s)

        WeatherIcon.MOSTLY_CLEAR_DAY -> {
            sun(0.36f, 0.34f, 0.15f, s)
            cloud(0.56f, 0.62f, 0.46f, s)
        }
        WeatherIcon.MOSTLY_CLEAR_NIGHT -> {
            moon(0.36f, 0.34f, 0.18f, s)
            cloud(0.56f, 0.62f, 0.46f, s)
        }
        WeatherIcon.PARTLY_CLOUDY_DAY -> {
            sun(0.33f, 0.32f, 0.14f, s)
            cloud(0.55f, 0.60f, 0.54f, s)
        }
        WeatherIcon.PARTLY_CLOUDY_NIGHT -> {
            moon(0.33f, 0.32f, 0.17f, s)
            cloud(0.55f, 0.60f, 0.54f, s)
        }

        WeatherIcon.OVERCAST -> {
            cloud(0.40f, 0.36f, 0.44f, s, C.Cloud.copy(alpha = 0.5f))
            cloud(0.56f, 0.60f, 0.56f, s)
        }

        WeatherIcon.FOG -> {
            cloud(0.5f, 0.38f, 0.54f, s, C.Fog)
            fogLines(0.5f, 0.68f, s)
        }

        WeatherIcon.DRIZZLE -> {
            cloud(0.5f, 0.42f, 0.54f, s); drops(0.5f, 0.70f, s, 3, 0.10f)
        }
        WeatherIcon.RAIN_LIGHT -> {
            cloud(0.5f, 0.42f, 0.54f, s); drops(0.5f, 0.70f, s, 2, 0.15f)
        }
        WeatherIcon.RAIN -> {
            cloud(0.5f, 0.40f, 0.54f, s); drops(0.5f, 0.70f, s, 3, 0.17f)
        }
        WeatherIcon.RAIN_HEAVY -> {
            cloud(0.5f, 0.38f, 0.54f, s); drops(0.5f, 0.68f, s, 4, 0.20f)
        }
        WeatherIcon.SHOWERS -> {
            sun(0.29f, 0.25f, 0.12f, s)
            cloud(0.57f, 0.46f, 0.50f, s)
            drops(0.57f, 0.76f, s, 3, 0.16f)
        }
        WeatherIcon.FREEZING_DRIZZLE, WeatherIcon.FREEZING_RAIN -> {
            cloud(0.5f, 0.40f, 0.54f, s)
            drops(0.40f, 0.70f, s, 2, 0.16f)
            flakes(0.68f, 0.74f, s, 2)
        }

        WeatherIcon.SNOW_LIGHT -> {
            cloud(0.5f, 0.42f, 0.54f, s); flakes(0.5f, 0.72f, s, 2)
        }
        WeatherIcon.SNOW -> {
            cloud(0.5f, 0.40f, 0.54f, s); flakes(0.5f, 0.72f, s, 3)
        }
        WeatherIcon.SNOW_HEAVY -> {
            cloud(0.5f, 0.38f, 0.54f, s); flakes(0.5f, 0.70f, s, 4)
        }
        WeatherIcon.SNOW_GRAINS -> {
            cloud(0.5f, 0.42f, 0.54f, s); flakes(0.5f, 0.72f, s, 3, 0.032f)
        }
        WeatherIcon.SNOW_SHOWERS -> {
            sun(0.29f, 0.25f, 0.12f, s)
            cloud(0.57f, 0.46f, 0.50f, s)
            flakes(0.57f, 0.78f, s, 3)
        }

        WeatherIcon.THUNDERSTORM -> {
            cloud(0.5f, 0.38f, 0.54f, s); bolt(0.5f, 0.60f, s)
        }
        WeatherIcon.THUNDERSTORM_HAIL -> {
            cloud(0.5f, 0.35f, 0.54f, s)
            bolt(0.41f, 0.57f, s)
            flakes(0.70f, 0.78f, s, 2, 0.04f)
        }

        WeatherIcon.UNKNOWN -> cloud(0.5f, 0.5f, 0.54f, s, C.Cloud.copy(alpha = 0.45f))
    }
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float, s: Float) {
    val c = Offset(cx * s, cy * s)
    val rr = r * s
    drawCircle(C.Sun, rr, c)
    val inner = rr * 1.5f
    val outer = rr * 2.05f
    repeat(8) { i ->
        val a = (PI / 4.0 * i).toFloat()
        val dx = cos(a)
        val dy = sin(a)
        drawLine(
            C.Sun,
            Offset(c.x + dx * inner, c.y + dy * inner),
            Offset(c.x + dx * outer, c.y + dy * outer),
            strokeWidth = s * 0.05f,
            cap = StrokeCap.Round,
        )
    }
}

/** 用背景色挖出月牙。全局背景是纯黑，这样比 Path 布尔运算省得多。 */
private fun DrawScope.moon(cx: Float, cy: Float, r: Float, s: Float) {
    val c = Offset(cx * s, cy * s)
    val rr = r * s
    drawCircle(C.Moon, rr, c)
    drawCircle(C.Bg, rr * 0.90f, Offset(c.x + rr * 0.58f, c.y - rr * 0.40f))
}

private fun DrawScope.cloud(cx: Float, cy: Float, w: Float, s: Float, color: Color = C.Cloud) {
    val ww = w * s
    val x = cx * s
    val y = cy * s
    drawCircle(color, ww * 0.25f, Offset(x - ww * 0.22f, y - ww * 0.03f))
    drawCircle(color, ww * 0.31f, Offset(x + ww * 0.03f, y - ww * 0.13f))
    drawCircle(color, ww * 0.23f, Offset(x + ww * 0.29f, y + ww * 0.02f))
    drawRoundRect(
        color,
        topLeft = Offset(x - ww * 0.47f, y + ww * 0.01f),
        size = Size(ww * 0.94f, ww * 0.29f),
        cornerRadius = CornerRadius(ww * 0.145f),
    )
}

private fun DrawScope.drops(cx: Float, cy: Float, s: Float, n: Int, gap: Float) {
    repeat(n) { i ->
        val x = (cx + (i - (n - 1) / 2f) * gap) * s
        val y = cy * s
        drawLine(
            C.Rain,
            Offset(x + s * 0.03f, y),
            Offset(x - s * 0.03f, y + s * 0.13f),
            strokeWidth = s * 0.05f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.flakes(cx: Float, cy: Float, s: Float, n: Int, r: Float = 0.045f) {
    repeat(n) { i ->
        val x = (cx + (i - (n - 1) / 2f) * 0.15f) * s
        val y = (cy + if (i % 2 == 0) 0f else 0.05f) * s
        drawCircle(C.Snow, r * s, Offset(x, y))
    }
}

private fun DrawScope.bolt(cx: Float, cy: Float, s: Float) {
    val x = cx * s
    val y = cy * s
    val p = Path().apply {
        moveTo(x + s * 0.06f, y - s * 0.02f)
        lineTo(x - s * 0.08f, y + s * 0.16f)
        lineTo(x + s * 0.01f, y + s * 0.16f)
        lineTo(x - s * 0.05f, y + s * 0.36f)
        lineTo(x + s * 0.11f, y + s * 0.11f)
        lineTo(x + s * 0.02f, y + s * 0.11f)
        close()
    }
    drawPath(p, C.Bolt)
}

private fun DrawScope.fogLines(cx: Float, cy: Float, s: Float) {
    repeat(3) { i ->
        val y = (cy + i * 0.10f) * s
        val half = (if (i == 1) 0.30f else 0.24f) * s
        drawLine(
            C.Fog.copy(alpha = 0.85f),
            Offset(cx * s - half, y),
            Offset(cx * s + half, y),
            strokeWidth = s * 0.055f,
            cap = StrokeCap.Round,
        )
    }
}
