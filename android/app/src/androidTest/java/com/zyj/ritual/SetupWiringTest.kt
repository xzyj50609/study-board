package com.zyj.ritual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zyj.ritual.ui.screens.setup.SetupScreen
import com.zyj.ritual.ui.screens.setup.SetupTestTags
import com.zyj.ritual.ui.screens.setup.SetupViewModel
import com.zyj.ritual.ui.theme.RitualTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首次设置的接线测试。
 *
 * 这个文件存在的唯一理由：2026-08-06 真机翻车。
 * 当时 SetupScreen 的主按钮 `onClick = onStart`，只做了页面跳转，
 * 从没调用过保存函数，于是 Plan 从没写进 DataStore，
 * 今日/月历/进度三个 tab 永远停在「加载中」。
 *
 * 单元测试全绿、构建成功、APK 能装能开——没有任何一道关卡拦得住它，
 * 因为没人验证过「点了按钮之后数据到底有没有落盘」。这条测试就是那道关卡。
 *
 * 现在流程是两步：保存 → 确认卡 → 用户主动点「进入今日」。
 */
@RunWith(AndroidJUnit4::class)
class SetupWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var env: TestEnv

    @Before
    fun setUp() {
        env = TestEnv()
    }

    @After
    fun tearDown() {
        env.close()
    }

    /** 渲染设置页，返回「进入今日」被点了没有的标记。 */
    private fun renderSetup(onStart: () -> Unit = {}): SetupViewModel {
        val viewModel = SetupViewModel(env.repository)
        composeRule.setContent {
            RitualTheme {
                SetupScreen(viewModel = viewModel, onStart = onStart)
            }
        }
        return viewModel
    }

    private fun clickSaveAndWait() {
        composeRule.onNodeWithTag(SetupTestTags.START_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTagOrEmpty(SetupTestTags.SAVED_CARD)
        }
    }

    /** waitUntil 里不能用会抛异常的断言，这里换成布尔判断。 */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagOrEmpty(
        tag: String,
    ): Boolean = onAllNodes(
        androidx.compose.ui.test.hasTestTag(tag)
    ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun 点保存按钮必须把计划写进存储而不是只跳页面() {
        renderSetup()
        clickSaveAndWait()

        runBlocking {
            val plan = env.repository.getPlan()
            assertNotNull("点了保存之后 Plan 必须已经落盘，否则首页只会显示加载中", plan)
            assertEquals(SetupViewModel.DEFAULT_TOTAL_ARTICLES, plan!!.totalArticles)
            assertEquals(SetupViewModel.DEFAULT_COMPLETED_ARTICLES, plan.completedBeforeStart)
            assertEquals(SetupViewModel.DEFAULT_COMPLETED_ARTICLES + 1, plan.startArticle)
        }
    }

    @Test
    fun 保存成功之后首页状态必须是非空的() {
        renderSetup()
        clickSaveAndWait()

        runBlocking {
            val state = env.repository.todayStateFlow().first()
            assertNotNull("保存后 todayStateFlow 必须产出非空状态", state)
            // 起点 12 篇 × 6 项 = 72 条 IMPORTED 记录
            assertEquals(72, state!!.records.size)
            assertEquals(13, state.progress.currentArticle)
            assertTrue(state.copy.headline.isNotBlank())
        }
    }

    @Test
    fun 保存前不显示进入主页按钮保存后才显示() {
        renderSetup()

        // 还没保存：只有保存按钮，没有进入主页按钮
        composeRule.onNodeWithTag(SetupTestTags.START_BUTTON).assertIsDisplayed()
        assertFalse(
            "没保存就不该出现进入主页的入口",
            composeRule.onAllNodesWithTagOrEmpty(SetupTestTags.ENTER_HOME_BUTTON),
        )

        clickSaveAndWait()

        // 保存后：确认卡 + 进入主页按钮都在
        composeRule.onNodeWithTag(SetupTestTags.SAVED_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(SetupTestTags.ENTER_HOME_BUTTON).assertIsDisplayed()
    }

    @Test
    fun 进入主页按钮点下去那一刻计划必须已经在存储里() {
        var planReadableWhenNavigating: Boolean? = null
        renderSetup(onStart = {
            planReadableWhenNavigating = runBlocking { env.repository.getPlan() != null }
        })

        clickSaveAndWait()
        composeRule.onNodeWithTag(SetupTestTags.ENTER_HOME_BUTTON).performClick()

        assertEquals(
            "跳主页的那一刻，计划必须已经能从存储读出来",
            true,
            planReadableWhenNavigating,
        )
    }

    @Test
    fun 没点保存时存储里什么都没有() {
        renderSetup()
        composeRule.waitForIdle()
        runBlocking {
            assertNull("光是打开设置页不该写任何东西", env.repository.getPlan())
        }
    }
}
