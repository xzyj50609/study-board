package com.zyj.ritual.ui.screens.article

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.domain.calculator.TodayCopyResolver
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.ui.components.SegmentedTicks
import com.zyj.ritual.ui.components.TaskRow
import com.zyj.ritual.ui.components.TaskRowState
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 文章任务详情页。
 *
 * 展示某一篇全部 6 项任务的完成状态和时间戳，
 * 支持就地勾选和撤销。
 */
@Composable
fun ArticleScreen(
    articleIndex: Int,
    state: TodayState,
    onCheckTask: (Int, Int) -> Unit,
    onUndoTask: (Int, Int) -> Unit,
    onBack: () -> Unit,
) {
    val plan = state.plan
    val records = state.records

    // 这篇的 6 项完成情况
    val articleRecords = remember(articleIndex, records) {
        records
            .filter { it.articleIndex == articleIndex }
            .associateBy { it.taskIndex }
    }
    val completedCount = articleRecords.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RitualSpace.screenPadding),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))

        // 返回
        Text(
            "‹ 返回",
            style = RitualTypography.bodyMedium.copy(color = RitualColors.accentInk),
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(onClick = onBack),
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        // 标题
        Text(
            "第 $articleIndex 篇",
            style = RitualTypography.headlineMedium,
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        // 进度条
        SegmentedTicks(
            completedCount = completedCount,
            total = plan.tasksPerArticle,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "$completedCount/${plan.tasksPerArticle} 项 · " +
                "${"%.2f".format(completedCount.toDouble() / plan.tasksPerArticle * 100)}%",
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 第一天 · 3 项
        TaskGroup(
            title = "第一天 · ${plan.tasksPerDay} 项",
            tasks = (1..plan.tasksPerDay).toList(),
            articleIndex = articleIndex,
            recordsMap = articleRecords,
            onClickTask = { task ->
                val isDone = articleRecords.containsKey(task)
                if (isDone) onUndoTask(articleIndex, task)
                else onCheckTask(articleIndex, task)
            },
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 第二天 · 3 项
        if (plan.daysPerArticle >= 2) {
            TaskGroup(
                title = "第二天 · ${plan.tasksPerDay} 项",
                tasks = (plan.tasksPerDay + 1..plan.tasksPerDay * 2).toList(),
                articleIndex = articleIndex,
                recordsMap = articleRecords,
                onClickTask = { task ->
                    val isDone = articleRecords.containsKey(task)
                    if (isDone) onUndoTask(articleIndex, task)
                    else onCheckTask(articleIndex, task)
                },
            )
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 说明
        Text(
            "每篇 6 个小任务，全部完成才算完整读完一篇。",
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
        )

        Spacer(Modifier.height(RitualSpace.navBarHeight + 32.dp))
    }
}

// ———————————— 子组件 ————————————

@Composable
private fun TaskGroup(
    title: String,
    tasks: List<Int>,
    articleIndex: Int,
    recordsMap: Map<Int, com.zyj.ritual.domain.model.TaskRecord>,
    onClickTask: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = RitualTypography.labelSmall,
        )
        Spacer(Modifier.height(RitualSpace.listGap))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { taskIndex ->
                val record = recordsMap[taskIndex]
                val state = when {
                    record == null -> TaskRowState.Unchecked
                    record.source == RecordSource.BACKFILL ->
                        TaskRowState.Backfill(formatStamp(record.completedAt))
                    else ->
                        TaskRowState.Checked(formatStamp(record.completedAt))
                }
                TaskRow(
                    text = TodayCopyResolver.taskName(taskIndex),
                    state = state,
                    onClick = { onClickTask(taskIndex) },
                )
            }
        }
    }
}

private fun formatStamp(instant: java.time.Instant?): String {
    if (instant == null) return ""
    val zdt = instant.atZone(ZoneId.of("Asia/Shanghai"))
    return String.format(
        "%02d.%02d %02d:%02d",
        zdt.monthValue, zdt.dayOfMonth, zdt.hour, zdt.minute
    )
}
