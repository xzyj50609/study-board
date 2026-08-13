package com.zyj.ritual.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
// 光有 @Preview 不够：截图引擎只认带 @PreviewTest 的，
// 少了它构建会报「test sources present ... did not discover any tests」。
import com.android.tools.screenshot.PreviewTest
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.ui.screens.today.TodayScreen
import com.zyj.ritual.ui.theme.DarkRitualColors
import com.zyj.ritual.ui.theme.LightRitualColors
import com.zyj.ritual.ui.theme.RitualColorScheme
import com.zyj.ritual.ui.theme.RitualTheme

/**
 * 今日页截图。
 *
 * 为什么要有这些图：2026-08-06 那轮改了今日页和进度页，单测全绿、构建成功、
 * APK 也出了，但**界面到底长什么样谁都没看见**，交接文档里只能写「还得你装上看一眼」。
 * 界面质量不该由用户装机肉眼兜底。
 *
 * 跑法：
 *   ./gradlew updateDebugScreenshotTest   # 生成/更新参考图（第一次跑用这个）
 *   ./gradlew validateDebugScreenshotTest # 跟参考图比对，不一致就失败并出 HTML 报告
 *
 * 图落在 app/src/screenshotTestDebug/reference/ 下面，直接打开就能看。
 */

import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord

private val SAMPLE_VOCAB_CONFIG = VocabConfig(
    totalWords = 1883,
    initialDone = 380,
    dailyWords = 20,
    startDate = "2026-07-27"
)

private val SAMPLE_VOCAB_STATE = VocabAggregateState(
    config = SAMPLE_VOCAB_CONFIG,
    records = listOf(
        VocabRecord("2026-08-06", 20, "new")
    ),
    state = VocabCalculator.computeState(
        listOf(VocabRecord("2026-08-06", 20, "new")),
        SAMPLE_VOCAB_CONFIG,
        "2026-08-06"
    ),
    calendar = VocabCalculator.computeCalendar(
        listOf(VocabRecord("2026-08-06", 20, "new")),
        SAMPLE_VOCAB_CONFIG,
        "2026-08-06"
    ),
    today = TODAY,
)

@Composable
private fun Frame(state: TodayState, colors: RitualColorScheme) {
    RitualTheme(colors = colors) {
        TodayScreen(
            state = state,
            onCheckTask = { _, _ -> },
            onUndoTask = { _, _ -> },
            onOpenArticle = {},
            onNavigateToSettings = {},
            vocabState = SAMPLE_VOCAB_STATE,
        )
    }
}

/** 落后一点：应完成 15 项，实际 14 项，缺 1 项。日常最常见的样子，缺额卡走暗铜。 */
@PreviewTest
@Preview(name = "今日页-落后1项", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayBehindPreview() = Frame(buildState(14), LightRitualColors)

/** 刚好跟上计划。 */
@PreviewTest
@Preview(name = "今日页-刚好跟上", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayOnTrackPreview() = Frame(buildState(15), LightRitualColors)

/** 提前做了不少，有额度。金卡是"暖金压深"这个决定成不成立的主战场。 */
@PreviewTest
@Preview(name = "今日页-有额度", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayAheadPreview() = Frame(buildState(24), LightRitualColors)

/** 一项都没做。删掉悬浮主按钮之后，这一屏还剩什么就靠这张图看。 */
@PreviewTest
@Preview(name = "今日页-一项没做", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayEmptyPreview() = Frame(buildState(0), LightRitualColors)

/**
 * 深色探针 —— 全项目**唯一一张**深色图。
 *
 * 2026-08-07 起出货的是亮色，所以关卡按亮色建（每一页都截）。
 * 但 `DarkRitualColors` 没删（换回去只要改 `RitualTheme` 的默认值一处），
 * 留这一张是为了它烂掉的时候有人吱一声——比如以后有人往组件里写死一个亮色色值，
 * 亮色下看不出来，这张图会变。
 *
 * ⚠️ 别拿它当"深色也验过了"。深色只有今日页这一张，其余页面都没有。
 */
@PreviewTest
@Preview(name = "深色探针-今日页", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DarkCanaryPreview() = Frame(buildState(14), DarkRitualColors)
