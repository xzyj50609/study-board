package com.zyj.ritual.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.calculator.CreditCalculator
import com.zyj.ritual.domain.calculator.TodayCopyResolver
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.ui.components.ProgressBarLine
import com.zyj.ritual.ui.components.SegmentedProgressBar
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTextStyles
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 进度页。
 *
 * 读文章：60sp 百分比 + 双段进度条 + 42 格矩阵 + ETA 卡。
 * 背单词：一张摘要卡（点进去是背词看板那张全的）。
 *
 * 历史记录原来挂在这一页最下面，现在挪到右上角「历史 ›」的二级页了——
 * 平常不看的东西不该一直占地方，而且原来那块硬编码只取 30 条。
 */
@Composable
fun ProgressScreen(
    state: TodayState,
    vocabState: VocabAggregateState? = null,
    onOpenHistory: () -> Unit = {},
    onOpenVocabBoard: () -> Unit = {},
) {
    val progress = state.progress
    val credit = state.credit
    val plan = state.plan
    val records = state.records

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RitualSpace.screenPadding),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("进度", style = RitualTypography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "历史 ›",
                style = RitualTypography.bodyLarge.copy(color = RitualColors.accentInk),
                modifier = Modifier
                    .clickable(onClick = onOpenHistory)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))

        Text(
            "读文章",
            style = RitualTypography.titleMedium.copy(color = RitualColors.onBgMuted),
        )
        Spacer(Modifier.height(RitualSpace.listGap))

        // 60sp 百分比
        Text(
            "${"%.2f".format(progress.overallProgress * 100)}%",
            style = RitualTypography.displayLarge.copy(
                fontSize = RitualTypeSize.display,
                fontFamily = RitualFontFamilies.num,
                color = RitualColors.onBg,
            ),
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        // 双段进度条：蓝=已完成，金=额度。用 Row+weight 分配，
        // 老版本靠 padding(start = (overall*100).dp) 偏移，那是把比例当 dp 用，屏幕宽度一变就飘
        SegmentedProgressBar(
            firstRatio = progress.totalCompleted.toFloat() / progress.totalTasks,
            secondRatio = if (credit.state == com.zyj.ritual.domain.model.CreditState.AHEAD) {
                credit.creditTasks.toFloat() / progress.totalTasks
            } else 0f,
            firstColor = RitualColors.accentInk,
            secondColor = RitualColors.accentGold,
            height = RitualSize.progressBar,
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        Text(
            "${progress.totalCompleted} / ${progress.totalTasks} 项 · ${progress.completeArticles} / ${plan.totalArticles} 篇",
            style = RitualTypography.bodyMedium,
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 42 格矩阵
        ArticleMatrix(
            totalArticles = plan.totalArticles,
            completedBeforeStart = plan.completedBeforeStart,
            records = records,
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // ETA 卡
        ETACard(
            baseDate = credit.baseCompletionDate,
            expectedDate = credit.expectedCompletionDate,
            creditDays = CreditCalculator.formatCreditDays(credit.creditDays),
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // ── 背单词 ──
        if (vocabState != null) {
            Text(
                "背单词",
                style = RitualTypography.titleMedium.copy(color = RitualColors.onBgMuted),
            )
            Spacer(Modifier.height(RitualSpace.listGap))
            VocabSummaryCard(vocabState, onClick = onOpenVocabBoard)
        }

        Spacer(Modifier.height(RitualSpace.navBarHeight + 32.dp))
    }
}

/**
 * 背单词摘要卡。点进去是背词看板那张全的。
 *
 * ⚠️ 百分比必须调 `VocabCalculator.donePercent`，不许在这里再算一遍。
 * 两处各算一次的时候，平时都是「20%」看不出问题，
 * 只有边界值（1882/1883）才会一处 99% 一处 100%。
 */
@Composable
private fun VocabSummaryCard(
    vocabState: VocabAggregateState,
    onClick: () -> Unit,
) {
    val s = vocabState.state
    val total = s.doneWords + s.remainWords
    val percent = VocabCalculator.donePercent(
        doneWords = s.doneWords,
        totalWords = total,
        finished = s.finished,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .clickable(onClick = onClick)
            .padding(RitualSpace.cardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                vocabState.config.bookName,
                style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "›",
                style = RitualTypography.titleMedium.copy(color = RitualColors.onBgMuted),
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$percent%",
                style = RitualTypography.displaySmall.copy(
                    fontFamily = RitualFontFamilies.num,
                    color = RitualColors.accentGold,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${s.doneWords} / $total 词",
                style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(RitualSpace.listGap))

        ProgressBarLine(
            ratio = if (total > 0) s.doneWords.toFloat() / total else 0f,
            color = RitualColors.accentGold,
            height = RitualSize.progressBar,
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        Text(
            // 复习没有总量（SRS 每天重算），所以这里只报档位和累计，不报百分比
            "复习 第 ${s.reviewLevel} 档 · 累计 ${s.reviewTotal} 个",
            style = RitualTypography.bodyMedium.copy(color = RitualColors.accentReview),
        )
    }
}

// ———————————— 子组件 ————————————

@Composable
private fun ArticleMatrix(
    totalArticles: Int,
    completedBeforeStart: Int,
    records: List<com.zyj.ritual.domain.model.TaskRecord>,
) {
    val completedArticles = (1..totalArticles).map { art ->
        val count = records.count { it.articleIndex == art }
        when {
            count >= 6 -> 2  // 完成
            count > 0 -> 1   // 进行中
            else -> 0          // 未开始
        }
    }

    // 方块是固定 28dp 的，7 列加起来永远窄于屏幕，剩下的空当全堆在右边——
    // 靠左贴着看起来像没排版好。整块居中（2026-08-06 用户点名）。
    // 最后一行不满 7 格的位置是用等宽 Spacer 占着的，每行宽度都一样，
    // 所以只要 Column 居中，整块就正了——别再往 Row 上加 Arrangement.Center，
    // 那会把最后一行相对上面几行挪一段，格子编号就对不齐列了。
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val rows = (totalArticles + 6) / 7
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    if (index < totalArticles) {
                        val status = completedArticles[index]
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (status) {
                                        2 -> RitualColors.accentInk
                                        1 -> RitualColors.accentInk.copy(alpha = 0.4f)
                                        else -> RitualColors.surfaceLow
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                style = RitualTextStyles.calendarTag.copy(
                                    // onAccent 是给「压在实心强调色上」用的。进行中那格底色是
                                    // 40% 透明的强调色，不是实心——照搬 onAccent 会让编号和底
                                    // 几乎一个亮度，深浅两版都糊（2026-08-07 截图查出来的）。
                                    color = when (status) {
                                        2 -> RitualColors.onAccent
                                        1 -> RitualColors.onBg
                                        else -> RitualColors.onBgFaint
                                    },
                                ),
                            )
                        }
                    } else {
                        Spacer(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ETACard(
    baseDate: java.time.LocalDate,
    expectedDate: java.time.LocalDate,
    creditDays: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
    ) {
        Text(
            "预计完成",
            style = RitualTypography.titleMedium,
        )
        Spacer(Modifier.height(RitualSpace.listGap))
        InfoRow("基础计划完成日", "${baseDate.monthValue}月${baseDate.dayOfMonth}日")
        InfoRow("按当前速度预计", "${expectedDate.monthValue}月${expectedDate.dayOfMonth}日")
        InfoRow("当前额度", creditDays)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted))
        Text(value, style = RitualTypography.bodyMedium)
    }
}

// HistoryRow / HistoryEventType.label() / formatStamp 已搬到
// ui/components/HistoryRow.kt 改成 public——历史现在是二级页，两处都要用。
