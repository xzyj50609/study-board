package com.zyj.ritual.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.ui.screens.progress.ProgressScreen
import com.zyj.ritual.ui.theme.LightRitualColors
import com.zyj.ritual.ui.theme.RitualColorScheme
import com.zyj.ritual.ui.theme.RitualTheme

/**
 * 进度页截图。
 *
 * 这一页在亮色下风险最集中：
 * - 60sp 的大百分比数字（主文字色）
 * - 42 格矩阵：实心块上压白字（onAccent 在亮色下要反过来）
 * - 双段进度条：蓝段和金段接缝处会不会错位（老版本靠 dp 偏移，屏宽一变就飘）
 * - 背单词摘要卡：金色进度条 + 紫色档位文字压在浅卡片底上
 *
 * 历史记录 2026-08-08 挪去二级页了，这一页不再有它。
 */

@Composable
private fun Frame(
    state: TodayState,
    colors: RitualColorScheme,
    vocabState: VocabAggregateState? = null,
) {
    RitualTheme(colors = colors) {
        ProgressScreen(state = state, vocabState = vocabState)
    }
}

/**
 * 26 项 = 4 篇整 + 第 5 篇做了 2 项，所以矩阵里三种格子都在：
 * 1-4 已完成（实心蓝）、5 进行中（40% 蓝）、6 往后未开始（卡片底）。
 * 亮色下"40% 蓝"和"未开始"会不会挤成一样，就看这张。
 */
@PreviewTest
@Preview(name = "进度页-有额度", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProgressAheadPreview() = Frame(buildState(26, withHistory = true), LightRitualColors)

/**
 * 带背单词摘要卡的版本。
 *
 * ⚠️ 上面那张 `ProgressAheadPreview` **拍不到摘要卡**——它没传 vocabState，
 * 卡片整块不渲染。也就是说光有那一张的话，「进度页能看到考研核心词汇进度」
 * 这个功能在关卡里是空的：代码在、屏幕上没有，而测试照样全绿。
 * 这张就是补这个洞的。
 */
@PreviewTest
@Preview(name = "进度页-含背单词摘要", widthDp = 390, heightDp = 940, showBackground = true)
@Composable
private fun ProgressWithVocabPreview() = Frame(
    state = buildState(26, withHistory = true),
    colors = LightRitualColors,
    vocabState = buildProgressVocabAggregate(),
)
