package com.zyj.ritual.ui.screens.today

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.ui.components.AddAmountSheet
import com.zyj.ritual.ui.components.ProgressBarLine
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualTypography
import kotlinx.coroutines.delay

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
    val animatedPercent by animateIntAsState(
        targetValue = percent,
        animationSpec = tween(durationMillis = 1400),
        label = "vocabPercent",
    )
    val animatedToday by animateIntAsState(
        targetValue = state.todayWords,
        animationSpec = tween(durationMillis = 1400),
        label = "vocabToday",
    )
    var lastTodayWords by remember { mutableStateOf(state.todayWords) }
    var newDelta by remember { mutableStateOf(0) }
    LaunchedEffect(state.todayWords) {
        if (state.todayWords > lastTodayWords) {
            newDelta = state.todayWords - lastTodayWords
            delay(1400)
            newDelta = 0
        }
        lastTodayWords = state.todayWords
    }
    // 打卡量跟着今天的状态走，不硬编码 20。今天要复习 45 个却只能一次点 +20，
    // 「定了多少 → 我完成了」这条路径就走不完
    val newAmount = state.todayQuota.coerceAtLeast(1)
    val reviewAmount = (state.reviewLeftToday?.takeIf { it > 0 } ?: state.todayQuota)
        .coerceAtLeast(1)

    var sheet by remember { mutableStateOf<TodayAddTarget?>(null) }

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
                        text = "今天已背 $animatedToday / ${state.todayQuota} 词",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = RitualColors.onBg
                    )
                    if (newDelta > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+$newDelta 新词",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RitualColors.accentGold,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        // 「已完成百分之多少」当主角，「还要学」退成副信息。
                        // 只说「剩 1503 词」的时候看着永远遥遥无期，看不到自己在推进
                        text = "已背 $animatedPercent% · 还要学 ${state.remainWords} 词",
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
                durationMillis = 1400,
                pulseOnFinish = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 就地打卡按钮行。短按记预填量，长按改数量——预填量是计划额度（40），
            // 但用户在「不背单词」里多学一次是一组 20 个，不给改就只能记多一倍。
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TodayAddButton(
                    text = "+$newAmount 新词",
                    color = RitualColors.accentInk,
                    onClick = { onAddWords(newAmount) },
                    onLongClick = { sheet = TodayAddTarget.New },
                    modifier = Modifier.weight(1f),
                )

                TodayAddButton(
                    text = "+$reviewAmount 复习",
                    color = RitualColors.accentReview,
                    onClick = { onAddReview(reviewAmount) },
                    onLongClick = { sheet = TodayAddTarget.Review },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 长按在屏幕上看不见，这行小字就是这个功能的入口。删了它等于删了功能。
            Text(
                text = "长按可以改这次记多少个",
                fontSize = 11.sp,
                color = RitualColors.onBgFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (sheet) {
        TodayAddTarget.New -> AddAmountSheet(
            title = "记新词",
            initialAmount = newAmount,
            max = state.remainWords.coerceAtLeast(1),
            presets = TODAY_ADD_PRESETS,
            onDismiss = { sheet = null },
            onConfirm = { amount ->
                onAddWords(amount)
                sheet = null
            },
        )
        TodayAddTarget.Review -> AddAmountSheet(
            title = "记复习",
            initialAmount = reviewAmount,
            max = TODAY_REVIEW_ADD_MAX,
            presets = TODAY_ADD_PRESETS,
            onDismiss = { sheet = null },
            onConfirm = { amount ->
                onAddReview(amount)
                sheet = null
            },
        )
        null -> Unit
    }
}

/** 长按今日卡打卡按钮时，弹的是哪一个面板 */
private enum class TodayAddTarget { New, Review }

/** 快捷档：20 是「不背单词」一组的量，40 是用户当前计划额度 */
private val TODAY_ADD_PRESETS = listOf(10, 20, 40)

/** 复习没有天然总量，这个数只用来挡住手滑多打一位 */
private const val TODAY_REVIEW_ADD_MAX = 9_999

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayAddButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(RitualRadius.button))
            .background(color.copy(alpha = 0.15f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = RitualTypography.bodyMedium.copy(
                color = color,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
