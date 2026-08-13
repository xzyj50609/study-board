package com.zyj.ritual.ui.screens.vocab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.domain.vocab.VocabCalendarCell
import com.zyj.ritual.ui.theme.RitualColors

/** 「当天自己背的」淡蓝填充的浓度。 */
private const val OWN_FILL_ALPHA = 0.22f

/**
 * 「提前做掉的」淡绿填充的浓度，比蓝再淡一档。
 * 同样透明度下绿看着比蓝重，给一样的值会让上半截压着下半截。
 */
private const val AHEAD_FILL_ALPHA = 0.18f

/**
 * 单个背词日历格子 Component.
 *
 * 状态映射：
 * - 当天自己背的 (own) → 蓝 (accentInk)
 * - 提前做掉的 (ahead) → 绿 (accentGold，语义是「你赚到的」)
 * - 漏了的 (gap) → 红描边 (warn)
 * - 只复习没背新词 (reviewOnly) → 淡紫底 + 底部点 (accentReview)
 * - 今天 (isToday) → 粗体 + 深色外环
 *
 * ### ⚠️ 填充一律是淡色，字一律是深墨。别改回实心块压白字。
 *
 * 2026-08-07 第三轮改的。之前这里填的是**饱和实色**，格子过半就把数字翻成白色，
 * 一屏几十个格子铺下来是全 APP 最重的东西，用户两轮都判「丑 / 太深 / 很重」。
 * 换成淡色块之后，比例照样看得出来（填多高就是背了多少），但整屏立刻轻下来，
 * 而且数字始终是深墨压浅底，比原来的白字压色块还清楚。
 *
 * 淡到什么程度是有讲究的：绿比蓝再淡一档。等高的两段色块里绿看着总比蓝重，
 * 给一样的透明度会显得上半截压着下半截。
 */
@Composable
fun VocabCalendarCellView(
    cell: VocabCalendarCell.Day,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inkColor = RitualColors.accentInk
    val goldColor = RitualColors.accentGold
    val warnColor = RitualColors.warn
    val reviewColor = RitualColors.accentReview
    val bgSurface = RitualColors.surface
    val onBgText = RitualColors.onBg
    val onBgFaintText = RitualColors.onBgFaint
    val isLight = RitualColors.isLight

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(
                when {
                    cell.reviewOnly -> reviewColor.copy(alpha = if (isLight) 0.18f else 0.25f)
                    else -> bgSurface
                }
            )
            .then(
                if (cell.gap) Modifier.border(1.5.dp, warnColor.copy(alpha = 0.8f), shape)
                else if (cell.isToday) Modifier.border(2.dp, onBgText, shape)
                else Modifier
            )
            .clickable { onClick(cell.date) },
        contentAlignment = Alignment.Center
    ) {
        // 双色堆叠填充 (Canvas)。两段都是淡色，不是实心。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. 下半段：自己背的词 (own) -> 淡蓝
            val ownHeight = (height * cell.own.coerceIn(0.0, 1.0)).toFloat()
            if (ownHeight > 0f) {
                drawRect(
                    color = inkColor.copy(alpha = OWN_FILL_ALPHA),
                    topLeft = Offset(0f, height - ownHeight),
                    size = Size(width, ownHeight)
                )
            }

            // 2. 上半段：提前做掉的词 (ahead) -> 淡绿
            val fillHeight = (height * cell.fill.coerceIn(0.0, 1.0)).toFloat()
            val aheadHeight = (fillHeight - ownHeight).coerceAtLeast(0f)
            if (aheadHeight > 0f) {
                drawRect(
                    color = goldColor.copy(alpha = AHEAD_FILL_ALPHA),
                    topLeft = Offset(0f, height - fillHeight),
                    size = Size(width, aheadHeight)
                )
            }
        }

        // 日期数字。**永远是深墨**——底下是淡色块，不存在需要翻白字的情况。
        Text(
            text = cell.dayNum.toString(),
            fontSize = 12.sp,
            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
            color = onBgText,
        )

        // 旗标标示
        if (cell.isPlanFinish || cell.isProjFinish) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(6.dp)
                    .background(
                        if (cell.isPlanFinish) RitualColors.onBg else goldColor,
                        CircleShape
                    )
            )
        }

        // 复习日小圆点
        if (cell.reviewOnly) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .background(reviewColor, CircleShape)
            )
        }
    }
}
