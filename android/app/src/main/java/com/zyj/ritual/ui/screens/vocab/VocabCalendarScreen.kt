package com.zyj.ritual.ui.screens.vocab

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.domain.vocab.VocabCalendarCell
import com.zyj.ritual.domain.vocab.VocabCalendarResult
import com.zyj.ritual.domain.vocab.VocabState
import com.zyj.ritual.ui.components.AddAmountSheet
import com.zyj.ritual.ui.components.ProgressBarLine
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypography

@Composable
fun VocabCalendarScreen(
    viewModel: VocabViewModel,
    onNavigateToSetup: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is VocabUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RitualColors.bg)
                    .padding(16.dp)
            ) {
                Text(
                    text = "加载背单词数据…",
                    style = RitualTypography.bodySmall,
                )
            }
        }
        is VocabUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RitualColors.bg)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("背单词看板加载错误", style = RitualTypography.headlineMedium)
                Text(state.message, color = RitualColors.warnText)
            }
        }
        is VocabUiState.Ready -> {
            VocabCalendarScreenContent(
                aggregate = state.aggregate,
                onAddWords = { words -> viewModel.addRecord(words, "new") },
                onAddReview = { words -> viewModel.addRecord(words, "backlog") },
                onSaveReviewDue = { due -> viewModel.saveReviewDue(due) },
                onOpenSettings = onNavigateToSetup,
            )
        }
    }
}

@Composable
fun VocabCalendarScreenContent(
    aggregate: VocabAggregateState,
    onAddWords: (Int) -> Unit = {},
    onAddReview: (Int) -> Unit = {},
    onSaveReviewDue: (Int) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state = aggregate.state
    val calendar = aggregate.calendar

    var showCalibrate by rememberSaveable { mutableStateOf(false) }

    // ⚠️ 这里**不要**再套一层 Scaffold。
    // MainActivity 已经有一个 Scaffold 了，它已经把状态栏的高度让出来了；
    // 这一页原来又套了一个，于是状态栏高度被让了两遍——屏幕顶端到「背词设置」
    // 之间白出来一大片。用户 2026-08-17 反馈的「上面空太多」就是这个，
    // 不是内边距调大了，是同一份系统边距算了两次。
    // 别的页面（今日/月历/进度/设置）都没套第二层，所以只有这页显得往下掉。
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VocabBoardPanels(
                    aggregate = aggregate,
                    onAddWords = onAddWords,
                    onAddReview = onAddReview,
                    onOpenSettings = onOpenSettings,
                    onOpenCalibrate = { showCalibrate = true },
                    // 面板已经在 grid 的左右 16dp 内边距里了，别再套一层
                    horizontalPadding = 0.dp,
                )
            }
            calendarGrid(calendar)
        }
    }

    if (showCalibrate) {
        ReviewDueSheet(
            state = state,
            onDismiss = { showCalibrate = false },
            onSave = { due ->
                onSaveReviewDue(due)
                showCalibrate = false
            },
        )
    }
}

/**
 * 顶部那几块（设置入口 / 进度卡 / 复习区 / 打卡按钮 / 图例）。
 *
 * 整块作为 LazyVerticalGrid 的一个整行 item，跟日历一起滚——
 * 顶上固定一大块面板会把日历挤成一条缝。
 *
 * `horizontalPadding`：外层 grid 已经有 16dp 内边距了，这里再套一层就会缩进两次。
 */
@Composable
private fun VocabBoardPanels(
    aggregate: VocabAggregateState,
    onAddWords: (Int) -> Unit,
    onAddReview: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalibrate: () -> Unit,
    horizontalPadding: Dp,
) {
    val state = aggregate.state

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "背词设置 ›",
                fontSize = 13.sp,
                color = RitualColors.accentInk,
                modifier = Modifier
                    .clickable { onOpenSettings() }
                    .padding(4.dp),
            )
        }

        // 顶栏：已背进度条 + 百分比 + 计划/预计口径
        VocabHeaderCard(
            state = state,
            configName = aggregate.config.bookName,
            examDate = aggregate.config.examDate,
            outerPadding = horizontalPadding,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 复习两条（累计里程碑 + 今天）
        VocabReviewSection(
            state = state,
            onOpenCalibrate = onOpenCalibrate,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 就地打卡：预填量跟着今天的状态走，不硬编码 20
        VocabAddRow(
            state = state,
            onAddWords = onAddWords,
            onAddReview = onAddReview,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )

        Spacer(modifier = Modifier.height(10.dp))

        VocabLegendRow(horizontalPadding = horizontalPadding)
    }
}

