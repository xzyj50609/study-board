package com.zyj.ritual.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.calculator.CreditCalculator
import com.zyj.ritual.domain.calculator.TodayCopyResolver
import com.zyj.ritual.domain.model.CalendarCellStatus
import com.zyj.ritual.domain.model.CreditState
import com.zyj.ritual.domain.model.DayPlan
import com.zyj.ritual.domain.model.RecordSource
import com.zyj.ritual.domain.model.TaskRecord
import com.zyj.ritual.domain.vocab.VocabCalendarCell
import com.zyj.ritual.ui.components.TaskRow
import com.zyj.ritual.ui.components.TaskRowState
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTextStyles
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 月历页。
 *
 * 支持 HorizontalPager 左右滑动切月，
 * 范围 = 计划开始月前 1 月 ~ 预计完成月后 3 月。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: TodayState,
    vocabState: VocabAggregateState? = null,
    onCheckTask: (Int, Int) -> Unit = { _, _ -> },
    onUndoTask: (Int, Int) -> Unit = { _, _ -> },
) {
    // 背词那天的情况，按 "YYYY-MM-DD" 索引。
    // ⚠️ 直接吃 computeCalendar 已经算好的格子，不在这里重算——
    // 「只复习没背新词的那天不算漏」这条规则藏在两遍分配里，重算必然翻车。
    val vocabByDate: Map<String, VocabCalendarCell.Day> =
        remember(vocabState?.calendar) {
            vocabState?.calendar?.cells
                ?.filterIsInstance<VocabCalendarCell.Day>()
                ?.associateBy { it.date }
                ?: emptyMap()
        }
    // 计算月份范围：计划开始月前 1 月 ~ 预计完成月后 3 月
    val months = remember(state.credit.expectedCompletionDate, state.plan.planStartDate) {
        val startMonth = YearMonth.from(state.plan.planStartDate).minusMonths(1)
        val endMonth = YearMonth.from(state.credit.expectedCompletionDate).plusMonths(3)
        generateSequence(startMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(endMonth) }
            .toList()
    }

    // 找到今天所在月的 index，作为初始页
    val todayMonth = YearMonth.from(state.today)
    val initialPage = months.indexOfFirst { it == todayMonth }.coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { months.size },
    )
    val pagerScope = rememberCoroutineScope()

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // 当前显示的月份
    val currentMonth = months[pagerState.currentPage]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))

        // 顶部：标题 + 月份切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RitualSpace.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("月历", style = RitualTypography.headlineMedium)
            Spacer(Modifier.weight(1f))

            // 左箭头
            val canGoPrev = pagerState.currentPage > 0
            Text(
                "‹",
                style = RitualTypography.titleMedium.copy(
                    color = if (canGoPrev) RitualColors.onBg else RitualColors.onBgFaint,
                    fontWeight = FontWeight.Light,
                ),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (canGoPrev) Modifier.clickable {
                            pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else Modifier
                    ),
            )
            Spacer(Modifier.width(RitualSpace.listGap))
            Text(
                "${currentMonth.year} 年 ${currentMonth.monthValue} 月",
                style = RitualTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.width(RitualSpace.listGap))
            // 右箭头
            val canGoNext = pagerState.currentPage < months.size - 1
            Text(
                "›",
                style = RitualTypography.titleMedium.copy(
                    color = if (canGoNext) RitualColors.onBg else RitualColors.onBgFaint,
                    fontWeight = FontWeight.Light,
                ),
                modifier = if (canGoNext) Modifier
                    .size(32.dp)
                    .clickable {
                        pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(RitualSpace.listGap))

        // HorizontalPager 月历
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val month = months[page]
            CalendarMonthView(
                month = month,
                state = state,
                vocabByDate = vocabByDate,
                onClickDate = { selectedDate = it },
            )
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 摘要三格（固定在底部）
        Box(Modifier.padding(horizontal = RitualSpace.screenPadding)) {
            CalendarSummary(state, currentMonth)
        }

        Spacer(Modifier.height(RitualSpace.navBarHeight + 32.dp))
    }

    // 底部面板
    if (selectedDate != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedDate = null },
            sheetState = sheetState,
            containerColor = RitualColors.surface,
            dragHandle = null,
        ) {
            DaySheetContent(
                date = selectedDate!!,
                state = state,
                vocabDay = vocabByDate[selectedDate!!.toString()],
                onCheckTask = { art, task ->
                    onCheckTask(art, task)
                    selectedDate = null
                },
                onUndoTask = { art, task ->
                    onUndoTask(art, task)
                    selectedDate = null
                },
            )
        }
    }
}

