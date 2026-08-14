package com.zyj.ritual.ui.screens.vocab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.data.repository.VocabRepository
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabSetupCalculator
import com.zyj.ritual.ui.components.DateFieldRow
import com.zyj.ritual.ui.components.NumberStepperField
import com.zyj.ritual.ui.components.PresetChipsRow
import com.zyj.ritual.ui.components.PrimaryPill
import com.zyj.ritual.ui.components.RitualDatePickerDialog
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualFontFamilies
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypography
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class PickerTarget { PLAN_START, EXAM }

/**
 * 背单词设置页。
 *
 * 这一版换了提问方式。上一版问的是 App 内部想知道的东西
 * （「起点存量（以前已背）」是多少），用户答不上来，只能瞎填；
 * 这一版问的是用户本来就知道的东西：
 * **哪天开始按新节奏背、那天已经背了多少、从那天起每天多少。**
 * 内部那两个量（initialDone / planStartDone）由 [VocabSetupCalculator] 反推。
 *
 * 还有两条硬规矩，都是上一版栽过的跟头：
 * 1. 配置没从磁盘读上来之前，这个页面**根本不会被创建**（见 RitualNavGraph）。
 *    上一版用一份写死的默认配置当初值，用户一进来看到的就是 1883/380/20，
 *    只改了日期就保存 → 真实的 2416 被悄悄写回 1883。屏幕上什么都不会报错。
 * 2. 保存前必须把「哪几个数会从 A 变成 B」念一遍给用户看，让他自己否决。
 */
@Composable
fun VocabSetupScreen(
    aggregate: VocabAggregateState,
    vocabRepository: VocabRepository,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    VocabSetupScreenContent(
        aggregate = aggregate,
        isSaving = isSaving,
        onBack = onBack,
        onSave = { draft ->
            isSaving = true
            scope.launch {
                runCatching {
                    vocabRepository.saveSetup(
                        bookName = draft.bookName,
                        totalWords = draft.totalWords,
                        planStartDate = draft.planStartDate,
                        planStartDone = draft.planStartDone,
                        dailyWords = draft.dailyWords,
                        examDate = draft.examDate,
                    )
                }
                isSaving = false
                onSaved()
            }
        },
    )
}

/** 用户在设置页填的那一组数。 */
data class VocabSetupDraft(
    val bookName: String,
    val totalWords: Int,
    val planStartDate: String,
    val planStartDone: Int,
    val dailyWords: Int,
    val examDate: String,
)