/** 星期标题 + 日历格子。两版布局共用同一份，避免抄两遍抄岔。 */
private fun LazyGridScope.calendarGrid(calendar: VocabCalendarResult) {
    val weekDays = listOf("一", "二", "三", "四", "五", "六", "日")
    items(weekDays) { day ->
        Text(
            text = day,
            fontSize = 12.sp,
            color = RitualColors.onBgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    items(calendar.cells) { cell ->
        when (cell) {
            is VocabCalendarCell.Month -> {
                Text(
                    text = cell.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RitualColors.onBg,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
            is VocabCalendarCell.Pad -> {
                Spacer(modifier = Modifier.size(1.dp))
            }
            is VocabCalendarCell.Day -> {
                VocabCalendarCellView(cell = cell, onClick = { })
            }
        }
    }
}

/**
 * 就地打卡两个按钮。
 *
 * **实际背了多少就记多少，不许凑整。** 这是 2026-08-17 用户反馈的核心：
 * 按钮固定记 40（＝今天的计划额度），可他有时只多学一组 20、有时 28、有时 8 个。
 * 只能记 40 的话，那 8 个要么不记、要么记成 40——前者丢数据，后者数据是假的。
 * 所以加减号一步走 1，快捷档也不是「20 的倍数」那套，随便什么数都填得出来。
 *
 * 「改」按钮必须**看得见**。上一版做成长按，屏幕上没有任何痕迹，
 * 用户装上之后根本没发现这个功能存在。这里沿用复习那行已经验证过的「改」小方框——
 * 同一个 App 里同一件事就该长一个样。
 */
@Composable
private fun VocabAddRow(
    state: VocabState,
    onAddWords: (Int) -> Unit,
    onAddReview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val newAmount = state.todayQuota.coerceAtLeast(1)
    // 今天校准过就补齐剩下的；没校准就退回今天的新词额度当默认步长
    val reviewAmount = (state.reviewLeftToday?.takeIf { it > 0 } ?: state.todayQuota)
        .coerceAtLeast(1)

    var sheet by remember { mutableStateOf<AddSheetTarget?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AddButton(
                text = "+$newAmount 新词",
                color = RitualColors.accentInk,
                onClick = { onAddWords(newAmount) },
                onPickAmount = { sheet = AddSheetTarget.New },
                modifier = Modifier.weight(1f),
            )
            AddButton(
                text = "+$reviewAmount 复习",
                color = RitualColors.accentReview,
                onClick = { onAddReview(reviewAmount) },
                onPickAmount = { sheet = AddSheetTarget.Review },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "今天已背 ${state.todayWords} / ${state.todayQuota} · 背了几个就记几个，点 ▾ 改",
            fontSize = 11.sp,
            color = RitualColors.onBgFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (sheet) {
        AddSheetTarget.New -> AddAmountSheet(
            title = "这次背了几个新词",
            initialAmount = newAmount,
            // 上限＝还没背的词数。背完了还能继续加，只能是填错了
            max = state.remainWords.coerceAtLeast(1),
            todayDone = state.todayWords,
            todayTarget = state.todayQuota,
            onDismiss = { sheet = null },
            onConfirm = { amount ->
                onAddWords(amount)
                sheet = null
            },
        )
        AddSheetTarget.Review -> AddAmountSheet(
            title = "这次复习了几个",
            initialAmount = reviewAmount,
            // 复习没有「总量」这个概念（见 VocabReviewSection 顶部第 1 条红线），
            // 所以不拿 remainWords 当上限，只给一个防手滑的宽松上界
            max = REVIEW_ADD_MAX,
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

/** 点「改」时，弹的是哪一个面板 */
private enum class AddSheetTarget { New, Review }

/** 复习量没有天然上限，这个数只用来挡住手滑多打一位 */
private const val REVIEW_ADD_MAX = 9_999

/**
 * 打卡按钮：主体记预填量，右端一个 `▾` 点开改数量。
 *
 * 为什么是 `▾` 而不是「改」小方框：方框是第四个元素，硬塞进这一行会把
 * 两个按钮挤扁，用户原话「太丑了，不符合这个 UI 设计」。而 `▾` 是这个 App
 * 已有的记号——日期选择器（RitualDatePicker）用的就是它，含义是「点这里挑一个值」，
 * 正好是这里要表达的意思。它长在按钮内部，不占额外位置。
 *
 * 为什么不干脆退回长按：长按在屏幕上没有任何痕迹，上一版做成长按之后
 * 用户装上根本没发现有这功能。`▾` 看得见，长按看不见，差别就在这。
 */
@Composable
private fun AddButton(
    text: String,
    color: Color,
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
        // 主体：点了就按预填量记一笔，这是每天的主路径，占满剩余宽度
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = RitualTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }

        // 右端 ▾：点开面板改数量。给足点击区域，别做成只有箭头那几像素能点
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(onClick = onPickAmount)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▾",
                style = RitualTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}

@Composable
fun VocabHeaderCard(
    state: VocabState,
    configName: String,
    examDate: String,
    modifier: Modifier = Modifier,
    // 原来这里写死 .padding(16.dp)，外面 Column 又给了一层，于是卡片左右缩进两次、
    // 上下也白吃掉 32dp。这正是用户说的「背词设置占了特别大一行，上面空了很多」。
    outerPadding: Dp = 16.dp,
) {
    val totalWords = state.doneWords + state.remainWords
    val percent = VocabCalculator.donePercent(
        doneWords = state.doneWords,
        totalWords = totalWords,
        finished = state.finished,
    )
    val animatedDone by animateIntAsState(
        targetValue = state.doneWords,
        animationSpec = tween(durationMillis = 1400),
        label = "vocabDone",
    )
    val animatedPercent by animateIntAsState(
        targetValue = percent,
        animationSpec = tween(durationMillis = 1400),
        label = "vocabPercent",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = RitualColors.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = outerPadding)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = configName,
                fontSize = 14.sp,
                color = RitualColors.onBgMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$animatedDone",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = RitualColors.onBg
                    )
                    Text(
                        text = " / $totalWords",
                        fontSize = 16.sp,
                        color = RitualColors.onBgMuted,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = "今天 ${state.todayWords} / ${state.todayQuota}",
                    fontSize = 14.sp,
                    fontWeight = if (state.todayWords >= state.todayQuota) {
                        FontWeight.Bold
                    } else FontWeight.Normal,
                    color = if (state.todayWords >= state.todayQuota) {
                        RitualColors.accentGold
                    } else RitualColors.onBgMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 已背进度条。用户点名要这条——「还剩 1503 个」和「完成 20%」
            // 讲的是同一件事，但心理感觉截然不同
            ProgressBarLine(
                ratio = if (totalWords > 0) {
                    state.doneWords.toFloat() / totalWords
                } else 0f,
                color = RitualColors.accentGold,
                height = 14.dp,
                durationMillis = 1400,
                pulseOnFinish = true,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 百分比是这条的主角，字号压过右边的「还要学」。
                // 算法只有 VocabCalculator.donePercent 一处，进度页摘要卡调的是同一个函数
                Text(
                    text = "$animatedPercent%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RitualColors.accentGold,
                )
                Text(
                    // 这个数对得上不背单词 APP 首页的「待学」，两边能直接核
                    text = if (state.finished) "背完了 🎉" else "还要学 ${state.remainWords}",
                    fontSize = 12.sp,
                    color = RitualColors.onBgMuted,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 原来这里是一个「跟上计划」小标签——四个字不解释就看不懂，
            // 它的真实含义是「预计完成日正好等于计划完成日」。
            // 换成把两个日期和差值都摊开写，用户自己就能算，不用猜形容词。
            val aheadText = when {
                state.aheadDays > 0 -> "早 ${state.aheadDays} 天"
                state.aheadDays < 0 -> "晚 ${-state.aheadDays} 天"
                else -> "不早不晚"
            }
            Text(
                text = "计划 ${VocabCalculator.formatCn(state.planFinishDate)}" +
                    " · 预计 ${VocabCalculator.formatCn(state.projFinishDate)}" +
                    " · $aheadText",
                fontSize = 13.sp,
                fontWeight = if (state.isAlarm) FontWeight.SemiBold else FontWeight.Normal,
                color = if (state.isAlarm) RitualColors.warnText else RitualColors.onBg,
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "D-${state.daysToExam} · 考试 ${VocabCalculator.formatCn(examDate)}",
                fontSize = 12.sp,
                color = RitualColors.onBgFaint
            )
        }
    }
}

@Composable
fun VocabLegendRow(horizontalPadding: Dp = 16.dp) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp)
    ) {
        LegendItem(color = RitualColors.accentInk, label = "已背")
        LegendItem(color = RitualColors.accentGold, label = "提前")
        LegendItem(color = RitualColors.warn, label = "缺口")
        LegendItem(color = RitualColors.accentReview, label = "复习")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = RitualColors.onBgMuted)
    }
}
