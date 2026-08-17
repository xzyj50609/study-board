package com.zyj.ritual.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 「这次记多少个」面板。长按打卡按钮弹出来。
 *
 * 为什么需要它：打卡按钮的数量一直等于今天的计划额度（用户是 40），
 * 但用户在「不背单词」里多学一次是一组 20 个。想记 20 的时候点一下就变成 40，
 * 等于记了 20 个根本没背的词——数据从此和现实对不上，而且屏幕上完全看不出来。
 *
 * 为什么是长按不是常驻控件：短按「+40」是每天的主路径，一天点一次，
 * 不能为了偶尔改个数就把它拆成「先调数再确认」两步。长按是加法不是改法。
 * 代价是长按这件事屏幕上看不见，所以调用方**必须**在按钮附近写明可以长按
 * （见 VocabTodayCard / VocabCalendarScreen 里那行提示小字），否则这个功能等于不存在。
 *
 * 上限不是随便定的：`max` 传的是「还没背的词数」。填一个比这还大的数，
 * 意味着背完了还在背，只能是填错了。这时候拦住并把原因写在屏幕上，
 * 不静默截断成上限值——静默截断的话用户以为记了 99999，实际记了 1503，
 * 两个数都不是他想要的，而他不会知道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAmountSheet(
    title: String,
    initialAmount: Int,
    max: Int,
    presets: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var amount by remember { mutableStateOf(initialAmount.coerceIn(1, maxOf(max, 1))) }

    val error: String? = when {
        amount < 1 -> "至少要记 1 个"
        amount > max -> "最多还能记 $max 个，这本词书就背完了"
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RitualColors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = RitualSpace.screenPadding,
                    end = RitualSpace.screenPadding,
                    bottom = RitualSpace.sectionGap,
                ),
        ) {
            Text(title, style = RitualTypography.headlineSmall)
            Spacer(Modifier.height(RitualSpace.listGap))

            NumberStepperField(
                label = "这次记多少个",
                value = amount,
                onValueChange = { amount = it },
                step = 5,
                min = 1,
                max = maxOf(max, 1),
                unit = "个",
            )

            Spacer(Modifier.height(RitualSpace.listGap))

            PresetChipsRow(
                presets = presets,
                selected = amount,
                onSelect = { amount = it },
            )

            if (error != null) {
                Spacer(Modifier.height(RitualSpace.listGap))
                Text(
                    error,
                    style = RitualTypography.bodySmall.copy(color = RitualColors.warnText),
                )
            }

            Spacer(Modifier.height(RitualSpace.sectionGap))

            PrimaryPill(
                text = "记 $amount 个",
                enabled = error == null,
                onClick = { onConfirm(amount) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        }
    }
}
