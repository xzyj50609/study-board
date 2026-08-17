package com.zyj.ritual.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.zyj.ritual.data.repository.VocabAggregateState
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import com.zyj.ritual.ui.screens.vocab.VocabCalendarScreenContent
import com.zyj.ritual.ui.screens.vocab.VocabSetupScreenContent
import com.zyj.ritual.ui.theme.RitualTheme
import java.time.LocalDate

private val FIXED_TODAY = LocalDate.of(2026, 8, 6)
private val TODAY_STR = FIXED_TODAY.toString()

private val BASE_CONFIG = VocabConfig(
    totalWords = 1883,
    initialDone = 380,
    dailyWords = 20,
    startDate = "2026-07-27",
    examDate = "2026-12-19"
)

private fun buildVocabAggregate(
    records: List<VocabRecord>,
    config: VocabConfig = BASE_CONFIG,
): VocabAggregateState {
    val state = VocabCalculator.computeState(records, config, TODAY_STR)
    val calendar = VocabCalculator.computeCalendar(records, config, TODAY_STR)
    return VocabAggregateState(
        config = config,
        records = records,
        state = state,
        calendar = calendar,
        today = FIXED_TODAY,
    )
}

/** 造出「累计复习恰好 N 个」的一组记录，用来钉分档条的几种状态 */
private fun reviewRecords(total: Int): List<VocabRecord> =
    listOf(VocabRecord("2026-08-05", total, "backlog"))

class VocabScreenshots {

    @PreviewTest
    @Preview
    @Composable
    fun vocabCalendarNormalPace() {
        // 7/27 起匀速背到 8/6
        val records = (0..10).map { i ->
            val date = LocalDate.of(2026, 7, 27).plusDays(i.toLong()).toString()
            VocabRecord(date, 20, "new")
        }
        RitualTheme {
            VocabCalendarScreenContent(aggregate = buildVocabAggregate(records))
        }
    }

    @PreviewTest
    @Preview
    @Composable
    fun vocabCalendarWithGap() {
        // 7/27, 7/28 背了，7/29-7/31 断更缺口，8/1 起继续背
        val records = listOf(
            VocabRecord("2026-07-27", 20, "new"),
            VocabRecord("2026-07-28", 20, "new"),
            VocabRecord("2026-08-01", 20, "new"),
            VocabRecord("2026-08-02", 20, "new"),
        )
        RitualTheme {
            VocabCalendarScreenContent(aggregate = buildVocabAggregate(records))
        }
    }

    @PreviewTest
    @Preview
    @Composable
    fun vocabCalendarWithAhead() {
        // 第一天狂背 80，顺延到后续格
        val records = listOf(
            VocabRecord("2026-07-27", 80, "new"),
            VocabRecord("2026-07-28", 20, "new"),
        )
        RitualTheme {
            VocabCalendarScreenContent(aggregate = buildVocabAggregate(records))
        }
    }