// ———————————— 单月视图 ————————————

@Composable
private fun CalendarMonthView(
    month: YearMonth,
    state: TodayState,
    vocabByDate: Map<String, VocabCalendarCell.Day>,
    onClickDate: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RitualSpace.screenPadding),
    ) {
        // 星期表头
        WeekHeader()

        Spacer(Modifier.height(RitualSpace.listGap))

        // 月格
        CalendarGrid(
            month = month,
            state = state,
            vocabByDate = vocabByDate,
            onClickDate = onClickDate,
        )
    }
}

// ———————————— 月格 ————————————

@Composable
private fun WeekHeader() {
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                day,
                style = RitualTypography.bodySmall.copy(
                    color = RitualColors.onBgFaint,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    state: TodayState,
    vocabByDate: Map<String, VocabCalendarCell.Day>,
    onClickDate: (LocalDate) -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    // 周一起始：周一 = 1，周日 = 7
    val firstDayOfWeek = firstOfMonth.dayOfWeek.value
    val offset = firstDayOfWeek - 1  // 前面空几格
    val daysInMonth = month.lengthOfMonth()
    val totalCells = offset + daysInMonth
    val rows = (totalCells + 6) / 7

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.heightIn(max = (rows * 60).dp),
        verticalArrangement = Arrangement.spacedBy(RitualSpace.calendarGap),
        horizontalArrangement = Arrangement.spacedBy(RitualSpace.calendarGap),
        userScrollEnabled = false,
    ) {
        items(rows * 7) { index ->
            val dayNumber = index - offset + 1
            if (dayNumber in 1..daysInMonth) {
                val date = month.atDay(dayNumber)
                CalendarCell(
                    date = date,
                    state = state,
                    vocabDay = vocabByDate[date.toString()],
                    onClick = { onClickDate(date) },
                )
            } else {
                Spacer(Modifier.height(RitualSize.calendarCell))
            }
        }
    }
}

