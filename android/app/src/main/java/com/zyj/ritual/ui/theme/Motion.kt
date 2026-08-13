package com.zyj.ritual.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp

/**
 * 动效参数。
 * 全部从 design-tokens.json 生成。
 * 设计明确禁用：模糊、视差、常驻循环动画。
 * 唯一允许的循环类效果：额度增加时金弧呼吸一次（不循环）。
 */
object RitualMotion {
    val checkDuration = 220
    val ringDuration = 480
    val barDuration = 450
    val sheetDuration = 260
    val monthSwitchDuration = 220
    val pageTransitionDuration = 240
    val pageTransitionOffset = 12.dp

    // 设计统一用的缓动：cubic-bezier(0.2, 0.8, 0.2, 1)
    // 这其实就是 Material 的 FastOutSlowInEasing，写死在这里确保和设计一致
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    // 切月用 easeOut
    val easeOutEasing: Easing = FastOutSlowInEasing
}
