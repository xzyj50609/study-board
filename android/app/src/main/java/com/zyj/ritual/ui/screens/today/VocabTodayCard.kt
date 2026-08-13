package com.zyj.ritual.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.ui.components.ProgressBarLine
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualTypography

@Composable
fun VocabTodayCard(
    vocabState: VocabAggregateState,
    onAddWords: (Int) -> Unit,
    onAddReview: (Int) -> Unit,
    onOpenVocabBoard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = vocabState.state
    val config = vocabState.config
    val total = state.doneWords + state.remainWords
    // 全 App 只有 VocabCalculator.donePercent 一处算法，
    // 背词看板、进度页摘要卡、这张卡三处调的是同一个函数
    val percent = VocabCalculator.donePercent(
        doneWords = state.doneWords,
        totalWords = total,
        finished = state.finished,
    )
    // 打卡量跟着今天的状态走，不硬编码 20。今天要复习 45 个却只能一次点 +20，
    // 「定了多少 → 我完成了」这条路径就走不完
    val newAmount = state.todayQuota.coerceAtLeast(1)
    val reviewAmount = (state.reviewLeftToday?.takeIf { it > 0 } ?: state.todayQuota)
        .coerceAtLeast(1)

    Card(
        colors = CardDefaults.cardColors(containerColor = RitualColors.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标示与入口
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(RitualColors.accentGold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "背单词 · ${config.bookName}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = RitualColors.onBg
                    )
                }

                Text(
                    text = "背词看板 ›",
                    fontSize = 13.sp,
                    color = RitualColors.accentInk,
                    modifier = Modifier.clickable { onOpenVocabBoard() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 状态行：今日成果 & 目标
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "今天已背 ${state.todayWords} / ${state.todayQuota} 词",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = RitualColors.onBg
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        // 「已完成百分之多少」当主角，「还要学」退成副信息。
                        // 只说「剩 1503 词」的时候看着永远遥遥无期，看不到自己在推进
                        text = "已背 $percent% · 还要学 ${state.remainWords} 词",
                        fontSize = 12.sp,
                        color = RitualColors.onBgMuted
                    )
                }

                // 「跟上计划」这四个字看不出什么意思，换成能自己读懂的天数差
                val aheadText = when {
                    state.aheadDays > 0 -> "早 ${state.aheadDays} 天"
                    state.aheadDays < 0 -> "晚 ${-state.aheadDays} 天"
                    else -> "不早不晚"
                }
                Text(
                    text = aheadText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        state.aheadDays > 0 -> RitualColors.accentGold
                        state.aheadDays < 0 -> RitualColors.warn
                        else -> RitualColors.onBgMuted
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            ProgressBarLine(
                ratio = if (total > 0) state.doneWords.toFloat() / total else 0f,
                color = RitualColors.accentGold,
                height = 10.dp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 就地打卡按钮行 (+20 新词 / +20 复习)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(RitualRadius.button))
                        .background(RitualColors.accentInk.copy(alpha = 0.15f))
                        .clickable { onAddWords(newAmount) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$newAmount 新词",
                        style = RitualTypography.bodyMedium.copy(
                            color = RitualColors.accentInk,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(RitualRadius.button))
                        .background(RitualColors.accentReview.copy(alpha = 0.15f))
                        .clickable { onAddReview(reviewAmount) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$reviewAmount 复习",
                        style = RitualTypography.bodyMedium.copy(
                            color = RitualColors.accentReview,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