@Composable
private fun CalendarCell(
    date: LocalDate,
    state: TodayState,
    vocabDay: VocabCalendarCell.Day?,
    onClick: () -> Unit,
) {
    val status = remember(date, state.records, state.today, state.credit.state) {
        com.zyj.ritual.domain.calculator.DayStatusResolver.resolve(
            state.plan,
            state.calendar,
            state.records,
            date,
            state.today,
            state.credit.state,
        )
    }
    val dayPlan = state.calendar.getDayPlan(date)

    val bgColor = when (status) {
        CalendarCellStatus.TODAY -> RitualColors.surface
        CalendarCellStatus.PAST_DEFICIT -> RitualColors.warn.copy(alpha = 0.10f)
        CalendarCellStatus.PAST_COVERED -> RitualColors.accentGold.copy(alpha = 0.08f)
        CalendarCellStatus.PAST_ADVANCED, CalendarCellStatus.FUTURE_ADVANCED -> RitualColors.accentGold.copy(alpha = 0.15f)
        CalendarCellStatus.PAST_BACKFILL -> RitualColors.accentInk.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val borderColor = when (status) {
        CalendarCellStatus.TODAY -> RitualColors.accentGold
        CalendarCellStatus.PAST_DEFICIT -> RitualColors.warn.copy(alpha = 0.5f)
        CalendarCellStatus.PAST_ADVANCED, CalendarCellStatus.FUTURE_ADVANCED -> RitualColors.accentGold
        CalendarCellStatus.PAST_BACKFILL -> RitualColors.accentInk
        else -> Color.Transparent
    }

    val dotColor = when (status) {
        CalendarCellStatus.BEFORE_START -> RitualColors.onBgFaint.copy(alpha = 0.5f)
        CalendarCellStatus.TODAY -> RitualColors.accentGold
        CalendarCellStatus.TODAY_PARTIAL -> RitualColors.accentInk
        CalendarCellStatus.TODAY_COMPLETE -> RitualColors.accentInk
        CalendarCellStatus.PAST_ON_PLAN -> RitualColors.accentInk
        CalendarCellStatus.PAST_PARTIAL -> RitualColors.accentInk.copy(alpha = 0.6f)
        CalendarCellStatus.PAST_BACKFILL -> RitualColors.accentInk
        CalendarCellStatus.PAST_ADVANCED -> RitualColors.accentGold
        CalendarCellStatus.PAST_COVERED -> RitualColors.accentGold.copy(alpha = 0.6f)
        CalendarCellStatus.PAST_DEFICIT -> RitualColors.warn
        CalendarCellStatus.FUTURE_PLANNED -> RitualColors.onBgFaint
        CalendarCellStatus.FUTURE_ADVANCED -> RitualColors.accentGold
    }

    val textColor = when (status) {
        CalendarCellStatus.BEFORE_START -> RitualColors.onBgFaint
        CalendarCellStatus.FUTURE_PLANNED -> RitualColors.onBgFaint
        else -> RitualColors.onBg
    }

    Column(
        modifier = Modifier
            .height(RitualSize.calendarCell)
            .clip(RoundedCornerShape(RitualRadius.calendarCell))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(RitualRadius.calendarCell))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = RitualTextStyles.calendarDay.copy(color = textColor),
        )
        // 两个点并排：左金 = 读文章，右蓝 = 背单词。
        // 背词那颗只有真有数据时才占位，否则金点保持原来的居中位置，
        // 免得没接背词数据的日子看起来整体偏了
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            if (vocabDay != null) {
                VocabDot(vocabDay)
            }
        }
        // 篇·第几天小字
        val tagText = if (dayPlan.isPlanDay) "${dayPlan.articleIndex}·${dayPlan.phase}" else ""
        Text(
            tagText,
            style = RitualTextStyles.calendarTag,
        )
    }
}

/**
 * 月历格子里的背词那颗点。
 *
 * ⚠️ 判据一律用 `computeCalendar` 已经算好的标志位，不要图省事改成
 * `reviewWords > 0` 之类的自造条件：**只复习没背新词的那天必须画空心**。
 * 画成实心的话，那天跟真背完新词的日子长得一模一样，回头复盘会算错进度，
 * 而屏幕上完全看不出哪里错了。
 */
@Composable
private fun VocabDot(day: VocabCalendarCell.Day) {
    when {
        // 那天真背了新词
        day.ownWords > 0 -> Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(RitualColors.accentReview),
        )
        // 只复习了，没背新词——空心，不算漏也不算完成
        day.reviewOnly -> Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .border(1.5.dp, RitualColors.accentReview, CircleShape),
        )
        // 早于今天、额度没填满、那天什么都没干
        day.gap -> Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .border(1.5.dp, RitualColors.warn.copy(alpha = 0.7f), CircleShape),
        )
        // 未来的日子 / 没有任何记录：不画，别拿一个灰点冒充"有数据"
        else -> Spacer(Modifier.size(9.dp))
    }
}

// ———————————— 摘要三格 ————————————