/** 纯 UI，不碰存储——这样才截得了图。 */
@Composable
fun VocabSetupScreenContent(
    aggregate: VocabAggregateState,
    isSaving: Boolean,
    onSave: (VocabSetupDraft) -> Unit,
    onBack: () -> Unit,
) {
    val currentConfig = aggregate.config
    val records = aggregate.records
    val todayStr = aggregate.today.toString()

    val anchor = remember(currentConfig, records) {
        VocabSetupCalculator.anchorFrom(currentConfig, records, todayStr)
    }

    var bookName by remember { mutableStateOf(currentConfig.bookName) }
    var totalWords by remember { mutableStateOf(currentConfig.totalWords) }
    var planStartDate by remember { mutableStateOf(LocalDate.parse(anchor.planStartDate)) }
    var planStartDone by remember { mutableStateOf(anchor.planStartDone) }
    var dailyWords by remember { mutableStateOf(anchor.dailyWords) }
    var examDate by remember { mutableStateOf(parseDateOr(currentConfig.examDate, aggregate.today)) }

    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    val error = VocabSetupCalculator.validate(
        records = records,
        totalWords = totalWords,
        planStartDate = planStartDate.toString(),
        planStartDone = planStartDone,
        dailyWords = dailyWords,
    )

    // 预览用的候选配置：跟保存写下去的是同一个函数算出来的，
    // 不是另写一份"差不多"的展示逻辑——两份迟早会在边界上对不上。
    val candidate: VocabConfig? = if (error == null) {
        runCatching {
            VocabSetupCalculator.applyAnchor(
                current = currentConfig,
                records = records,
                bookName = bookName,
                totalWords = totalWords,
                planStartDate = planStartDate.toString(),
                planStartDone = planStartDone,
                dailyWords = dailyWords,
                examDate = examDate.toString(),
            )
        }.getOrNull()
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RitualSpace.screenPadding),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))
        Spacer(Modifier.height(RitualSpace.sectionGap))

        Text("背单词设置", style = RitualTypography.headlineLarge)
        Text(
            "只填你本来就知道的三个数：哪天开始按新节奏背、那天已经背了多少、从那天起每天多少。" +
                "剩下的换算 App 自己做。",
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(RitualSpace.sectionGap))

        DateFieldRow(
            label = "从哪天起算",
            date = planStartDate,
            onClick = { picker = PickerTarget.PLAN_START },
            hint = "这天之前背的照样算进总进度，只是不再回头算成欠账。",
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        NumberStepperField(
            label = "那天为止总共背了",
            value = planStartDone,
            onValueChange = { planStartDone = it },
            step = 10,
            max = 99_999,
            unit = "个",
            hint = "照那个背单词 APP 首页的累计数填就行。",
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        NumberStepperField(
            label = "从那天起每天背",
            value = dailyWords,
            onValueChange = { dailyWords = it },
            step = 5,
            min = 1,
            max = 999,
            unit = "个 / 天",
        )
        Spacer(Modifier.height(RitualSpace.listGap))
        PresetChipsRow(
            presets = listOf(20, 40, 60, 80),
            selected = dailyWords,
            onSelect = { dailyWords = it },
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        NumberStepperField(
            label = "整本词书一共",
            value = totalWords,
            onValueChange = { totalWords = it },
            step = 1,
            min = 1,
            max = 99_999,
            unit = "个",
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        DateFieldRow(
            label = "考试日期",
            date = examDate,
            onClick = { picker = PickerTarget.EXAM },
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        BookNameField(value = bookName, onValueChange = { bookName = it })

        Spacer(Modifier.height(RitualSpace.sectionGap))

        if (error != null) {
            ErrorCard(error)
        } else if (candidate != null) {
            PlanPreviewCard(
                candidate = candidate,
                records = records,
                todayStr = todayStr,
            )
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))

        PrimaryPill(
            text = when {
                isSaving -> "保存中…"
                error != null -> "先把上面那条改对"
                else -> "保存背词设置"
            },
            enabled = !isSaving && error == null,
            onClick = { showConfirm = true },
        )

        Spacer(Modifier.height(RitualSpace.listGap))

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("不改了，返回", style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted))
        }

        Spacer(Modifier.height(RitualSpace.sectionGap))
    }

    when (picker) {
        PickerTarget.PLAN_START -> RitualDatePickerDialog(
            initialDate = planStartDate,
            title = "从哪天起按新节奏算",
            onConfirm = { planStartDate = it; picker = null },
            onDismiss = { picker = null },
        )
        PickerTarget.EXAM -> RitualDatePickerDialog(
            initialDate = examDate,
            title = "考试哪天",
            onConfirm = { examDate = it; picker = null },
            onDismiss = { picker = null },
        )
        null -> Unit
    }

    if (showConfirm && candidate != null) {
        ChangeConfirmDialog(
            before = currentConfig,
            after = candidate,
            onConfirm = {
                showConfirm = false
                onSave(
                    VocabSetupDraft(
                        bookName = bookName,
                        totalWords = totalWords,
                        planStartDate = planStartDate.toString(),
                        planStartDone = planStartDone,
                        dailyWords = dailyWords,
                        examDate = examDate.toString(),
                    )
                )
            },
            onDismiss = { showConfirm = false },
        )
    }
}

// ———————————— 子部件 ————————————

