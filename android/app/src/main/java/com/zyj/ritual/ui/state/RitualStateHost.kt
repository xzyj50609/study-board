package com.zyj.ritual.ui.state

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.zyj.ritual.data.repository.TodayState
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypography

/** UI 测试用的锚点，正式包里也留着，成本为零。 */
object RitualTestTags {
    const val LOADING = "ritual_loading"
    const val NOT_SET_UP = "ritual_not_set_up"
    const val ERROR = "ritual_error"
    const val READY = "ritual_ready"
}

/**
 * 四个 tab 的统一状态外壳。
 *
 * 页面本身只写 Ready 分支（拿到的 TodayState 一定非空），
 * 加载、未设置、异常三种情况在这里一次性处理干净。
 */
@Composable
fun RitualStateHost(
    uiState: RitualUiState,
    onGoToSetup: () -> Unit,
    content: @Composable (TodayState) -> Unit,
) {
    when (uiState) {
        is RitualUiState.Loading -> LoadingBox()
        is RitualUiState.NotSetUp -> NotSetUpBox(onGoToSetup = onGoToSetup)
        is RitualUiState.Error -> ErrorBox(uiState)
        is RitualUiState.Ready -> Box(Modifier.testTag(RitualTestTags.READY)) {
            content(uiState.state)
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .padding(horizontal = RitualSpace.screenPadding)
            .testTag(RitualTestTags.LOADING),
    ) {
        Text(
            "加载中…",
            style = RitualTypography.bodySmall,
            modifier = Modifier.padding(top = RitualSpace.statusBarPad),
        )
    }
}

@Composable
private fun NotSetUpBox(onGoToSetup: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .padding(horizontal = RitualSpace.screenPadding)
            .testTag(RitualTestTags.NOT_SET_UP),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有计划", style = RitualTypography.headlineMedium)
            Spacer(Modifier.height(RitualSpace.listGap))
            Text(
                "先去设置起点和节奏，之后这里会显示今天要做的 6 项。",
                style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
            )
            Spacer(Modifier.height(RitualSpace.sectionGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RitualSize.primaryButtonH)
                    .clip(RoundedCornerShape(RitualRadius.button))
                    .background(RitualColors.onBg)
                    .clickable(onClick = onGoToSetup),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "去设置",
                    style = RitualTypography.titleMedium.copy(
                        color = RitualColors.onAccent,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

/**
 * 出错时把真实原因摆在屏幕上。
 *
 * 这一屏是上一轮翻车换来的：异常被 catch 吞掉、页面显示「加载中」，
 * 结果只能靠盲改代码猜根因。宁可界面丑一点，也要让问题自己说话。
 */
@Composable
private fun ErrorBox(error: RitualUiState.Error) {
    var showDetail by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .padding(horizontal = RitualSpace.screenPadding)
            .verticalScroll(rememberScrollState())
            .testTag(RitualTestTags.ERROR),
        verticalArrangement = Arrangement.spacedBy(RitualSpace.listGap),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))
        Text("数据读取失败", style = RitualTypography.headlineMedium)
        Text(
            error.message,
            style = RitualTypography.bodyMedium.copy(color = RitualColors.warnText),
        )
        if (error.detail != null) {
            Text(
                if (showDetail) "收起详情" else "展开详情",
                style = RitualTypography.bodySmall.copy(color = RitualColors.accentInk),
                modifier = Modifier.clickable { showDetail = !showDetail },
            )
            if (showDetail) {
                Text(
                    error.detail,
                    style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RitualRadius.button))
                        .background(RitualColors.surfaceLow)
                        .padding(RitualSpace.cardPadding),
                )
            }
        }
        Text(
            "可以在设置页导出备份后重装，数据不会丢。",
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
        )
        Spacer(Modifier.height(RitualSpace.navBarHeight))
    }
}
