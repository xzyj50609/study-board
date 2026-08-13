package com.zyj.ritual.ui.screens.vocab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyj.ritual.domain.vocab.VocabState
import com.zyj.ritual.ui.components.PrimaryPill
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 「今天要复习多少」校准面板。移植自网页版 v15 的 `#backlog-sheet`。
 *
 * 这个面板是**整条复习链路唯一的入口**。安卓版之前没有它，
 * `reviewDueDate` 永远是空串 → `reviewDueToday` 永远是 null →
 * 复习进度条一次都没画出来过。
 *
 * ⚠️ 红线：**今天还没校准时输入框必须留空，不许拿昨天的数预填。**
 * 那个数看起来像是今天的，用户一按保存就把一个陈旧的目标坐实了，
 * 而且屏幕上完全看不出来它是昨天的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDueSheet(
    state: VocabState,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    // 已校准就填现值（覆盖语义下预填只能填现值），没校准就留空
    var input by remember {
        mutableStateOf(state.reviewDueToday?.toString() ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RitualColors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RitualSpace.screenPadding)
                .padding(bottom = 32.dp),
        ) {
            Text("今天要复习多少", style = RitualTypography.titleMedium)

            Spacer(Modifier.height(RitualSpace.listGap))

            Text(
                "照不背单词首页那个「复习」数字填。它每天早上按你每个词的记忆状态重算一遍，" +
                    "学了新词就会变大——这是正常的，不是欠债。",
                style = RitualTypography.bodySmall,
                color = RitualColors.onBgMuted,
            )

            Spacer(Modifier.height(RitualSpace.listGap))

            // 顺带把累计摆出来：打开这个面板是每天都会做的动作，
            // 这一刻正好用来提醒"你一共已经复习了这么多"
            Text(
                "到今天为止一共复习了 ${state.reviewTotal} 个 · 今天已复习 ${state.todayReview}",
                style = RitualTypography.bodyMedium,
                color = RitualColors.accentReview,
            )

            Spacer(Modifier.height(RitualSpace.sectionGap))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filter { c -> c.isDigit() }.take(5)
                        error = null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("个") },
                    isError = error != null,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                PrimaryPill(
                    text = "保存",
                    onClick = {
                        val value = input.toIntOrNull()
                        if (value == null || value < 0) {
                            // 保存失败必须说出来，不许静默关掉面板——
                            // 静默关掉会让人以为存上了
                            error = "填一个 0 或以上的整数"
                        } else {
                            onSave(value)
                        }
                    },
                    modifier = Modifier.width(96.dp),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    error.orEmpty(),
                    style = RitualTypography.bodySmall,
                    color = RitualColors.warnText,
                )
            }
        }
    }
}
