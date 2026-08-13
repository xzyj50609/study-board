package com.zyj.ritual.ui.screens.vocab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.domain.vocab.VocabState
import com.zyj.ritual.ui.components.ProgressBarLine
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualTypography

/**
 * 复习区：两行两条进度条。移植自网页版 v15 的 `.review-goal` + `.backlog-zone`。
 *
 * ```
 * 复习  ████████████████░░░░░░░░  544
 *                     第 2 档 · 还差 456
 * 今天  ██████████░░░░░░  38/45          改
 * ```
 *
 * ⚠️ 移植红线，四条，每条都是踩过的坑：
 *
 * 1. **累计条的分母只能是「下一档」，不能编一个总量。** 复习队列是不背单词 APP
 *    每天按 SRS 重算的，学新词还会把它推高，根本不存在「一共要复习 N 个」。
 *    硬编一个总量出来，条子填到 30% 也只是编出来的 30%。
 *    （288 那套「一次性欠账」就是在这里翻的车。）
 *
 * 2. **分子分母只准读 `reviewIntoLevel / reviewStep`，禁止在这里写
 *    `reviewTotal % reviewStep`。** 后者会绕过算法里的 `justHitStep` 分支：
 *    累计正好 500 那一天条子会从 99.8% 闪回 0%，看起来像进度被抹掉了。
 *    平时两者算出来一模一样，只有满档当天不一样——十几天才撞一次，
 *    撞上了也会被当成看错。
 *
 * 3. **两条必须分两行。** 一行版实测把里程碑条挤得比「今天」那条还短，
 *    等于在说今天更重要，跟这条存在的理由正好相反。
 *
 * 4. **今天没校准（reviewDueToday == null）就把今天那条整条收起来**，
 *    不画一条 0% 的空条。「今天已复习 21」配一条空进度条是自相矛盾的，
 *    看起来像今天什么都没干。没有分母就没有进度可画，收起来比画个假的诚实。
 */
@Composable
fun VocabReviewSection(
    state: VocabState,
    onOpenCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleared = state.reviewToNext == 0 && state.reviewTotal > 0

    Column(modifier = modifier.fillMaxWidth()) {

        // ── 第一行：累计里程碑（只涨不跌，成就感来源）──
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLabel("复习")
            ProgressBarLine(
                // 红线 2：只读 reviewIntoLevel / reviewStep
                ratio = state.reviewIntoLevel.toFloat() / state.reviewStep,
                color = RitualColors.accentReview,
                height = 11.dp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${state.reviewTotal}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = RitualColors.accentReview,
            )
        }

        Spacer(Modifier.height(3.dp))

        // 说明贴右，正好落在累计数下面，读起来是同一句话的下半句
        Text(
            text = if (cleared) {
                "第 ${state.reviewLevel} 档满了 ✓"
            } else {
                "第 ${state.reviewLevel} 档 · 还差 ${state.reviewToNext}"
            },
            style = RitualTypography.bodySmall,
            color = if (cleared) RitualColors.accentReview else RitualColors.onBgMuted,
            fontWeight = if (cleared) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        // ── 第二行：今天（可点，点开校准面板）──
        val due = state.reviewDueToday
        val todayDone = due != null && state.reviewLeftToday == 0

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenCalibrate)
                .padding(vertical = 3.dp, horizontal = 5.dp),
        ) {
            RowLabel("今天")

            if (due == null) {
                // 红线 4：没有分母就不画条子。占位交给 weight，「改」还是贴右
                Text(
                    text = "已复习 ${state.todayReview}",
                    style = RitualTypography.bodyMedium,
                    color = RitualColors.onBgMuted,
                    modifier = Modifier.weight(1f),
                )
            } else {
                ProgressBarLine(
                    ratio = if (due == 0) 1f else state.todayReview.toFloat() / due,
                    color = RitualColors.accentReview,
                    height = 8.dp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (todayDone) "${state.todayReview}/$due ✓" else "${state.todayReview}/$due",
                    style = RitualTypography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (todayDone) RitualColors.accentReview else RitualColors.onBgMuted,
                )
            }

            Spacer(Modifier.width(8.dp))
            // 「改」小方框：告诉用户这一行是能点的。全 App 不用图标，沿用文字
            Text(
                text = "改",
                fontSize = 12.sp,
                color = RitualColors.onBgMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, RitualColors.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        style = RitualTypography.bodyMedium,
        color = RitualColors.onBgMuted,
        modifier = Modifier.padding(end = 8.dp),
    )
}
