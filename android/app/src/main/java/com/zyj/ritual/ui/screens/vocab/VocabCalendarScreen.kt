package com.zyj.ritual.ui.screens.vocab

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/**
 * 顶部面板的两种排法。用户反馈「背词设置占了特别大一行，日历空间太小」，
 * 两种解法都做出来渲染成图对比，选定后删掉没选的那个。
 */
enum class VocabBoardLayout {
    /** A：顶部面板也进日历的滚动容器，往下滑面板滚走，日历占满整屏 */
    ScrollAll,

    /** B：面板仍固定在顶部，只把重复的内外双层内边距和多余空行压掉 */
    CompactFixed,
}

@Composable
fun VocabCalendarScreenContent(
    aggregate: VocabAggregateState,
    onAddWords: (Int) -> Unit = {},
    onAddReview: (Int) -> Unit = {},
    onSaveReviewDue: (Int) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    layout: VocabBoardLayout = VocabBoardLayout.ScrollAll,
) {
    val state = aggregate.state
    val calendar = aggregate.calendar

    var showCalibrate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = RitualColors.bg
    ) { innerPadding ->
        when (layout) {
            VocabBoardLayout.ScrollAll -> LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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

            VocabBoardLayout.CompactFixed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                VocabBoardPanels(
                    aggregate = aggregate,
                    onAddWords = onAddWords,
                    onAddReview = onAddReview,
                    onOpenSettings = onOpenSettings,
                    onOpenCalibrate = { showCalibrate = true },
                    horizontalPadding = 16.dp,
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    calendarGrid(calendar)
                }
            }
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
 * 抽出来是因为 A、B 两版布局要放同一份内容，只是放的容器不同——
 * A 版塞进 LazyVerticalGrid 当整行 item，B 版留在 Column 里固定。
 * 抄两份的话，以后改一处忘了另一处，两版就会悄悄长得不一样。
 *
 * `horizontalPadding`：A 版外层 grid 已经有 16dp 内边距了，再套一层就会缩进两次。
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
 * 预填量跟着状态走，不再硬编码 20：今天要复习 45 个却只能一次点 +20，
 * 「今天定了多少、我完成了」这条路径就走不完。
 *
 * 短按记预填量（每天的主路径），长按弹面板改数量——用户在「不背单词」里
 * 多学一次是一组 20 个，而这里的预填量是计划额度 40，不给改就只能记多一倍。
 * 长按这件事屏幕上看不见，所以下面那行提示小字是功能的一部分，不是装饰。
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
            modifier = Modifier.fillMaxWidth()
        ) {
            AddButton(
                text = "+$newAmount 新词",
                color = RitualColors.accentInk,
                onClick = { onAddWords(newAmount) },
                onLongClick = { sheet = AddSheetTarget.New },
                modifier = Modifier.weight(1f),
            )
            AddButton(
                text = "+$reviewAmount 复习",
                color = RitualColors.accentReview,
                onClick = { onAddReview(reviewAmount) },
                onLongClick = { sheet = AddSheetTarget.Review },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "长按可以改这次记多少个",
            fontSize = 11.sp,
            color = RitualColors.onBgFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (sheet) {
        AddSheetTarget.New -> AddAmountSheet(
            title = "记新词",
            initialAmount = newAmount,
            // 上限＝还没背的词数。背完了还能继续加，只能是填错了
            max = state.remainWords.coerceAtLeast(1),
            presets = VOCAB_ADD_PRESETS,
            onDismiss = { sheet = null },
            onConfirm = { amount ->
                onAddWords(amount)
                sheet = null
            },
        )
        AddSheetTarget.Review -> AddAmountSheet(
            title = "记复习",
            initialAmount = reviewAmount,
            // 复习没有「总量」这个概念（见 VocabReviewSection 顶部第 1 条红线），
            // 所以不拿 remainWords 当上限，只给一个防手滑的宽松上界
            max = REVIEW_ADD_MAX,
            presets = VOCAB_ADD_PRESETS,
            onDismiss = { sheet = null },
            onConfirm = { amount ->
                onAddReview(amount)
                sheet = null
            },
        )
        null -> Unit
    }
}

/** 长按打卡按钮时，弹的是哪一个面板 */
private enum class AddSheetTarget { New, Review }

/** 快捷档：20 是「不背单词」一组的量，40 是用户当前计划额度 */
private val VOCAB_ADD_PRESETS = listOf(10, 20, 40)

/** 复习量没有天然上限，这个数只用来挡住手滑多打一位 */
private const val REVIEW_ADD_MAX = 9_999

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddButton(
    text: String,
    color: Color,
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = RitualTypography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
        )
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
