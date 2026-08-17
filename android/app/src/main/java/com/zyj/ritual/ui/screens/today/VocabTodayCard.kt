package com.zyj.ritual.ui.screens.today

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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

            // 打卡按钮：主体记今天的额度，右端 ▾ 点开改数量。
            // 实际背了 28 个、8 个都得记得下来，所以改数量的入口必须看得见——
            // 上一版藏在长按里，用户装上之后根本没发现有这功能。
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TodayAddButton(
                    text = "+$newAmount 新词",
                    color = RitualColors.accentInk,
                    onClick = { onAddWords(newAmount) },
                    onPickAmount = { sheet = TodayAddTarget.New },
                    modifier = Modifier.weight(1f),
                )

                TodayAddButton(
                    text = "+$reviewAmount 复习",
                    color = RitualColors.accentReview,
                    onClick = { onAddReview(reviewAmount) },
                    onPickAmount = { sheet = TodayAddTarget.Review },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "背了几个就记几个，点 ▾ 改",
                fontSize = 11.sp,
                color = RitualColors.onBgFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (sheet) {
        TodayAddTarget.New -> AddAmountSheet(
            title = "这次背了几个新词",
            initialAmount = newAmount,
            max = state.remainWords.coerceAtLeast(1),
            todayDone = state.todayWords,
            todayTarget = state.todayQuota,
            onDismiss = { sheet = null },
            onConfirm = { amount ->
                onAddWords(amount)
                sheet = null
            },
        )
        TodayAddTarget.Review -> AddAmountSheet(
            title = "这次复习了几个",
            initialAmount = reviewAmount,
            max = TODAY_REVIEW_ADD_MAX,
            todayDone = state.todayReview,
            todayTarget = state.reviewDueToday,
            onDismiss = { sheet = null },
            onConfirm = { amount ->
                onAddReview(amount)
                sheet = null
            },
        )
        null -> Unit
    }
}

/** 点今日卡的「改」时，弹的是哪一个面板 */
private enum class TodayAddTarget { New, Review }

/** 复习没有天然总量，这个数只用来挡住手滑多打一位 */
private const val TODAY_REVIEW_ADD_MAX = 9_999

/**
 * 打卡按钮：主体记预填量，右端 `▾` 点开改数量。
 * `▾` 是这个 App 已有的「点这里挑一个值」记号（见 RitualDatePicker），
 * 长在按钮内部不占额外位置——之前在旁边加「改」小方框，四个元素挤一行，太丑。
 */
@Composable
private fun TodayAddButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onPickAmount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(RitualRadius.button))
            .background(color.copy(alpha = 0.15f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = RitualTypography.bodyMedium.copy(
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(onClick = onPickAmount)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▾",
                style = RitualTypography.bodyMedium.copy(
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
