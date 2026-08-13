package com.zyj.ritual

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zyj.ritual.ui.state.RitualStateHost
import com.zyj.ritual.ui.state.RitualTestTags
import com.zyj.ritual.ui.state.RitualUiState
import com.zyj.ritual.ui.theme.RitualTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 四种页面状态各自渲染对了没有。
 *
 * 重点是 Error 分支：上一版把异常吞掉、显示成「加载中」，
 * 结果排查只能靠猜。现在异常必须显示成异常。
 */
@RunWith(AndroidJUnit4::class)
class RitualStateHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 加载中显示加载态() {
        composeRule.setContent {
            RitualTheme {
                RitualStateHost(RitualUiState.Loading, onGoToSetup = {}) { Text("不该出现") }
            }
        }
        composeRule.onNodeWithTag(RitualTestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun 没有计划时引导去设置而不是卡加载中() {
        var wentToSetup = false
        composeRule.setContent {
            RitualTheme {
                RitualStateHost(RitualUiState.NotSetUp, onGoToSetup = { wentToSetup = true }) {
                    Text("不该出现")
                }
            }
        }
        composeRule.onNodeWithTag(RitualTestTags.NOT_SET_UP).assertIsDisplayed()
        composeRule.onNodeWithText("去设置").performClick()
        assertTrue("未设置态必须能一键回到设置页", wentToSetup)
    }

    @Test
    fun 出错时把真实原因显示出来() {
        composeRule.setContent {
            RitualTheme {
                RitualStateHost(
                    RitualUiState.Error("数据库炸了", "java.lang.IllegalStateException"),
                    onGoToSetup = {},
                ) { Text("不该出现") }
            }
        }
        composeRule.onNodeWithTag(RitualTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("数据库炸了").assertIsDisplayed()
        // 详情默认收起，点开能看到 stacktrace
        composeRule.onNodeWithText("展开详情").performClick()
        composeRule.onNodeWithText("java.lang.IllegalStateException").assertIsDisplayed()
    }
}