    @PreviewTest
    @Preview
    @Composable
    fun vocabCalendarWithReviewDays() {
        // 7/29–8/1 只有复习，没有新词 (accentReview 闷紫呈现)
        val records = listOf(
            VocabRecord("2026-07-27", 20, "new"),
            VocabRecord("2026-07-28", 20, "new"),
            VocabRecord("2026-07-29", 40, "backlog"),
            VocabRecord("2026-07-30", 50, "backlog"),
            VocabRecord("2026-08-02", 20, "new"),
        )
        RitualTheme {
            VocabCalendarScreenContent(aggregate = buildVocabAggregate(records))
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  复习分档条的四态（2026-08-08 新增）
    //
    //  这四张守的是两件在 DOM/单测里查不出来、只有像素能看出来的事：
    //  ① 今天没校准时「今天」那条要整条消失，而累计条照旧在；
    //  ② 累计正好落在档位线上时条子要停在满格，不许掉回 0%。
    // ════════════════════════════════════════════════════════════════

    /**
     * 今天还没填「今天要复习多少」。
     *
     * 这是每天早上打开 App 的默认状态，也正是用户抱怨过的那个场景：
     * **「今天」那条要整条收起来，只留「已复习 N」和「改」，
     * 但上面的累计里程碑条必须照旧画着。**
     * 画一条 0% 的空条等于说今天什么都没干，跟「已复习 21」自相矛盾。
     */
    @PreviewTest
    @Preview
    @Composable
    fun vocabReviewNotCalibrated() {
        val records = (0..10).map { i ->
            VocabRecord(LocalDate.of(2026, 7, 27).plusDays(i.toLong()).toString(), 20, "new")
        } + reviewRecords(526)
        RitualTheme {
            // reviewDueDate 留空 = 今天没校准
            VocabCalendarScreenContent(aggregate = buildVocabAggregate(records))
        }
    }

    /** 档中间：526 / 500 一档 → 第 2 档，进了 26，还差 474 */
    @PreviewTest
    @Preview
    @Composable
    fun vocabReviewMidLevel() {
        val records = (0..10).map { i ->
            VocabRecord(LocalDate.of(2026, 7, 27).plusDays(i.toLong()).toString(), 20, "new")
        } + reviewRecords(526) + VocabRecord(TODAY_STR, 38, "backlog")
        RitualTheme {
            VocabCalendarScreenContent(
                aggregate = buildVocabAggregate(
                    records,
                    // 今天校准过：45 个到期，已做 38
                    BASE_CONFIG.copy(reviewDue = 45, reviewDueDate = TODAY_STR),
                )
            )
        }
    }

    /**
     * 正好满一档（累计 1000，档长 500）。
     *
     * ⚠️ 这张图必须是**满格 + 「第 2 档满了 ✓」**。
     * 要是哪天有人在 UI 里改成自己算 `reviewTotal % reviewStep`，
     * 这里会变成一条空条——十几天才撞一次的 bug，只有这张基准图逮得住。
     */
    @PreviewTest
    @Preview
    @Composable
    fun vocabReviewLevelCleared() {
        val records = (0..10).map { i ->
            VocabRecord(LocalDate.of(2026, 7, 27).plusDays(i.toLong()).toString(), 20, "new")
        } + reviewRecords(1000)
        RitualTheme {
            VocabCalendarScreenContent(
                aggregate = buildVocabAggregate(
                    records,
                    BASE_CONFIG.copy(reviewDue = 45, reviewDueDate = TODAY_STR),
                )
            )
        }
    }

    /** 落后报警：只背了两天就停了，计划/预计那行要变红字 */
    @PreviewTest
    @Preview
    @Composable
    fun vocabHeaderAlarm() {
        val records = listOf(
            VocabRecord("2026-07-27", 20, "new"),
            VocabRecord("2026-07-28", 20, "new"),
        )
        RitualTheme {
            VocabCalendarScreenContent(aggregate = buildVocabAggregate(records))
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  顶部留白（2026-08-17 新增）
    //
    //  用户反馈「从状态栏到『背词设置』这行蓝字之间空太多」。根因是这一页
    //  自己套了第二层 Scaffold，把状态栏高度让了两遍（MainActivity 已经让过一次）。
    //  这张图钉的就是「蓝字必须贴近屏幕顶端」——嵌套 Scaffold 一旦被人加回来，
    //  这张基准图会立刻变化。
    // ════════════════════════════════════════════════════════════════

    @PreviewTest
    @Preview(name = "背词看板-顶部不留白", widthDp = 390, heightDp = 844, showBackground = true)
    @Composable
    fun vocabBoardTopGap() {
        RitualTheme {
            VocabCalendarScreenContent(
                aggregate = buildVocabAggregate(SETUP_RECORDS, SETUP_CONFIG),
            )
        }
    }

    // ════════════════════════════════════════════════════════════
    //  设置页
    //
    //  上一版这页是全 App 唯一用 Material 原生 OutlinedTextField 的地方，
    //  在一片米白纸感里冒出一圈紫色描边，用户的原话是"跟整个 APP 的风格根本不搭"。
    //  界面好不好看不能靠用户装机肉眼兜底，所以这一页也进截图关卡。
    // ════════════════════════════════════════════════════════════

    @PreviewTest
    @Preview(name = "背词设置-正常", widthDp = 390, heightDp = 1400, showBackground = true)
    @Composable
    fun vocabSetupNormal() {
        RitualTheme {
            VocabSetupScreenContent(
                aggregate = buildVocabAggregate(SETUP_RECORDS, SETUP_CONFIG),
                isSaving = false,
                onSave = {},
                onBack = {},
            )
        }
    }

    /** 填出一组算不出来的数时，错误必须写在屏幕上、保存按钮要拦住。 */
    @PreviewTest
    @Preview(name = "背词设置-数字矛盾", widthDp = 390, heightDp = 1400, showBackground = true)
    @Composable
    fun vocabSetupInvalid() {
        RitualTheme {
            VocabSetupScreenContent(
                // 词书总量只有 100，但记录加起来早就超了
                aggregate = buildVocabAggregate(SETUP_RECORDS, SETUP_CONFIG.copy(totalWords = 100)),
                isSaving = false,
                onSave = {},
                onBack = {},
            )
        }
    }
}

/** 用户真实数据的形状：2416 词的词书，8/2 起 40/天 */
private val SETUP_CONFIG = VocabConfig(
    totalWords = 2416,
    initialDone = 380,
    dailyWords = 40,
    planStartDone = 420,
    rateChanges = listOf(com.zyj.ritual.domain.vocab.RateChange("2026-08-02", 40)),
    examDate = "2026-12-19",
)

private val SETUP_RECORDS = listOf(
    VocabRecord("2026-07-30", 20, "new"),
    VocabRecord("2026-07-31", 20, "new"),
    VocabRecord("2026-08-02", 40, "new"),
    VocabRecord("2026-08-03", 40, "new"),
    VocabRecord("2026-08-04", 40, "new"),
    VocabRecord("2026-08-05", 40, "new"),
    VocabRecord("2026-08-06", 40, "new"),
)
