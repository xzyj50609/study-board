package com.zyj.ritual.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 背词设置「锚点」换算的关卡。
 *
 * 这一组用例对着的是 v1.0 真实翻过的车：用户在设置页只改了起算日和每天词数，
 * 保存之后整本词书从 2416 变成 1883、百分比全乱。屏幕上一个错误都没报。
 *
 * 所以这里守三件事：
 * 1. 改计划口径**不许**改动"现在总共背了多少"；
 * 2. 计划完成日钉死在起算日，不随"今天是几号"漂移；
 * 3. 算不出来的组合必须在保存之前就被拦下，并且给的是人话。
 */
class VocabSetupCalculatorTest {

    private fun rec(date: String, words: Int, kind: String = "new") =
        VocabRecord(date = date, words = words, kind = kind)

    /** 真实数据形状：7 月底零星背过，8 月 2 日起 40/天 */
    private val records = listOf(
        rec("2026-07-30", 20),
        rec("2026-07-31", 20),
        rec("2026-08-02", 40),
        rec("2026-08-03", 40),
        rec("2026-08-04", 40, kind = "backlog"),   // 复习不算新词
        rec("2026-08-05", 40),
    )

    private val config = VocabConfig(
        bookName = "2027考研真题核心词汇",
        totalWords = 2416,
        initialDone = 380,
        dailyWords = 40,
    )

    // ———————————— 1. 不许动"现在已背" ————————————

    @Test
    fun `填锚点不会改动现在总共背了多少`() {
        val before = VocabCalculator.computeState(records, config, "2026-08-13")

        // 用户填：8 月 2 日那天已经背了 420 个（= 380 起点 + 7 月底的 40）
        val after = VocabSetupCalculator.applyAnchor(
            current = config,
            records = records,
            bookName = config.bookName,
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 420,
            dailyWords = 40,
            examDate = config.examDate,
        )
        val afterState = VocabCalculator.computeState(records, after, "2026-08-13")

        assertEquals(
            "改计划口径把已背总数改掉了——这正是 v1.0 那个 bug 的样子",
            before.doneWords, afterState.doneWords,
        )
        assertEquals(2416, after.totalWords)
        // initialDone 是被反推出来的，用户从来不用知道它
        assertEquals(380, after.initialDone)
        assertEquals(420, after.planStartDone)
    }

    @Test
    fun `什么都不改直接保存 存下去的配置跟原来一模一样`() {
        // 先存一次，得到一份"已经锚过"的配置
        val anchored = VocabSetupCalculator.applyAnchor(
            current = config,
            records = records,
            bookName = config.bookName,
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 420,
            dailyWords = 40,
            examDate = config.examDate,
        )

        // 再打开设置页：表单初值从配置反推
        val anchor = VocabSetupCalculator.anchorFrom(anchored, records, "2026-08-13")
        // 什么都不改，直接按保存
        val again = VocabSetupCalculator.applyAnchor(
            current = anchored,
            records = records,
            bookName = anchored.bookName,
            totalWords = anchored.totalWords,
            planStartDate = anchor.planStartDate,
            planStartDone = anchor.planStartDone,
            dailyWords = anchor.dailyWords,
            examDate = anchored.examDate,
        )

        assertEquals("进设置页什么都没改按了保存，数字却变了", anchored, again)
    }

    @Test
    fun `没设过锚点的老配置 打开设置页时按老口径反推初值`() {
        val legacy = config.copy(planStartDone = null, rateChanges = emptyList())
        val anchor = VocabSetupCalculator.anchorFrom(legacy, records, "2026-08-13")

        // 起算日退回最早一条新词记录
        assertEquals("2026-07-30", anchor.planStartDate)
        // 那天之前没有任何记录，所以就是起点存量本身
        assertEquals(380, anchor.planStartDone)
        assertEquals(40, anchor.dailyWords)
    }

    // ———————————— 2. 计划完成日钉在起算日 ————————————

    /**
     * 用户原话：「你要算的是我那个时候需要背的是多少、已经背的是多少，
     * 如果从那个时候开始算，每天背 40 个，应该什么时候背完。」
     *
     * 所以计划完成日是一个从 8 月 2 日推出来的固定日期，
     * 不是"从今天起还要多少天"。后者每天打开都是个像模像样的数字，
     * 却会随进度悄悄漂移，几周后才发现对不上。
     */
    @Test
    fun `计划完成日从起算日推出来 不随今天是几号漂移`() {
        val anchored = VocabSetupCalculator.applyAnchor(
            current = config,
            records = records,
            bookName = config.bookName,
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 420,
            dailyWords = 40,
            examDate = config.examDate,
        )

        val d13 = VocabCalculator.computeState(records, anchored, "2026-08-13").planFinishDate
        val d20 = VocabCalculator.computeState(records, anchored, "2026-08-20").planFinishDate
        val d31 = VocabCalculator.computeState(records, anchored, "2026-08-31").planFinishDate

        assertEquals("计划完成日跟着今天在动", d13, d20)
        assertEquals("计划完成日跟着今天在动", d13, d31)

        // 8/2 起点 420，还剩 1996 个，40/天 → 50 天，含 8/2 当天 → 9 月 20 日
        assertEquals("2026-09-20", d13)
    }

