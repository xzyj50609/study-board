package com.zyj.ritual.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualMotion
import com.zyj.ritual.ui.theme.RitualSize

/**
 * 当前文章的 6 段刻度进度条。
 *
 * 6 个胶囊，每个 5dp 高，间隔 4dp。
 * 已完成的亮（月光蓝），未完成的暗（轨道色）。
 */
@Composable
fun SegmentedTicks(
    completedCount: Int,
    total: Int = 6,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            val isDone = index < completedCount
            val color by animateColorAsState(
                targetValue = if (isDone) RitualColors.accentInk
                              else RitualColors.onBg.copy(alpha = 0.09f),
                animationSpec = tween(durationMillis = RitualMotion.barDuration),
                label = "tick-$index",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(RitualSize.articleBar)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}
