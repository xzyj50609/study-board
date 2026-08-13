package com.zyj.ritual.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualMotion
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualTextStyles
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 任务行：左边圆点 + 任务名 + 右侧状态（NEXT / 时间戳）。
 *
 * 5 种状态：
 * - UNCHECKED：未完成，灰点 + 正常文字
 * - NEXT：下一项，蓝框蓝底 + NEXT 标签
 * - CHECKED：已完成，实心蓝点 + 灰化文字 + 时间戳
 * - BACKFILL：补打卡，实心点 + 「补记」标签
 * - DEFICIT：缺额待补，暗铜色
 *
 * 为什么用 sealed class 不用布尔组合：5 种状态是互斥的，
 * sealed class 让调用方不会拼出不存在的组合。
 */
sealed interface TaskRowState {
    data object Unchecked : TaskRowState
    data object Next : TaskRowState
    data class Checked(val timeText: String) : TaskRowState
    data class Backfill(val timeText: String) : TaskRowState
    data object Deficit : TaskRowState
}

@Composable
fun TaskRow(
    text: String,
    state: TaskRowState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isChecked = state is TaskRowState.Checked || state is TaskRowState.Backfill

    // 背景色
    val bgColor by animateColorAsState(
        targetValue = when (state) {
            is TaskRowState.Next -> RitualColors.accentInk.copy(alpha = 0.10f)
            is TaskRowState.Deficit -> RitualColors.warn.copy(alpha = 0.08f)
            is TaskRowState.Checked -> Color.Transparent
            is TaskRowState.Backfill -> Color.Transparent
            is TaskRowState.Unchecked -> RitualColors.surfaceLow
        },
        animationSpec = tween(durationMillis = RitualMotion.checkDuration),
        label = "taskrow-bg",
    )

    // 边框色
    val borderColor by animateColorAsState(
        targetValue = when (state) {
            is TaskRowState.Next -> RitualColors.accentInk.copy(alpha = 0.45f)
            is TaskRowState.Deficit -> RitualColors.warn.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        label = "taskrow-border",
    )

    // 文字色
    val textColor by animateColorAsState(
        targetValue = when {
            isChecked -> RitualColors.onBgMuted
            state is TaskRowState.Next -> RitualColors.onBg
            state is TaskRowState.Deficit -> RitualColors.onBg
            else -> RitualColors.onBg
        },
        label = "taskrow-text",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(RitualRadius.card))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(RitualRadius.card))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskDot(state = state)
            Spacer(Modifier.width(14.dp))
            Text(
                text = text,
                style = RitualTypography.bodyLarge.copy(
                    color = textColor,
                    fontWeight = if (state is TaskRowState.Next) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 2,
            )
        }

        // 右侧：NEXT 标签 / 时间戳 / 补记标签
        AnimatedContent(targetState = state, label = "taskrow-trailing") { s ->
            when (s) {
                is TaskRowState.Next -> {
                    Text(
                        "NEXT",
                        style = RitualTypography.labelSmall.copy(
                            color = RitualColors.accentInk,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                is TaskRowState.Checked -> {
                    Text(s.timeText, style = RitualTextStyles.stamp)
                }
                is TaskRowState.Backfill -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "补记",
                            style = RitualTypography.bodySmall.copy(
                                color = RitualColors.accentGold,
                                fontWeight = FontWeight.Medium,
                                fontSize = RitualTypeSize.caption,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.timeText, style = RitualTextStyles.stamp)
                    }
                }
                else -> {}
            }
        }
    }
}

/**
 * 任务行左边的圆点。
 * 未完成 = 空心圈；已完成 = 实心蓝点；补记 = 实心金点；NEXT = 空心蓝圈（粗）。
 */
@Composable
private fun TaskDot(state: TaskRowState) {
    val isChecked = state is TaskRowState.Checked || state is TaskRowState.Backfill
    val isBackfill = state is TaskRowState.Backfill
    val isNext = state is TaskRowState.Next
    val isDeficit = state is TaskRowState.Deficit

    val dotColor = when {
        isBackfill -> RitualColors.accentGold
        isChecked -> RitualColors.accentInk
        isNext -> RitualColors.accentInk
        isDeficit -> RitualColors.warn
        else -> RitualColors.onBgFaint
    }

    val strokeWidth = if (isNext) 2.dp else 1.5.dp

    Box(
        modifier = Modifier
            .size(RitualSize.checkDot)
            .clip(CircleShape)
            .background(
                if (isChecked) dotColor else Color.Transparent
            )
            .border(
                width = strokeWidth,
                color = dotColor,
                shape = CircleShape,
            ),
    )
}
