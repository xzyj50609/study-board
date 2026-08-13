package com.zyj.ritual.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.ui.screens.article.ArticleScreen
import com.zyj.ritual.ui.screens.settings.SettingsScreenContent
import com.zyj.ritual.ui.screens.setup.SetupFormState
import com.zyj.ritual.ui.screens.setup.SetupScreenContent
import com.zyj.ritual.ui.screens.setup.SetupViewModel
import com.zyj.ritual.ui.state.RitualStateHost
import com.zyj.ritual.ui.state.RitualUiState
import com.zyj.ritual.ui.theme.LightRitualColors
import com.zyj.ritual.ui.theme.RitualTheme
import java.time.LocalDate

/**
 * 今日 / 进度 / 月历之外的页面。
 *
 * 这些页在 2026-08-07 之前一张图都没有——设置页和首次设置页因为握着 ViewModel
 * 渲染不出来，为此把它们的展示部分拆成了 `XxxScreenContent`。
 *
 * 亮色是出货配色，所以这里全部只截亮色。
 * 深色留了今日页 4 张当探针（见 TodayScreenshots.kt），够发现它有没有烂掉。
 */

private fun light(): com.zyj.ritual.ui.theme.RitualColorScheme = LightRitualColors

// ———————————— 首次设置页 ————————————
// 新用户装上看到的第一屏。这一页出过"按钮没接线"的事故，值得盯着。

/** 刚进来，还没保存。底部是「保存并开始第 13 篇」。 */
@PreviewTest
@Preview(name = "首次设置页-未保存", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SetupIdlePreview() {
    RitualTheme(colors = light()) {
        SetupScreenContent(
            form = setupForm(SetupViewModel.SaveState.Idle),
            onIncrement = {}, onDecrement = {}, onSelectDays = {}, onSave = {}, onStart = {},
        )
    }
}

/** 保存成功：预览金卡被确认卡取代，底部换成「进入今日 ›」。 */
@PreviewTest
@Preview(name = "首次设置页-已保存", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SetupSavedPreview() {
    RitualTheme(colors = light()) {
        SetupScreenContent(
            form = setupForm(
                SetupViewModel.SaveState.Saved(
                    Plan(
                        totalArticles = 42,
                        // 起点已学完 12 → 从第 13 篇开始。确认卡会把这两个数都念出来，
                        // 对不上就说明存进去的和填的不是一回事。
                        completedBeforeStart = 12,
                        startArticle = 13,
                        daysPerArticle = 2,
                        planStartDate = LocalDate.of(2026, 8, 1),
                    )
                )
            ),
            onIncrement = {}, onDecrement = {}, onSelectDays = {}, onSave = {}, onStart = {},
        )
    }
}

/**
 * 正在保存：按钮变禁用态，文案换成「正在保存…」。
 * 这张专门盯「在等」和「坏了」有没有被压成同一个画面——
 * 字看不见的话，用户只会以为点了没反应。
 */
@PreviewTest
@Preview(name = "首次设置页-正在保存", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SetupSavingPreview() {
    RitualTheme(colors = light()) {
        SetupScreenContent(
            form = setupForm(SetupViewModel.SaveState.Saving),
            onIncrement = {}, onDecrement = {}, onSelectDays = {}, onSave = {}, onStart = {},
        )
    }
}

/** 保存失败：错误原因必须写在屏幕上，不许只是没反应。 */
@PreviewTest
@Preview(name = "首次设置页-保存失败", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SetupFailedPreview() {
    RitualTheme(colors = light()) {
        SetupScreenContent(
            form = setupForm(SetupViewModel.SaveState.Failed("写入存储失败，请重试")),
            onIncrement = {}, onDecrement = {}, onSelectDays = {}, onSave = {}, onStart = {},
        )
    }
}

private fun setupForm(saveState: SetupViewModel.SaveState) = SetupFormState(
    totalArticles = 42,
    completedArticles = 12,
    daysPerArticle = 2,
    nextArticle = 13,
    remainingArticles = 30,
    totalPlanDays = 60,
    expectedCompletionDate = LocalDate.of(2026, 9, 29),
    saveState = saveState,
)

// ———————————— 设置页 ————————————

@PreviewTest
@Preview(name = "设置页", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsPreview() {
    RitualTheme(colors = light()) {
        SettingsScreenContent(
            state = buildState(26),
            onReEnterSetup = {}, onReschedule = {}, onUndoLastArticle = {},
            onChangeDaysPerArticle = {}, onExportClick = {}, onImportClick = {},
            feedback = null,
        )
    }
}

/** 导入导出之后那句反馈。屏幕上必须看得见结果，不能只闪一下。 */
@PreviewTest
@Preview(name = "设置页-有反馈", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsFeedbackPreview() {
    RitualTheme(colors = light()) {
        SettingsScreenContent(
            state = buildState(26),
            onReEnterSetup = {}, onReschedule = {}, onUndoLastArticle = {},
            onChangeDaysPerArticle = {}, onExportClick = {}, onImportClick = {},
            feedback = "导出成功",
        )
    }
}

// ———————————— 文章详情页 ————————————

@PreviewTest
@Preview(name = "文章详情页", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ArticlePreview() {
    RitualTheme(colors = light()) {
        ArticleScreen(
            articleIndex = 5,
            state = buildState(26),
            onCheckTask = { _, _ -> },
            onUndoTask = { _, _ -> },
            onBack = {},
        )
    }
}

// ———————————— 四种状态外壳 ————————————
// 「加载中 / 没设置过 / 正常 / 出错」必须是四张不同的脸。
// 压成同一个 null 正是 2026-08-06「三个页面永久加载中」那次事故的成因，
// 所以这四张图是防复发的关卡，不是装饰。

@PreviewTest
@Preview(name = "状态-加载中", widthDp = 390, heightDp = 400, showBackground = true)
@Composable
private fun StateLoadingPreview() {
    RitualTheme(colors = light()) {
        RitualStateHost(uiState = RitualUiState.Loading, onGoToSetup = {}) {}
    }
}

@PreviewTest
@Preview(name = "状态-没设置过", widthDp = 390, heightDp = 400, showBackground = true)
@Composable
private fun StateNotSetUpPreview() {
    RitualTheme(colors = light()) {
        RitualStateHost(uiState = RitualUiState.NotSetUp, onGoToSetup = {}) {}
    }
}

@PreviewTest
@Preview(name = "状态-出错", widthDp = 390, heightDp = 400, showBackground = true)
@Composable
private fun StateErrorPreview() {
    RitualTheme(colors = light()) {
        RitualStateHost(
            uiState = RitualUiState.Error(
                message = "读取计划失败：database is locked",
                detail = "android.database.sqlite.SQLiteDatabaseLockedException\n" +
                    "  at com.zyj.ritual.data.repository.StudyRepository.planFlow",
            ),
            onGoToSetup = {},
        ) {}
    }
}