@Composable
private fun CalendarSummary(state: TodayState, month: YearMonth) {
    val monthRecords = state.records.filter { r ->
        val date = r.completedAt?.atZone(java.time.ZoneId.of("Asia/Shanghai"))?.toLocalDate()
        date != null && YearMonth.from(date) == month
    }
    val completedThisMonth = monthRecords.size
    val creditDays = CreditCalculator.formatCreditDays(state.credit.creditDays)
    val deficit = if (state.credit.state == CreditState.DEFICIT) state.credit.deficitTaskCount else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RitualSpace.listGap),
    ) {
        SummaryCard("本月完成", "$completedThisMonth 项", Modifier.weight(1f))
        SummaryCard("剩余额度", creditDays, Modifier.weight(1f))
        SummaryCard("需补", "$deficit 项", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RitualRadius.card))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = RitualTypography.titleMedium.copy(fontFamily = RitualFontFamilies.num))
        Text(label, style = RitualTypography.bodySmall)
    }
}

// ———————————— 底部面板 ————————————

@Composable
private fun DaySheetContent(
    date: LocalDate,
    state: TodayState,
    vocabDay: VocabCalendarCell.Day?,
    onCheckTask: (Int, Int) -> Unit,
    onUndoTask: (Int, Int) -> Unit,
) {
    val plan = state.plan
    val dayPlan = state.calendar.getDayPlan(date)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(RitualSpace.screenPadding)
            .padding(bottom = RitualSpace.navBarHeight),
    ) {
        Text(
            "${date.monthValue} 月 ${date.dayOfMonth} 日 ${weekdayName(date)}",
            style = RitualTypography.headlineSmall,
        )

        // 读文章那部分。非计划日只有一句话，但背词那段照样要出——
        // 非计划日一样能背词，不能因为文章没排就把背词记录也藏起来
        if (!dayPlan.isPlanDay) {
            Text(
                if (date < plan.planStartDate) "计划尚未开始" else "非计划日",
                style = RitualTypography.bodySmall,
                modifier = Modifier.padding(top = RitualSpace.sectionGap),
            )
        } else {
            Text(
                "原计划 · 第 ${dayPlan.articleIndex} 篇 第 ${dayPlan.phase} 天 · ${dayPlan.taskIndices.size} 项",
                style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(RitualSpace.sectionGap))

            val completedIds = state.records
                .filter { it.articleIndex == dayPlan.articleIndex && it.taskIndex in dayPlan.taskIndices }
                .associateBy { it.taskIndex }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayPlan.taskIndices.forEach { taskIndex ->
                    val record = completedIds[taskIndex]
                    val isDone = record != null
                    val taskState = if (record != null) {
                        if (record.source == RecordSource.BACKFILL) {
                            TaskRowState.Backfill(formatSheetTime(record.completedAt))
                        } else {
                            TaskRowState.Checked(formatSheetTime(record.completedAt))
                        }
                    } else {
                        TaskRowState.Unchecked
                    }
                    TaskRow(
                        text = TodayCopyResolver.taskName(taskIndex),
                        state = taskState,
                        onClick = {
                            if (isDone) onUndoTask(dayPlan.articleIndex!!, taskIndex)
                            else onCheckTask(dayPlan.articleIndex!!, taskIndex)
                        },
                    )
                }
            }
        }

        // ── 背单词 ──
        if (vocabDay != null) {
            Spacer(Modifier.height(RitualSpace.sectionGap))
            Text("背单词", style = RitualTypography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                // 两个都是 0 时明说"没背词"，不留空白——
                // 留空的话分不清"这天没背"和"数据没加载出来"
                if (vocabDay.ownWords == 0 && vocabDay.reviewWords == 0) {
                    "这天没背词"
                } else {
                    "新背 ${vocabDay.ownWords} · 复习 ${vocabDay.reviewWords}"
                },
                style = RitualTypography.bodyMedium.copy(
                    color = if (vocabDay.ownWords == 0 && vocabDay.reviewWords == 0) {
                        RitualColors.onBgMuted
                    } else RitualColors.accentReview
                ),
            )
        }
    }
}

private fun weekdayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)

private fun formatSheetTime(instant: java.time.Instant?): String {
    if (instant == null) return ""
    val zdt = instant.atZone(java.time.ZoneId.of("Asia/Shanghai"))
    return "${zdt.monthValue}.${zdt.dayOfMonth} ${zdt.hour}:${String.format("%02d", zdt.minute)}"
}
