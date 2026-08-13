package com.zyj.ritual.ui.screens.today

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.domain.calculator.CreditCalculator
import com.zyj.ritual.domain.calculator.TodayCopyResolver
import com.zyj.ritual.domain.model.CreditState
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import com.zyj.ritual.ui.components.CreditCard
import com.zyj.ritual.ui.components.ProgressRing
import com.zyj.ritual.ui.components.SegmentedTicks
import com.zyj.ritual.ui.components.TaskRow
import com.zyj.ritual.ui.components.TaskRowState
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTextStyles
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 今日学习页。
 *
 * 从上到下：
 * 1. 日期行 + 设置入口
 * 2. 标题
 * 3. 主区：进度环 + 右侧篇数和刻度
 * 4. 额度卡
 * 5. 第一天任务组（今日计划）
 * 6. 第二天任务组（可提前）
 * 7. 底部导航（在外层 Scaffold）
 *
 * 勾任务只有一个入口：点任务行本身（行首那个圈）。
 * 2026-08-06 之前还有一个悬浮在底部的大药丸按钮，用户点名删了，见下面 Column 之后的注释。
 */
@Composable
fun TodayScreen(
    state: TodayState,
    onCheckTask: (Int, Int) -> Unit,
    onUndoTask: (Int, Int) -> Unit,
    onOpenArticle: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBackfillFirst: () -> Unit = {},
    onReschedule: () -> Unit = {},
    vocabState: com.zyj.ritual.data.repository.VocabAggregateState? = null,
    onAddVocabWords: (Int) -> Unit = {},
    onAddVocabReview: (Int) -> Unit = {},
    onOpenVocabBoard: () -> Unit = {},
) {
    // 撤销确认弹窗状态
    var undoDialog by remember { mutableStateOf<UndoDialogState?>(null) }
    // 重排确认弹窗状态
    var showRescheduleDialog by remember { mutableStateOf(false) }

    val plan = state.plan
    val progress = state.progress
    val credit = state.credit
    val copy = state.copy
    val records = state.records
    val calendar = state.calendar
    val today = state.today

    // 当前篇的 6 项完成状态
    val completedInArticle = remember(records, progress.currentArticle) {
        val set = mutableSetOf<Int>()
        records.forEach { r ->
            if (r.articleIndex == progress.currentArticle) set.add(r.taskIndex)
        }
        set.toSet()
    }

    // 今日计划日的相位和任务
    val todayPlan = calendar.getDayPlan(today)
    val todayPhase = todayPlan.phase

    // 下一项（用于 NEXT 标记）—— 当前篇第一个未完成的
    val nextTaskInArticle = (1..plan.tasksPerArticle).firstOrNull { it !in completedInArticle }

    // 完成页替换
    if (progress.isAllDone) {
        DoneScreenContent(state = state)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))
        Spacer(Modifier.height(RitualSpace.unit * 2))

        // —— 顶部：日期行 + 设置 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RitualSpace.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatDateLong(today),
                style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "设置",
                style = RitualTypography.bodyMedium.copy(color = RitualColors.accentInk),
                modifier = Modifier.clickable(onClick = onNavigateToSettings),
            )
        }

        // —— 标题 ——
        Text(
            copy.headline,
            style = RitualTypography.headlineMedium,
            modifier = Modifier
                .padding(horizontal = RitualSpace.screenPadding)
                .padding(top = RitualSpace.listGap),
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // —— 主区：进度环 + 右侧信息 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RitualSpace.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 进度环
            ProgressRing(
                overallProgress = progress.overallProgress.toFloat(),
                creditProgress = creditProgress(credit, progress.totalTasks),
                centerText = "${ProgressCalculator.format(progress.overallProgress)}%",
                subText = "${progress.totalCompleted} / ${progress.totalTasks}",
                modifier = Modifier.width(132.dp),
            )

            Spacer(Modifier.width(RitualSpace.sectionGap))

            // 右侧：完整篇数 + 当前篇刻度
            Column(modifier = Modifier.weight(1f)) {
                // 完整篇数
                Text(
                    "完整学完",
                    style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${progress.completeArticles}",
                        style = RitualTypography.headlineSmall.copy(
                            fontFamily = RitualFontFamilies.num,
                            fontWeight = FontWeight.Light,
                            color = RitualColors.onBg,
                            fontSize = RitualTypeSize.h3,
                        ),
                    )
                    Text(
                        " / ${plan.totalArticles} 篇",
                        style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                Spacer(Modifier.height(RitualSpace.listGap))

                // 分割线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(RitualColors.divider),
                )

                Spacer(Modifier.height(RitualSpace.unit * 2))

                // 当前篇
                Text(
                    "当前第 ${progress.currentArticle} 篇",
                    style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                )
                Spacer(Modifier.height(8.dp))
                SegmentedTicks(
                    completedCount = progress.currentArticleCompleted,
                    total = plan.tasksPerArticle,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${progress.currentArticleCompleted}/${plan.tasksPerArticle} 项 · " +
                        "${"%.2f".format(progress.currentArticleProgress * 100)}%",
                    style = RitualTextStyles.stamp,
                )

                Spacer(Modifier.height(RitualSpace.listGap))
                Text(
                    "文章详情 ›",
                    style = RitualTypography.bodySmall.copy(
                        color = RitualColors.accentInk,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.clickable(onClick = onOpenArticle),
                )
            }
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // —— 额度卡 ——
        Box(Modifier.padding(horizontal = RitualSpace.screenPadding)) {
            CreditCard(
                state = credit.state,
                creditDaysText = CreditCalculator.formatCreditDays(credit.creditDays),
                deficitCount = credit.deficitTaskCount,
                earliestDeficitDate = credit.earliestDeficitDate,
                baseCompletionDate = credit.baseCompletionDate,
                expectedCompletionDate = credit.expectedCompletionDate,
                onBackfillFirst = onBackfillFirst,
                onReschedule = { showRescheduleDialog = true },
            )
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // —— 科目 2：背单词聚合卡片 ——
        if (vocabState != null) {
            Box(Modifier.padding(horizontal = RitualSpace.screenPadding)) {
                VocabTodayCard(
                    vocabState = vocabState,
                    onAddWords = onAddVocabWords,
                    onAddReview = onAddVocabReview,
                    onOpenVocabBoard = onOpenVocabBoard,
                )
            }
            Spacer(Modifier.height(RitualSpace.sectionGap))
        }

        // —— 任务组 ——
        // 按相位分两组，每组 3 项
        TaskGroup(
            title = "第一天 · 3 项",
            subtitle = if (todayPhase == 1) "（今日计划）" else "",
            subtitleColor = if (todayPhase == 1) RitualColors.accentInk else RitualColors.onBgFaint,
            tasks = (1..plan.tasksPerDay).toList(),
            articleIndex = progress.currentArticle,
            completedTasks = completedInArticle,
            nextTask = nextTaskInArticle,
            isTodayPhase = todayPhase == 1,
            onClickTask = { task ->
                val isDone = task in completedInArticle
                if (isDone) {
                    undoDialog = UndoDialogState(
                        articleIndex = progress.currentArticle,
                        taskIndex = task,
                        taskName = TodayCopyResolver.taskName(task),
                    )
                } else {
                    onCheckTask(progress.currentArticle, task)
                }
            },
            records = records,
            plan = plan,
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 第二天组（每篇 2 天时）
        if (plan.daysPerArticle >= 2) {
            TaskGroup(
                title = "第二天 · ${plan.tasksPerDay} 项",
                subtitle = if (todayPhase == 2) "（今日计划）" else "（可提前）",
                subtitleColor = if (todayPhase == 2) RitualColors.accentInk else RitualColors.onBgFaint,
                tasks = (plan.tasksPerDay + 1..plan.tasksPerDay * 2).toList(),
                articleIndex = progress.currentArticle,
                completedTasks = completedInArticle,
                nextTask = nextTaskInArticle,
                isTodayPhase = todayPhase == 2,
                onClickTask = { task ->
                    val isDone = task in completedInArticle
                    if (isDone) {
                        undoDialog = UndoDialogState(
                            articleIndex = progress.currentArticle,
                            taskIndex = task,
                            taskName = TodayCopyResolver.taskName(task),
                        )
                    } else {
                        onCheckTask(progress.currentArticle, task)
                    }
                },
                records = records,
                plan = plan,
            )
        }

        // 尾部留白：只让最后一行任务能滚到底部导航上面来，不再给悬浮按钮占位。
        Spacer(Modifier.height(RitualSpace.navBarHeight + 32.dp))
    }

    // ⚠️ 这里原来悬着一个固定在底部的大药丸主按钮（「完成第 N 项」）。
    // 2026-08-06 用户点名删掉，原话：「悬浮的勾选不需要悬浮了，看起来与页面不符……
    // 根本不需要这个悬浮的，直接点击前面的勾就行」。
    // 它做的事任务列表每一行前面的圈已经能做（TaskRow 整行可点 → onCheckTask），
    // 属于同一个动作的第二个入口，代价是永久盖住列表最后一行。
    // `copy.primaryButtonText / primaryButtonTarget` 仍由 TodayCopyResolver 算着没删——
    // 那是「下一步该干哪一项」的领域结论，别的地方（文案、测试）在用，只是不再画成按钮。

    // —— 撤销确认弹窗 ——
    if (undoDialog != null) {
        val dlg = undoDialog!!
        UndoConfirmDialog(
            taskName = dlg.taskName,
            articleIndex = dlg.articleIndex,
            onConfirm = {
                onUndoTask(dlg.articleIndex, dlg.taskIndex)
                undoDialog = null
            },
            onDismiss = { undoDialog = null },
        )
    }

    // —— 重新排期确认弹窗 ——
    if (showRescheduleDialog) {
        RescheduleConfirmDialog(
            state = state,
            onConfirm = {
                onReschedule()
                showRescheduleDialog = false
            },
            onDismiss = { showRescheduleDialog = false },
        )
    }
}

// ———————————— 子组件 ————————————

@Composable
private fun TaskGroup(
    title: String,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color,
    tasks: List<Int>,
    articleIndex: Int,
    completedTasks: Set<Int>,
    nextTask: Int?,
    isTodayPhase: Boolean,
    onClickTask: (Int) -> Unit,
    records: List<TaskRecord>,
    plan: com.zyj.ritual.domain.model.Plan,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RitualSpace.screenPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = RitualTypography.labelSmall,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    subtitle,
                    style = RitualTypography.bodySmall.copy(
                        color = subtitleColor,
                        fontSize = RitualTypeSize.caption,
                    ),
                )
            }
        }
        Spacer(Modifier.height(RitualSpace.listGap))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { taskIndex ->
                val isDone = taskIndex in completedTasks
                val isNext = !isDone && taskIndex == nextTask &&
                    (isTodayPhase || nextTask in tasks)
                val record = records.firstOrNull {
                    it.articleIndex == articleIndex && it.taskIndex == taskIndex
                }
                val taskState = when {
                    isDone && record?.source == RecordSource.BACKFILL ->
                        TaskRowState.Backfill(formatStamp(record.completedAt))
                    isDone ->
                        TaskRowState.Checked(formatStamp(record?.completedAt))
                    isNext ->
                        TaskRowState.Next
                    else ->
                        TaskRowState.Unchecked
                }
                TaskRow(
                    text = TodayCopyResolver.taskName(taskIndex),
                    state = taskState,
                    onClick = { onClickTask(taskIndex) },
                )
            }
        }
    }
}

