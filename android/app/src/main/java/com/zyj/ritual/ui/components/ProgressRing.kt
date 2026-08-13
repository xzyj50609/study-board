package com.zyj.ritual.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualMotion
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography
import androidx.compose.ui.unit.sp

/**
 * 进度环（双段弧）。
 *
 * 蓝弧 = 已完成进度（overallProgress），金弧 = 额度（creditProgress）。
 * 两段都是从 12 点钟方向开始顺时针画。
 *
 * 设计规格：
 * - 直径 132dp，描边 13dp
 * - 底色：暖砂 9%（轨道）
 * - 蓝弧：accentInk（已完成）
 * - 金弧：accentGold（额度）
 * - 动画：480ms，cubic-bezier(0.2, 0.8, 0.2, 1)
 * - RoundCap 端点
 *
 * @param overallProgress 整体进度 0..1（蓝弧）
 * @param creditProgress 额度进度 0..1（金弧，叠在蓝弧上面）
 * @param centerText 中心大文字（百分比）
 * @param subText 中心小字（如 72 / 252）
 */
@Composable
fun ProgressRing(
    overallProgress: Float,
    creditProgress: Float,
    centerText: String,
    subText: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = RitualSize.ring,
    strokeWidth: androidx.compose.ui.unit.Dp = RitualSize.ringStroke,
) {
    val animatedOverall by animateFloatAsState(
        targetValue = overallProgress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = RitualMotion.ringDuration,
            easing = RitualMotion.standardEasing,
        ),
        label = "ring-overall",
    )
    val animatedCredit by animateFloatAsState(
        targetValue = creditProgress.coerceIn(0f, 1f - animatedOverall),
        animationSpec = tween(
            durationMillis = RitualMotion.ringDuration,
            easing = RitualMotion.standardEasing,
        ),
        label = "ring-credit",
    )

    val trackColor = RitualColors.onBg.copy(alpha = 0.09f)  // 和 divider 一致
    val blue = RitualColors.accentInk
    val gold = RitualColors.accentGold

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val halfStroke = strokePx / 2

            // 轨道（底色）
            drawArc(
                color = trackColor,
                startAngle = -90f,  // 从 12 点钟方向开始
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
            )

            // 蓝弧（已完成）
            if (animatedOverall > 0f) {
                drawArc(
                    color = blue,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedOverall,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
                )
            }

            // 金弧（额度）——叠在蓝弧后面
            if (animatedCredit > 0f) {
                drawArc(
                    color = gold,
                    startAngle = -90f + 360f * animatedOverall,
                    sweepAngle = 360f * animatedCredit,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(size.toPx() - strokePx, size.toPx() - strokePx),
                )
            }
        }

        // 中心文字
        Column {
            // "29.76%" 有 6 个字符，用 display(60sp) 会撑破 132dp 的环并折成两行。
            // 收到 h2 档并锁死单行，让它老实待在环里。
            Text(
                text = centerText,
                style = RitualTypography.displayLarge.copy(
                    fontSize = RitualTypeSize.h2,
                    fontWeight = FontWeight.Light,
                    fontFamily = RitualFontFamilies.num,
                    color = RitualColors.onBg,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = subText,
                style = RitualTypography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp.takeIf { it.value >= 0 } ?: 4.dp),
            )
        }
    }
}

// 简单 Box 里的列布局
@Composable
private fun Column(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