    @Test
    fun `起算日之前的记录只进总进度 不再回头算成欠账`() {
        val anchored = VocabSetupCalculator.applyAnchor(
            current = config,
            records = records,
            bookName = config.bookName,
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 420,
            dailyWords = 40,
            examDate = config.examDate,
        )
        // 计划线只从 8/2 起算：8/2 当天应到 420 + 40 = 460
        val state = VocabCalculator.computeState(records, anchored, "2026-08-02")
        assertEquals(460, state.dueWords)
    }

    // ———————————— 3. 算不出来的组合要被拦住 ————————————

    @Test
    fun `那天已背比起算日之前的记录还少 要拦下来`() {
        val msg = VocabSetupCalculator.validate(
            records = records,
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 10,   // 7 月底已经记了 40 个，不可能只有 10
            dailyWords = 40,
        )
        assertNotNull("这组数会算出负的起点存量，却被放过去了", msg)
        assertTrue(msg!!, msg.contains("40"))
    }

    @Test
    fun `那天已背超过词书总量 要拦下来`() {
        val msg = VocabSetupCalculator.validate(
            records = records,
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 3000,
            dailyWords = 40,
        )
        assertNotNull(msg)
        assertTrue(msg!!, msg.contains("2416"))
    }

    @Test
    fun `加上后续记录会超过词书总量 也要拦下来`() {
        val msg = VocabSetupCalculator.validate(
            records = records,
            totalWords = 500,      // 词书填小了
            planStartDate = "2026-08-02",
            planStartDone = 420,
            dailyWords = 40,
        )
        assertNotNull(msg)
    }

    @Test
    fun `每天背 0 个要拦下来`() {
        val msg = VocabSetupCalculator.validate(
            records = records,
            totalWords = 2416,
            planStartDate = "2026-08-02",
            planStartDone = 420,
            dailyWords = 0,
        )
        assertNotNull(msg)
    }

    @Test
    fun `正常的一组数 不该报错`() {
        assertNull(
            VocabSetupCalculator.validate(
                records = records,
                totalWords = 2416,
                planStartDate = "2026-08-02",
                planStartDone = 420,
                dailyWords = 40,
            )
        )
    }

    /**
     * 用户 2026-08-14 的原话：「我不知道 8 月 2 日之前背了多少，你有当时设置的数据，
     * 和到现在为止的备份数据，难道不能反推吗？」
     *
     * 能。这里用他 8/6 那份真实备份的数字钉死这条反推：
     * initialDone 380（首次设置时人填的历史常数，唯一推不出来的一项）
     * + 8/2 之前记下的新词（7/27 的 20 + 7/28 的 20）= 420。
     * 中间那一堆 backlog 全是复习，一个都不能算进来。
     */
    @Test
    fun `按真实备份反推出 8月2日的起点是 420`() {
        val realRecords = listOf(
            rec("2026-07-27", 20),
            rec("2026-07-27", 40, kind = "backlog"),
            rec("2026-07-27", 40, kind = "backlog"),
            rec("2026-07-28", 20),
            rec("2026-07-29", 4, kind = "backlog"),
            rec("2026-07-30", 116, kind = "backlog"),
            rec("2026-07-31", 20, kind = "backlog"),
            rec("2026-08-01", 24, kind = "backlog"),
            rec("2026-08-02", 20),
            rec("2026-08-02", 20),
        )
        val realConfig = VocabConfig(totalWords = 2416, initialDone = 380, dailyWords = 20)

        val anchor = VocabSetupCalculator.anchorFrom(
            realConfig.copy(
                planStartDone = null,
                rateChanges = listOf(RateChange("2026-08-02", 40)),
            ),
            realRecords,
            "2026-08-14",
        )

        assertEquals("2026-08-02", anchor.planStartDate)
        assertEquals("反推出来的起点不是 420", 420, anchor.planStartDone)
    }

    @Test
    fun `换个起算日 反推出来的起点跟着变`() {
        val before0802 = 380 + VocabSetupCalculator.newWordsBefore(records, "2026-08-02")
        val before0805 = 380 + VocabSetupCalculator.newWordsBefore(records, "2026-08-05")

        assertEquals(420, before0802)   // 380 + 7 月底的 40
        assertEquals(500, before0805)   // 再加 8/2、8/3 的各 40
        assertTrue("起算日往后挪，起点却没跟着涨", before0805 > before0802)
    }

    @Test
    fun `起算日之前的新词累计不含复习`() {
        // 8/5 之前：7/30 的 20 + 7/31 的 20 + 8/2 的 40 + 8/3 的 40 = 120，
        // 8/4 那 40 个是 backlog，不算
        assertEquals(120, VocabSetupCalculator.newWordsBefore(records, "2026-08-05"))
    }
}