// —— 撤销确认弹窗 ——

private data class UndoDialogState(
    val articleIndex: Int,
    val taskIndex: Int,
    val taskName: String,
)

@Composable
private fun UndoConfirmDialog(
    taskName: String,
    articleIndex: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = RitualColors.surface,
        titleContentColor = RitualColors.onBg,
        textContentColor = RitualColors.onBgMuted,
        onDismissRequest = onDismiss,
        title = {
            Text("撤销「$taskName」？", style = RitualTypography.titleMedium)
        },
        text = {
            Text(
                "撤销后，第 $articleIndex 篇的完成进度会减少 1 项，" +
                    "总进度也会回退。",
                style = RitualTypography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "确认撤销",
                    style = RitualTypography.bodyMedium.copy(
                        color = RitualColors.warnText,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                )
            }
        },
    )
}

// —— 重新排期确认弹窗 ——

@Composable
private fun RescheduleConfirmDialog(
    state: TodayState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val credit = state.credit
    val progress = state.progress
    val isDeficit = credit.state == CreditState.DEFICIT

    AlertDialog(
        containerColor = RitualColors.surface,
        titleContentColor = RitualColors.onBg,
        textContentColor = RitualColors.onBgMuted,
        onDismissRequest = onDismiss,
        title = {
            Text("重新排期？", style = RitualTypography.titleMedium)
        },
        text = {
            Column {
                Text(
                    if (isDeficit) {
                        "目前缺 ${credit.deficitTaskCount} 项。重新排期后，计划从今天重新开始，" +
                            "额度归零，之前的历史记录全部保留。"
                    } else {
                        "目前领先 ${CreditCalculator.formatCreditDays(credit.creditDays)} 天。" +
                            "重新排期后，计划从今天重新开始，额度归零，之前的历史记录全部保留。"
                    },
                    style = RitualTypography.bodyMedium,
                )
                Spacer(Modifier.height(RitualSpace.listGap))
                Text(
                    "新起点：第 ${progress.currentArticle} 篇",
                    style = RitualTypography.bodyMedium,
                )
                Text(
                    "新的预计完成日：${credit.expectedCompletionDate.monthValue}月" +
                        "${credit.expectedCompletionDate.dayOfMonth}日",
                    style = RitualTypography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "确认重排",
                    style = RitualTypography.bodyMedium.copy(
                        color = RitualColors.accentInk,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                )
            }
        },
    )
}

// —— 全部读完时的完成态。做成 Today 的一个分支而不是独立路由：
// 它没有自己的交互，独立 route 只会多一层导航状态要维护。
@Composable
private fun DoneScreenContent(state: TodayState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .padding(horizontal = RitualSpace.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", style = RitualTypography.displayLarge)
            Spacer(Modifier.height(RitualSpace.sectionGap))
            Text(
                "${state.plan.totalArticles} 篇全部读完",
                style = RitualTypography.headlineLarge,
            )
            Spacer(Modifier.height(RitualSpace.listGap))
            Text(
                "${state.progress.totalCompleted} / ${state.progress.totalTasks} 项 · 100.00%",
                style = RitualTypography.bodyMedium,
            )
        }
    }
}

// ———————————— 工具 ————————————

private fun formatDateLong(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("M月d日 EEE")
    return date.format(formatter) + " · 北京"
}

private fun formatStamp(instant: java.time.Instant?): String {
    if (instant == null) return ""
    val zdt = instant.atZone(java.time.ZoneId.of("Asia/Shanghai"))
    return String.format("%02d.%02d %02d:%02d",
        zdt.monthValue, zdt.dayOfMonth, zdt.hour, zdt.minute)
}

// 计算额度占总任务的比例（画金弧用）
private fun creditProgress(
    credit: com.zyj.ritual.domain.model.CreditResult,
    totalTasks: Int,
): Float {
    if (credit.state != CreditState.AHEAD) return 0f
    return (credit.creditTasks.toDouble() / totalTasks).toFloat().coerceIn(0f, 1f)
}

// ProgressCalculator 的 format 别名
private object ProgressCalculator {
    fun format(progress: Double): String =
        "%.2f".format((progress * 100).coerceIn(0.0, 100.0))
}
