package com.weatherwatch.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.weatherwatch.watch.ui.WatchApp

class MainActivity : ComponentActivity() {

    private val controller by lazy { (application as WeatherApp).controller }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val d = LocalDensity.current
            // 系统字体调大时主温度会撑破屏幕。限幅到 1.15，
            // 既尊重无障碍设置，又保证 186dp 宽的版面不塌。
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.15f)),
            ) {
                WatchApp(controller)
            }
        }
    }

    /**
     * 同步的主力触发点。ColorOS 杀后台很凶，定时任务不可靠，
     * 但用户抬腕看表这个动作本身就是最好的时机。
     */
    override fun onStart() {
        super.onStart()
        controller.onAppOpen()
    }
}
