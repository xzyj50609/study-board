package com.zyj.ritual.ui.screens.setup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.components.PrimaryPill
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography

/** UI 测试锚点。 */
object SetupTestTags {
    const val START_BUTTON = "setup_start_button"
    const val ENTER_HOME_BUTTON = "setup_enter_home_button"
    const val SAVED_CARD = "setup_saved_card"
}

/**
 * 首次设置向导。
 *
 * 按原型样式：顶部 SETUP 标签、大标题、三张卡片（文章总数/已经学完/基础节奏）、
 * 预览金卡、底部主按钮。
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onStart: () -> Unit,
) {
    SetupScreenContent(
        form = SetupFormState(
            totalArticles = viewModel.totalArticles,
            completedArticles = viewModel.completedArticles,
            daysPerArticle = viewModel.daysPerArticle,
            nextArticle = viewModel.nextArticle,
            remainingArticles = viewModel.remainingArticles,
            totalPlanDays = viewModel.totalPlanDays,
            expectedCompletionDate = viewModel.expectedCompletionDate,
            saveState = viewModel.saveState,
        ),
        onIncrement = { viewModel.incrementCompleted() },
        onDecrement = { viewModel.decrementCompleted() },
        onSelectDays = { viewModel.daysPerArticle = it },
        onSave = { viewModel.savePlan() },
        onStart = onStart,
    )
}

/**
 * 首次设置页当前填了什么的快照。
 * 都是从 [SetupViewModel] 的派生属性抄下来的，没有新逻辑。
 */
internal data class SetupFormState(
    val totalArticles: Int,
    val completedArticles: Int,
    val daysPerArticle: Int,
    val nextArticle: Int,
    val remainingArticles: Int,
    val totalPlanDays: Int,
    val expectedCompletionDate: java.time.LocalDate,
    val saveState: SetupViewModel.SaveState,
)

/**
 * 首次设置页的纯展示部分。
 *
 * 为什么要从 [SetupScreen] 里拆出来：外层握着 ViewModel，而 ViewModel 要 StudyRepository
 * （Room + DataStore），截图测试造不出来，于是**新用户装上看到的第一屏一张图都没有**。
 * 拆出这一层只要喂一个数据类就能渲染。
 *
 * 拆的是渲染，不是行为：外层签名一个字没改，`SetupWiringTest`（盯着那次
 * "按钮没接线"事故的那道关卡）驱动的仍然是外层，照旧生效。
 */
@Composable
internal fun SetupScreenContent(
    form: SetupFormState,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSelectDays: (Int) -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
) {
    // 结构：上半部分可滚动，底部操作区固定。
    // 原来整页是一个不可滚动的 Column + weight(1f) 撑开，
    // 保存成功后多出一张确认卡就把「进入今日」按钮挤出屏幕、点不到了。
    // 字体放大到 200% 时同样会溢出，所以这里一次性改成滚动布局。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .padding(horizontal = RitualSpace.screenPadding),
    ) {
      Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
      ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))
        Spacer(Modifier.height(RitualSpace.sectionGap))

        // SETUP 标签
        Text(
            "SETUP · 首次设置",
            style = RitualTypography.labelSmall,
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        // 大标题
        Text(
            text = "先确认你的起点和节奏",
            style = RitualTypography.headlineLarge,
        )

        Text(
            "这些之后都能在设置里改。App 按北京时间判断日期。",
            style = RitualTypography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 卡片 1：文章总数
        SetupCard(
            label = "文章总数",
            content = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${form.totalArticles}",
                        style = RitualTypography.displayLarge.copy(
                            fontSize = RitualTypeSize.h1,
                            color = RitualColors.onBg,
                            fontFamily = RitualFontFamilies.num,
                            fontWeight = FontWeight.Light,
                        ),
                    )
                    Text(
                        " 篇 · 每篇 6 项 = ${form.totalArticles * 6} 项",
                        style = RitualTypography.bodyMedium,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            },
        )

        // 卡片 2：已经学完
        SetupCard(
            label = "已经学完",
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 减号
                        IconCircleButton(
                            onClick = onDecrement,
                            enabled = form.completedArticles > 0,
                        ) {
                            Text("−", style = RitualTypography.headlineSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${form.completedArticles}",
                                style = RitualTypography.displayLarge.copy(
                                    fontSize = 48.sp.takeIf { it.value > 0 } ?: RitualTypeSize.display,
                                    color = RitualColors.accentInk,
                                    fontFamily = RitualFontFamilies.num,
                                    fontWeight = FontWeight.Light,
                                ),
                            )
                            Text(
                                "篇 · 下一篇是第 ${form.nextArticle} 篇",
                                style = RitualTypography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        // 加号
                        IconCircleButton(
                            onClick = onIncrement,
                            enabled = form.completedArticles < form.totalArticles - 1,
                        ) {
                            Text("+", style = RitualTypography.headlineSmall)
                        }
                    }
                }
            },
        )

        // 卡片 3：基础节奏
        SetupCard(
            label = "基础节奏",
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RitualSpace.listGap),
                ) {
                    listOf(1, 2, 3).forEach { days ->
                        val selected = form.daysPerArticle == days
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(RitualRadius.button))
                                .background(
                                    if (selected) RitualColors.surfaceSel
                                    else RitualColors.surfaceLow
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) RitualColors.accentInk.copy(alpha = 0.4f)
                                    else RitualColors.outline,
                                    shape = RoundedCornerShape(RitualRadius.button),
                                )
                                .clickable { onSelectDays(days) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$days 天/篇",
                                style = RitualTypography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (selected) RitualColors.accentInk else RitualColors.onBg,
                                ),
                            )
                        }
                    }
                }
            },
        )

        Text(
            "节奏只用来排计划和算额度，不会锁住你——任何一天都可以多做。",
            style = RitualTypography.bodySmall,
            modifier = Modifier.padding(top = RitualSpace.listGap),
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 预览金卡。保存成功后由确认卡取代，这里就不再重复占位。
        if (form.saveState !is SetupViewModel.SaveState.Saved) {
            PreviewCard(
                remaining = form.remainingArticles,
                planDays = form.totalPlanDays,
                eta = form.expectedCompletionDate,
            )
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))
      }  // 可滚动内容区结束

        // —— 固定底部：保存 → 确认 → 进入主页，分成两步走 ——
        when (val saveState = form.saveState) {

            is SetupViewModel.SaveState.Saved -> {
                // 已落盘。给一条肉眼可见的凭据，再给一个明确的进入主页按钮。
                SavedConfirmCard(plan = saveState.plan)
                Spacer(Modifier.height(RitualSpace.listGap))
                PrimaryPill(
                    text = "进入今日 ›",
                    onClick = onStart,
                    modifier = Modifier.testTag(SetupTestTags.ENTER_HOME_BUTTON),
                )
            }

            else -> {
                if (saveState is SetupViewModel.SaveState.Failed) {
                    Text(
                        "保存失败：${saveState.message}",
                        style = RitualTypography.bodySmall.copy(color = RitualColors.warnText),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val saving = saveState is SetupViewModel.SaveState.Saving
                PrimaryPill(
                    text = if (saving) "正在保存…" else "保存并开始第 ${form.nextArticle} 篇",
                    enabled = !saving,
                    onClick = onSave,
                    modifier = Modifier.testTag(SetupTestTags.START_BUTTON),
                )
            }
        }

        Spacer(Modifier.height(RitualSpace.navBarHeight))
    }
}

