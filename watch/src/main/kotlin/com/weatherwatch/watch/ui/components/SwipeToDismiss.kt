package com.weatherwatch.watch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 右滑返回。
 *
 * 这个手势**必须由 App 自己实现** —— ColorOS Watch 和 Wear OS 一样，
 * 系统不会替第三方应用处理右滑退出（Wear Compose 里之所以有
 * SwipeToDismissBox，正是这个原因）。不写就等于没有返回手势。
 *
 * 这里手写而不是引 androidx.wear.compose：那个库为 Wear OS 设计，
 * 在这块非 Wear OS 的表上行为不确定，而这个组件本身只有几十行。
 *
 * 只响应向右拖；向左不动，避免和将来可能的横向内容冲突。
 * 垂直分页不受影响：draggable 限定 Orientation.Horizontal，
 * 纵向拖动会先被 VerticalPager 吃掉。
 */
@Composable
fun SwipeToDismissBox(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var width by remember { mutableIntStateOf(1) }
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun settle(velocity: Float) {
        scope.launch {
            val farEnough = dragX.value > width * DISMISS_FRACTION
            val fastEnough = velocity > FLING_VELOCITY && dragX.value > width * MIN_FRACTION
            if (farEnough || fastEnough) {
                // 先滑出屏幕再退出，避免画面突然消失
                dragX.animateTo(width.toFloat(), tween(durationMillis = 140))
                onDismissed()
                dragX.snapTo(0f)
            } else {
                dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            dragX.snapTo((dragX.value + delta).coerceIn(0f, width.toFloat()))
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity -> settle(velocity) },
            ),
    ) {
        val progress = (dragX.value / width).coerceIn(0f, 1f)
        // 不需要额外屏蔽内部点击：Modifier.clickable 在指针移动超过
        // 触摸斜率时本来就会取消这次点击。
        Box(
            Modifier
                .offset { IntOffset(dragX.value.roundToInt(), 0) }
                // 跟手淡出，让手势有"正在退出"的反馈而不是单纯平移
                .alpha(1f - progress * 0.55f),
        ) {
            content()
        }
    }
}

/** 拖过屏宽的这个比例，松手即退出 */
private const val DISMISS_FRACTION = 0.33f

/** 快速甩动时的最低位移比例，防止轻微滑动误退出 */
private const val MIN_FRACTION = 0.08f

/** px/s */
private const val FLING_VELOCITY = 900f
