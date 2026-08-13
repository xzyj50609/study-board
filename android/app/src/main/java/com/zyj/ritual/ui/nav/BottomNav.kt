package com.zyj.ritual.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypeSize
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 底部导航（金点 + 文字）。
 *
 * 按 Q5 裁决：用原型的"金点 + 文字标签"，不用四个 tab 图标。
 * 更克制、更符合"夜读"的气质。
 */
@Composable
fun RitualBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavItem(Routes.TODAY, "今日"),
        BottomNavItem(Routes.CALENDAR, "月历"),
        BottomNavItem(Routes.PROGRESS, "进度"),
        BottomNavItem(Routes.VOCAB, "背词"),
        BottomNavItem(Routes.SETTINGS, "设置"),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RitualSpace.navBarHeight)
            .background(RitualColors.bg)
            .padding(horizontal = RitualSpace.screenPadding),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(RitualRadius.pill))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onNavigate(item.route) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                // 金点（选中时显示）
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) RitualColors.accentGold
                            else Color.Transparent
                        ),
                )
                Text(
                    text = item.label,
                    style = RitualTypography.bodySmall.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Light,
                        color = if (selected) RitualColors.onBg else RitualColors.onBgMuted,
                        fontSize = RitualTypeSize.bodySm,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
)