@Composable
private fun BookNameField(value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .padding(RitualSpace.cardPadding),
    ) {
        Text("词书名称", style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted))
        Spacer(Modifier.height(RitualSpace.listGap))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = RitualTypography.bodyLarge.copy(color = RitualColors.onBg),
            cursorBrush = SolidColor(RitualColors.accentInk),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.warn.copy(alpha = 0.12f))
            .border(1.dp, RitualColors.warn.copy(alpha = 0.4f), RoundedCornerShape(RitualRadius.cardLg))
            .padding(RitualSpace.cardPadding),
    ) {
        Text(
            "这组数字算不出来",
            style = RitualTypography.titleMedium.copy(
                color = RitualColors.warnText,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            message,
            style = RitualTypography.bodySmall.copy(color = RitualColors.warnText),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * 实时预览：填的这组数会算出什么。
 *
 * 光显示"已保存"是不够的——用户没法判断存进去的口径对不对。
 * 这里当场把最要紧的四个结果念出来，对不上他自己就能发现。
 */
@Composable
private fun PlanPreviewCard(
    candidate: VocabConfig,
    records: List<com.zyj.ritual.domain.vocab.VocabRecord>,
    todayStr: String,
) {
    val state = remember(candidate, records, todayStr) {
        VocabCalculator.computeState(records, candidate, todayStr)
    }
    val percent = VocabCalculator.donePercent(state.doneWords, candidate.totalWords, state.finished)
    val paceText = when {
        state.aheadDays > 0 -> "比计划快 ${state.aheadDays} 天"
        state.aheadDays < 0 -> "比计划慢 ${-state.aheadDays} 天"
        else -> "刚好跟上计划"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitualRadius.cardLg))
            .background(RitualColors.surfaceLow)
            .border(
                1.dp,
                RitualColors.accentGold.copy(alpha = 0.3f),
                RoundedCornerShape(RitualRadius.cardLg),
            )
            .padding(RitualSpace.cardPadding),
    ) {
        Text(
            "按这组数字算下来",
            style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
        )
        Spacer(Modifier.height(RitualSpace.listGap))

        PreviewRow("计划背完那天", VocabCalculator.formatCn(state.planFinishDate), highlight = true)
        PreviewRow("照现在的速度", "${VocabCalculator.formatCn(state.projFinishDate)}背完 · $paceText")
        PreviewRow("现在总共背了", "${state.doneWords} 个 · $percent%")
        PreviewRow("今天按计划该到", "${state.dueWords} 个")
        PreviewRow("距离考试", "${state.daysToExam} 天")
    }
}

@Composable
private fun PreviewRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            label,
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = RitualTypography.bodySmall.copy(
                color = if (highlight) RitualColors.accentGold else RitualColors.onBg,
                fontFamily = RitualFontFamilies.num,
                fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

/**
 * 保存前的变更确认。
 *
 * 这一版栽的跟头就出在"保存"这两个字太轻了：
 * 用户以为自己只改了日期，实际上词书总量被从 2416 悄悄写回 1883。
 * 所以现在保存必须先把差异摆出来，一条都不藏。
 */
@Composable
private fun ChangeConfirmDialog(
    before: VocabConfig,
    after: VocabConfig,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val diffs = buildList {
        if (before.bookName != after.bookName) add("词书名称：${before.bookName} → ${after.bookName}")
        if (before.totalWords != after.totalWords) add("整本词书：${before.totalWords} → ${after.totalWords} 个")
        if (before.examDate != after.examDate) add("考试日期：${before.examDate} → ${after.examDate}")
        if (before.dailyWords != after.dailyWords) add("每天背：${before.dailyWords} → ${after.dailyWords} 个")
        val beforeStart = before.rateChanges.firstOrNull()?.from
        val afterStart = after.rateChanges.firstOrNull()?.from
        if (beforeStart != afterStart) add("起算日：${beforeStart ?: "没设过"} → ${afterStart ?: "没设过"}")
        if (before.planStartDone != after.planStartDone) {
            add("起算那天已背：${before.planStartDone ?: "没设过"} → ${after.planStartDone} 个")
        }
    }

    AlertDialog(
        containerColor = RitualColors.surface,
        titleContentColor = RitualColors.onBg,
        textContentColor = RitualColors.onBgMuted,
        onDismissRequest = onDismiss,
        title = { Text("确认这么改", style = RitualTypography.titleMedium) },
        text = {
            Column {
                if (diffs.isEmpty()) {
                    Text("跟现在存的一模一样，保存不会改变任何数字。", style = RitualTypography.bodyMedium)
                } else {
                    diffs.forEach { line ->
                        Text(
                            "· $line",
                            style = RitualTypography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(RitualSpace.listGap))
                    Text(
                        "已经记下的每日背词记录一条都不会动。",
                        style = RitualTypography.bodySmall.copy(color = RitualColors.onBgMuted),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "存",
                    style = RitualTypography.bodyMedium.copy(
                        color = RitualColors.accentInk,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("再看看", style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted))
            }
        },
    )
}

private fun parseDateOr(raw: String, fallback: LocalDate): LocalDate =
    runCatching { LocalDate.parse(raw) }.getOrDefault(fallback)
