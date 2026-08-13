package com.zyj.ritual.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.zyj.ritual.domain.vocab.VocabRecord
import com.zyj.ritual.ui.screens.history.HistoryScreen
import com.zyj.ritual.ui.theme.LightRitualColors
import com.zyj.ritual.ui.theme.RitualTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * 历史二级页截图。
 *
 * 这一页最容易出的错是「时刻」那一行：
 * 备份导入的老记录 `createdAt` 是 0，要是有人直接格式化，会打出「1.1 8:00」。
 * 下面那张里 8/1 那几条就是没时刻的，基准图上它们**只有一行标题、没有第二行小字**——
 * 混排里一眼能看出来跟别的不一样。哪天有人给它编了个时刻，这张图立刻不匹配。
 */

private val BEIJING = ZoneId.of("Asia/Shanghai")

private fun ts(day: Int, hour: Int, minute: Int) =
    LocalDate.of(2026, 8, day).atTime(hour, minute).atZone(BEIJING).toInstant().toEpochMilli()

@PreviewTest
@Preview(name = "历史页-两科混排", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HistoryMixedPreview() {
    RitualTheme(colors = LightRitualColors) {
        HistoryScreen(
            history = buildState(26, withHistory = true).history,
            vocabRecords = listOf(
                VocabRecord("2026-08-06", 20, "new", createdAt = ts(6, 19, 55)),
                VocabRecord("2026-08-06", 45, "backlog", createdAt = ts(6, 20, 2)),
                VocabRecord("2026-08-05", 20, "new", createdAt = ts(5, 21, 30)),
                // 下面两条是备份导入的老记录，没有时刻
                VocabRecord("2026-08-01", 20, "new", createdAt = 0),
                VocabRecord("2026-08-01", 30, "backlog", createdAt = 0),
            ),
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(name = "历史页-空", widthDp = 390, heightDp = 400, showBackground = true)
@Composable
private fun HistoryEmptyPreview() {
    RitualTheme(colors = LightRitualColors) {
        HistoryScreen(history = emptyList(), vocabRecords = emptyList(), onBack = {})
    }
}
