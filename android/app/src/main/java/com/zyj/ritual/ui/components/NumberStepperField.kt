package com.zyj.ritual.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSize
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 数字输入卡：大数字居中 + 左右加减圆钮，也能直接点数字改。
 *
 * 为什么不用 Material 的 OutlinedTextField：全 App 只有背词设置那一页用了它，
 * 于是那一页在一片米白纸感里冒出一圈紫色描边，跟别处完全不像同一个 App。
 * 这个组件跟首次设置页（SetupScreen）用的是同一套语言：卡片 + 大数字 + 圆钮。
 */
@Composable
fun NumberStepperField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    min: Int = 0,
    max: Int = 999_999,
    unit: String? = null,
    hint: String? = null,
) {
    // 输入过程中允许出现"空"这种不合法中间态，所以本地留一份文本。
    // 直接把 value.toString() 当 TextField 的值会导致清空后立刻被塞回 0，删不掉。
    var text by remember { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
    ) {
        Text(
            label,
            style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
        )
        Spacer(Modifier.height(RitualSpace.listGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                symbol = "−",
                enabled = value > min,
                onClick = { onValueChange((value - step).coerceIn(min, max)) },
            )

            // 单位放在数字正下方，不放旁边。
            // 放旁边的话，数字在定宽框里居中、单位贴着框右缘，两者中间会空出一大段，
            // 看着像两个不相干的东西。首次设置页那几张卡片用的也是「大数字 + 底下一行小字」。
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(6)
                        text = digits
                        digits.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                    },
                    singleLine = true,
                    textStyle = RitualTypography.headlineLarge.copy(
                        fontSize = RitualTypeSize.h1,
                        color = RitualColors.accentInk,
                        fontFamily = RitualFontFamilies.num,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(RitualColors.accentInk),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
                if (unit != null) {
                    Text(
                        unit,
                        style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            StepButton(
                symbol = "+",
                enabled = value < max,
                onClick = { onValueChange((value + step).coerceIn(min, max)) },
            )
        }

        if (hint != null) {
            Spacer(Modifier.height(RitualSpace.listGap))
            Text(
                hint,
                style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
            )
        }
    }
}

/**
 * 一排常用档位。点一下直接换数，不用按 20 次加号。
 */
@Composable
fun PresetChipsRow(
    presets: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RitualSpace.unit * 2),
    ) {
        presets.forEach { preset ->
            val isSelected = preset == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(RitualRadius.button))
                    .background(if (isSelected) RitualColors.accentInk else RitualColors.surfaceLow)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) RitualColors.accentInk else RitualColors.outline,
                        shape = RoundedCornerShape(RitualRadius.button),
                    )
                    .clickable { onSelect(preset) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$preset$suffix",
                    style = RitualTypography.bodyMedium.copy(
                        color = if (isSelected) RitualColors.onAccent else RitualColors.onBg,
                        fontFamily = RitualFontFamilies.num,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (enabled) RitualColors.surface else RitualColors.surface.copy(alpha = 0.3f)
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
        Text(symbol, style = RitualTypography.headlineSmall.copy(color = contentColor))
    }
}
