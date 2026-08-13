package com.zyj.ritual.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyj.ritual.domain.model.HistoryEvent
import com.zyj.ritual.domain.model.TimelineEntry
import com.zyj.ritual.domain.vocab.VocabRecord
import com.zyj.ritual.ui.components.HistoryRow
import com.zyj.ritual.ui.components.formatStamp
import com.zyj.ritual.ui.components.rowBadge
import com.zyj.ritual.ui.components.rowTitle
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 历史二级页。
 *
 * 原来这块挂在进度页底部，平常不看却一直占地方，而且硬编码 `.take(30)`。
 * 挪到二级页之后取消条数限制——历史就该是完整流水。
 *
 * 读文章和背单词的记录混排，因为"我这段时间干了啥"本来就是一件事，
 * 只显示一半反而更奇怪。
 *
 * 页面骨架照 ArticleScreen 那套：纯文字「‹ 返回」+ popBackStack，
 * 全 App 不用图标（BottomNav.kt:32-37 有裁决记录）。
 */
@Composable
fun HistoryScreen(
    history: List<HistoryEvent>,
    vocabRecords: List<VocabRecord>,
    onBack: () -> Unit,
) {
    val entries = remember(history, vocabRecords) {
        TimelineEntry.merge(history, vocabRecords)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RitualSpace.screenPadding),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))

        Text(
            "‹ 返回",
            style = RitualTypography.bodyLarge.copy(color = RitualColors.onBgMuted),
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp),
        )

        Spacer(Modifier.height(RitualSpace.listGap))
        Text("历史", style = RitualTypography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "共 ${entries.size} 条 · 读文章和背单词混排",
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        if (entries.isEmpty()) {
            Text(
                "还没有记录 · 勾掉第一项就会出现在这里",
                style = RitualTypography.bodySmall,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { entry ->
                    HistoryRow(
                        title = entry.rowTitle(),
                        // 没有时刻的记录这里是空串，那一行就只有标题——
                        // 看得出来它跟别的不一样，不会假装自己知道几点
                        time = formatStamp(entry.at),
                        badge = entry.rowBadge(),
                        badgeColor = when (entry) {
                            is TimelineEntry.Vocab ->
                                if (entry.isReview) RitualColors.accentReview
                                else RitualColors.accentGold
                            is TimelineEntry.Article -> RitualColors.onBg
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(RitualSpace.navBarHeight + 32.dp))
    }
}
