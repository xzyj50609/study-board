package com.zyj.ritual.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 底部主按钮（大药丸）。
 *
 * 4 态：默认 / 按下（scale .98，交给 clickable 自己处理）/ 禁用（30% 不透明）/ 加载
 * 第一版先做默认 + 禁用两态，加载态等有需要再加。
 */
@Composable
fun PrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 禁用态不能靠「把底色调成 30% 透明」了事：那样底变浅，而字仍是压在实心底上用的
    // onAccent（亮色下是白），白字压浅灰＝什么都看不见。而禁用态承载的是
    // 「正在保存…」这种进行中文案，看不见就等于把「在等」和「坏了」压成了同一个画面。
    // 改成整套换成柔和但读得出来的一对（2026-08-07 亮色截图查出来的）。
    val containerColor = if (enabled) RitualColors.onBg else RitualColors.surfaceSel
    val labelColor = if (enabled) RitualColors.onAccent else RitualColors.onBgMuted
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RitualSize.primaryButtonH)
            .clip(RoundedCornerShape(RitualRadius.button))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 任务名最长的一条是「核对答案，标出错题和不确定的题」，
        // 按钮是固定 52dp 的胶囊，换行会撑破它并盖住下面的任务列表。
        // 这里锁单行 + 省略号；完整任务名在下方列表里看得到，不丢信息。
        Text(
            text,
            style = RitualTypography.titleMedium.copy(
                color = labelColor,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

/**
 * 次按钮（描边按钮）。
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val textColor = if (danger) RitualColors.warnText else RitualColors.onBg
    val borderColor = if (danger) RitualColors.warn.copy(alpha = 0.5f) else RitualColors.outline
    val alpha = if (enabled) 1f else 0.3f

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(RitualRadius.button))
            .background(Color.Transparent)
            .border(1.dp, borderColor.copy(alpha = alpha), RoundedCornerShape(RitualRadius.button))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = RitualTypography.bodyLarge.copy(
                color = textColor.copy(alpha = alpha),
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

