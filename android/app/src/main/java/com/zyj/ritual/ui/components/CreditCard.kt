package com.zyj.ritual.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.domain.model.CreditState
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTextStyles
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography
import java.time.LocalDate

/**
 * 额度卡（三态）。
 *
 * - AHEAD：金卡，+N 天，原定完成日 / 现在预计完成日
 * - EVEN：中性卡，"正好跟上计划"
 * - DEFICIT：暗铜卡，"缺 N 项"，最早缺口日期，两个操作按钮
 */
@Composable
fun CreditCard(
    state: CreditState,
    creditDaysText: String,
    deficitCount: Int,
    earliestDeficitDate: LocalDate?,
    baseCompletionDate: LocalDate,
    expectedCompletionDate: LocalDate,
    onBackfillFirst: () -> Unit,
    onReschedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CreditState.AHEAD -> CreditCardAhead(
            creditDaysText = creditDaysText,
            baseDate = baseCompletionDate,
            expectedDate = expectedCompletionDate,
            modifier = modifier,
        )
        CreditState.EVEN -> CreditCardEven(
            expectedDate = expectedCompletionDate,
            modifier = modifier,
        )
        CreditState.DEFICIT -> CreditCardDeficit(
            deficitCount = deficitCount,
            earliestDate = earliestDeficitDate,
            onBackfillFirst = onBackfillFirst,
            onReschedule = onReschedule,
            modifier = modifier,
        )
        CreditState.NOT_STARTED -> CreditCardNotStarted(
            startDate = baseCompletionDate,
            modifier = modifier,
        )
    }
}

// ——— 领先 ———

@Composable
private fun CreditCardAhead(
    creditDaysText: String,
    baseDate: LocalDate,
    expectedDate: LocalDate,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.accentGold.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = RitualColors.accentGold.copy(alpha = 0.25f),
                shape = RoundedCornerShape(RitualRadius.cardLg),
            )
            .padding(RitualSpace.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 金环 +2
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RitualColors.accentGold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    creditDaysText,
                    style = RitualTypography.titleMedium.copy(
                        color = RitualColors.accentGold,
                        fontWeight = FontWeight.Medium,
                        fontFamily = RitualFontFamilies.num,
                    ),
                )
            }
            Spacer(Modifier.width(RitualSpace.cardPadding))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "你已经领先计划 $creditDaysText 天",
                    style = RitualTypography.bodyLarge.copy(
                        color = RitualColors.onBg,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    "原定 ${formatDateShort(baseDate)} 读完，现在预计 ${formatDateShort(expectedDate)}",
                    style = RitualTypography.bodySmall,
                )
            }
        }
    }
}

// ——— 齐平 ———

@Composable
private fun CreditCardEven(
    expectedDate: LocalDate,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RitualColors.surfaceSel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "=",
                    style = RitualTypography.titleMedium.copy(
                        color = RitualColors.onBgMuted,
                        fontWeight = FontWeight.Medium,
                        fontFamily = RitualFontFamilies.num,
                    ),
                )
            }
            Spacer(Modifier.width(RitualSpace.cardPadding))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "正好跟上计划",
                    style = RitualTypography.bodyLarge.copy(
                        color = RitualColors.onBg,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    "预计 ${formatDateShort(expectedDate)} 完成",
                    style = RitualTypography.bodySmall,
                )
            }
        }
    }
}

// ——— 缺额 ———

@Composable
private fun CreditCardDeficit(
    deficitCount: Int,
    earliestDate: LocalDate?,
    onBackfillFirst: () -> Unit,
    onReschedule: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.warn.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = RitualColors.warn.copy(alpha = 0.35f),
                shape = RoundedCornerShape(RitualRadius.cardLg),
            )
            .padding(RitualSpace.cardPadding),
    ) {
        Text(
            "额度已用完，缺 $deficitCount 项",
            style = RitualTypography.bodyLarge.copy(
                color = RitualColors.warnText,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (earliestDate != null) {
            Text(
                "最早缺口 ${formatDateShort(earliestDate)}",
                style = RitualTypography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(RitualSpace.cardPadding))
        Row(
            horizontalArrangement = Arrangement.spacedBy(RitualSpace.listGap),
        ) {
            // 主操作：优先补上
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(RitualRadius.button))
                    .background(RitualColors.warnText)
                    .clickable(onClick = onBackfillFirst),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "优先补上",
                    style = RitualTypography.bodyMedium.copy(
                        color = RitualColors.onAccent,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            // 次操作：重新排期
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(RitualRadius.button))
                    .border(
                        width = 1.dp,
                        color = RitualColors.warn.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(RitualRadius.button),
                    )
                    .clickable(onClick = onReschedule),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "重新排期",
                    style = RitualTypography.bodyMedium.copy(
                        color = RitualColors.warnText,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

// ——— 未开始 ———

@Composable
private fun CreditCardNotStarted(
    startDate: LocalDate,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "计划将于 ${formatDateShort(startDate)} 开始",
                style = RitualTypography.bodyLarge,
            )
        }
    }
}

private fun formatDateShort(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日"
