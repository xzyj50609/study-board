package com.zyj.ritual.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.ui.screens.calendar.CalendarScreen
import com.zyj.ritual.ui.theme.LightRitualColors
import com.zyj.ritual.ui.theme.RitualColorScheme
import com.zyj.ritual.ui.theme.RitualTheme

/**
 * 月历页截图。
 *
 * 这一页的格子有 9 种状态，颜色本身就是信息——一格是"提前做的"还是"欠着的"，
 * 全靠底色和边框区分。亮色下这些状态最容易挤成一坨：
 * 深色下 8%～15% 的强调色叠在深底上层次分明，叠到纸白上可能几乎看不出差别。
 *
 * 两个数据状态各截一张，合起来能画出全部格子类型：
 * 提前完成（金框金点）、按计划完成（蓝点）、缺额（红框红点）、
 * 今天（金框白底，只在今天还没做完时出现）、未来计划日（灰点）。
 */

@Composable
private fun Frame(
    state: TodayState,
    colors: RitualColorScheme,
    vocabState: VocabAggregateState? = null,
) {
    RitualTheme(colors = colors) {
        CalendarScreen(state = state, vocabState = vocabState)
    }
}

@PreviewTest
@Preview(name = "月历页-有额度", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CalendarAheadPreview() = Frame(buildState(24), LightRitualColors)

/**
 * 落后的月历：缺额那几格走暗铜，亮色下能不能一眼看出是"欠着的"要靠这张图。
 * 顺带这张里今天（6 号）还没做完，所以能看到今天那格的金框白底——
 * 今天做完了就按 R28 画成"按计划完成"，那个样子在上面那张里。
 */
@PreviewTest
@Preview(name = "月历页-落后", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CalendarBehindPreview() = Frame(buildState(9), LightRitualColors)

/**
 * 接上背词数据的月历：每个日期下面并排两个点，左金 = 读文章，右紫 = 背单词。
 *
 * ⚠️ 这张图的重点在 **7/29、7/30 那两格**（fixture 里只复习没背新词）：
 * 它们必须是**空心紫圈**，不能是实心。画成实心的话，那天跟真背完新词的日子
 * 长得一模一样——回头复盘会把只复习的天数算成完成，而屏幕上完全看不出错在哪。
 * 这正是这张基准图唯一逮得住的东西。
 */
@PreviewTest
@Preview(name = "月历页-带背词双点", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CalendarWithVocabPreview() = Frame(
    state = buildState(24),
    colors = LightRitualColors,
    vocabState = buildCalendarVocabAggregate(),
)