// ———————————— 子组件 ————————————

@Composable
private fun SetupCard(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RitualSpace.listGap)
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
    ) {
        Text(
            label,
            style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
        )
        Spacer(Modifier.height(RitualSpace.listGap))
        content()
    }
}

/**
 * 保存成功后的确认卡。
 *
 * 存在的理由：上一版"点了按钮页面就跳走"，跳走本身被当成了成功的证据，
 * 而实际上什么都没存。现在把存进去的数字念一遍给用户看，
 * 数字对得上才说明真的落盘了。
 */
@Composable
private fun SavedConfirmCard(plan: com.zyj.ritual.domain.model.Plan) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .border(
                width = 1.dp,
                color = RitualColors.accentInk.copy(alpha = 0.35f),
                shape = RoundedCornerShape(RitualRadius.cardLg),
            )
            .padding(RitualSpace.cardPadding)
            .testTag(SetupTestTags.SAVED_CARD),
    ) {
        Text(
            "计划已保存",
            style = RitualTypography.titleMedium.copy(
                color = RitualColors.accentInk,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            "共 ${plan.totalArticles} 篇 · 起点已学完 ${plan.completedBeforeStart} 篇 · " +
                "从第 ${plan.startArticle} 篇开始 · ${plan.daysPerArticle} 天/篇",
            style = RitualTypography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "数据已写入本机，杀掉进程也不会丢。",
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PreviewCard(
    remaining: Int,
    planDays: Int,
    eta: java.time.LocalDate,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .border(
                width = 1.dp,
                color = RitualColors.accentGold.copy(alpha = 0.25f),
                shape = RoundedCornerShape(RitualRadius.cardLg),
            )
            .padding(RitualSpace.cardPadding),
    ) {
        Text(
            "剩余 $remaining 篇 · 基础计划 $planDays 个学习日",
            style = RitualTypography.titleMedium.copy(
                color = RitualColors.accentGold,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            "预计 ${eta.monthValue} 月 ${eta.dayOfMonth} 日完成",
            style = RitualTypography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun IconCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val bgColor = if (enabled) RitualColors.surfaceLow else RitualColors.surfaceLow.copy(alpha = 0.3f)
    val contentColor = if (enabled) RitualColors.onBg else RitualColors.onBgFaint
    Box(
        modifier = Modifier
            .size(RitualSize.iconButton)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, RitualColors.outline.copy(alpha = if (enabled) 1f else 0.3f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.ProvideTextStyle(
            value = androidx.compose.ui.text.TextStyle(color = contentColor)
        ) {
            content()
        }
    }
}

// 这里原来有一个**私有的 PrimaryPill 副本**，和 ui/components/PrimaryPill.kt 长得几乎一样，
// 但少了单行省略、禁用态的处理也不同。它把同名的共享组件遮住了，于是 2026-08-07 修
// 「禁用态白字看不见」时改了共享组件，本页却纹丝不动——查了才发现改的根本不是同一个东西。
// 已删除，本页改用共享组件。⚠️ 别再在页面文件里放同名私有副本。

// 临时：Int.sp 扩展
private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Double.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
