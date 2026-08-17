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
 * 「这次背了几个」面板。点打卡按钮旁边的「改」弹出来。
 *
 * ## 为什么要有它
 *
 * 打卡按钮记的量固定等于今天的计划额度（比如 40）。可实际背词不是按额度发生的：
 * 有时多学一组 20，有时 28 个，有时只多背了 8 个。只能记 40 的话，
 * 那 8 个要么不记（数据丢了），要么记成 40（数据是假的）——两条都不行。
 *
 * ## 为什么加减号一步只走 1
 *
 * 上一版一步走 5、快捷档给的是 10/20/40，等于默认「你背的一定是 5 的倍数」。
 * 用户明确否掉了这个假设：背了 28 个就是 28 个，不该被凑成 30。
 * 一步走 1 慢，但配上下面那排快捷档（跟着今天的实际情况算，不是写死的整数），
 * 常用的数一下就点到，冷门的数也填得出来。
 *
 * ## 快捷档为什么不写死
 *
 * 写死 10/20/40 只在「今天目标 40」时说得通，目标改成 60 就全不对了。
 * 这里的档位从「今天还差多少」和「今天目标」算出来，跟着设置走。
 *
 * @param todayDone 今天已经记了多少（用来算「还差多少」这个档位）
 * @param todayTarget 今天的目标；复习那边可能没校准，所以允许为空
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAmountSheet(
    title: String,
    initialAmount: Int,
    max: Int,
    todayDone: Int,
    todayTarget: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val upperBound = maxOf(max, 1)
    var amount by remember { mutableStateOf(initialAmount.coerceIn(1, upperBound)) }

    val error: String? = when {
        amount < 1 -> "至少要记 1 个"
        amount > max -> "最多还能记 $max 个，这本词书就背完了"
        else -> null
    }

    // 档位跟着今天的实际情况走，不写死。
    // 「还差 N」是最常用的一档——背完今天的量就点它，不用心算。
    val remainToday = todayTarget?.let { it - todayDone }?.takeIf { it in 1..upperBound }
    val presets = buildList {
        remainToday?.let { add(it) }
        listOf(10, 20, todayTarget ?: 40).forEach { candidate ->
            if (candidate in 1..upperBound && candidate !in this) add(candidate)
        }
    }.sorted().take(4)

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

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (todayTarget != null) {
                    "今天已记 $todayDone / $todayTarget · 背了几个就填几个，不用凑整"
                } else {
                    "今天已记 $todayDone · 背了几个就填几个，不用凑整"
                },
                style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
            )

            Spacer(Modifier.height(RitualSpace.listGap))

            NumberStepperField(
                label = "这次记多少个",
                value = amount,
                onValueChange = { amount = it },
                // 一步走 1：背了 28 个就该点得出 28，不许被凑成 30
                step = 1,
                min = 1,
                max = upperBound,
                unit = "个",
            )

            if (presets.isNotEmpty()) {
                Spacer(Modifier.height(RitualSpace.listGap))
                PresetChipsRow(
                    presets = presets,
                    selected = amount,
                    onSelect = { amount = it },
                )
            }

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
