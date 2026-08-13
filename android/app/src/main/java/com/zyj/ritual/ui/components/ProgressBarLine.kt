package com.zyj.ritual.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualMotion
import kotlin.math.abs


/**
 * 通用横向进度条。
 *
 * 全 App 只此一条实现，四处在用：背词的已背条、复习累计里程碑条、今天的复习条、
 * 进度页读文章那条（双段版在下面）。
 *
 * 以前进度页那条是 private 的，别处想用只能各抄一份——抄出来的版本迟早在
 * 圆角、轨道底色、动画时长上跟这条对不上，看着像两个控件。
 */
@Composable
fun ProgressBarLine(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = RitualColors.onBg.copy(alpha = 0.09f),
    animate: Boolean = true,
    durationMillis: Int = RitualMotion.barDuration,
    pulseOnFinish: Boolean = false,
) {
    val target = ratio.coerceIn(0f, 1f)
    // 首次组合时 animateFloatAsState 直接就是目标值（只有后续变化才补间），
    // 所以截图测试拍到的是终态，不会拍到一条空条
    val shown = if (animate) {
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = RitualMotion.standardEasing,
            ),
            label = "progress",
        ).value
    } else target

    // 轻脉冲：只在到达目标后脉冲一次。首帧不算，避免截图测试和初始渲染也跳一下。
    val scale = remember { Animatable(1f) }
    val firstFrame = remember { mutableStateOf(true) }
    LaunchedEffect(shown, target) {
        val reached = if (animate) abs(shown - target) < 0.0005f else true
        if (pulseOnFinish && reached) {
            if (firstFrame.value) {
                firstFrame.value = false
            } else if (target > 0f) {
                scale.snapTo(1f)
                scale.animateTo(1.045f, animationSpec = tween(130, easing = FastOutSlowInEasing))
                scale.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
            }
        }
    }

    Box(
        modifier = modifier
            .scale(scale.value)
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor),
    ) {
        // 0 宽度时不画：fillMaxWidth(0f) 在某些密度下仍会露出一个圆角小点，
        // 看起来像"已经有一点进度了"
        if (shown > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

/**
 * 双段进度条：前一段是已完成，后一段接着画额度。
 *
 * 用 Row + weight 分配，不是靠 `padding(start = (overall * 100).dp)` 去偏移——
 * 那个老写法把「比例」当「dp」用了，屏幕宽度一变位置就飘，
 * 而且 100dp 只在某个特定宽度上碰巧对得上。
 *
 * weight 不接受 0，所以每段都要先判空；三段合起来恒等于 1。
 */
@Composable
fun SegmentedProgressBar(
    firstRatio: Float,
    secondRatio: Float,
    firstColor: Color,
    secondColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = RitualColors.onBg.copy(alpha = 0.09f),
) {
    val first = firstRatio.coerceIn(0f, 1f)
    val second = secondRatio.coerceIn(0f, 1f - first)
    val rest = (1f - first - second).coerceAtLeast(0f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor),
    ) {
        if (first > 0f) {
            Box(
                Modifier
                    .weight(first)
                    .fillMaxHeight()
                    .background(firstColor)
            )
        }
        if (second > 0f) {
            Box(
                Modifier
                    .weight(second)
                    .fillMaxHeight()
                    .background(secondColor)
            )
        }
        if (rest > 0f) {
            Spacer(Modifier.weight(rest))
        }
    }
}
