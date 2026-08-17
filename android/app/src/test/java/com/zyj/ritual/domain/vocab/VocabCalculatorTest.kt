package com.zyj.ritual.domain.vocab

import org.junit.Assert.*
import org.junit.Test

/**
 * model.js v2 单元测试 —— 从 model.test.js 628 行逐条翻译。
 *
 * 关键手法：todayStr 是注入的，不取系统时间——否则测试结果会随运行日期漂移。
 * 所有"第 N 天"都用固定的绝对日期表示。
 */
class VocabCalculatorTest {

    // 测试统一用一套小参数，好人肉验算：总 200 词，起点已背 40，每天 40，积压 80
    private val S = VocabConfig(totalWords = 200, initialDone = 40, dailyWords = 40, backlogTotal = 80, alarmLateDays = 3)
    private val D = "2026-07-28" // 起始日

    private fun rec(date: String, words: Int, kind: String = "new") =
        VocabRecord(date, words, kind)

    private fun daysCells(records: List<VocabRecord>, settings: VocabConfig, today: String) =
        VocabCalculator.computeCalendar(records, settings, today)
            .cells.filterIsInstance<VocabCalendarCell.Day>()

    // ---- 1. 还没开始 ----
    @Test
    fun `01 not started`() {
        val st = VocabCalculator.computeState(emptyList(), S, D)
        assertTrue("未开始 notStarted", st.notStarted)
        assertEquals("未开始 doneWords = 起点存量", 40, st.doneWords)
        assertFalse("未开始不报警", st.isAlarm)
        // (200-40)/40 = 4 天 → 7/28 起第 4 天 = 7/31
        assertEquals("未开始 planFinishDate", "2026-07-31", st.planFinishDate)
        assertEquals("未开始 projFinishDate = 计划", "2026-07-31", st.projFinishDate)
        assertEquals("未开始 aheadDays = 0", 0, st.aheadDays)
    }

    // ---- 2. 匀速：每天 40，第 3 天 ----
    @Test
    fun `02 steady pace day 3`() {
        val r = listOf(rec("2026-07-28", 40), rec("2026-07-29", 40), rec("2026-07-30", 40))
        val st = VocabCalculator.computeState(r, S, "2026-07-30")
        assertEquals("匀速 doneWords", 160, st.doneWords)
        assertEquals("匀速 dueWords", 160, st.dueWords)
        assertEquals("匀速 aheadDays = 0", 0, st.aheadDays)
        assertFalse("匀速不报警", st.isAlarm)
        assertEquals("匀速 projFinishDate = planFinishDate", st.planFinishDate, st.projFinishDate)
    }

    // ---- 3. 超额：第一天背 80 ----
    @Test
    fun `03 excess on day 1`() {
        val r = listOf(rec(D, 80))
        val st = VocabCalculator.computeState(r, S, D)
        assertEquals("超额 aheadDays = +1", 1, st.aheadDays)
        assertEquals("超额 projFinishDate 提前一天", "2026-07-30", st.projFinishDate)
        assertEquals("planFinishDate 固定不动", "2026-07-31", st.planFinishDate)
    }

    // ---- 4. 半格：背 20 个，今天还没背完不算晚 ----
    @Test
    fun `04 half done today is not late`() {
        val r = listOf(rec(D, 20))
        val st = VocabCalculator.computeState(r, S, D)
        assertEquals("今天背了一半 aheadDays = 0", 0, st.aheadDays)
        assertEquals("todayWords = 20", 20, st.todayWords)
        val cal = VocabCalculator.computeCalendar(r, S, D)
        val day1 = cal.cells.filterIsInstance<VocabCalendarCell.Day>().first { it.date == D }
        assertEquals("日历第一格填充 0.5", 0.5, day1.fill, 0.001)
        assertFalse("今天没填满不算缺口", day1.gap)
    }

    // ---- 5. 落后触发报警：错过的整天 ≥ 3 才报 ----
    @Test
    fun `05 alarm threshold`() {
        val r = listOf(rec(D, 40))
        var st = VocabCalculator.computeState(r, S, "2026-07-31")
        assertEquals("错过两整天 → 晚 2 天", -2, st.aheadDays)
        assertFalse("晚 2 天不报警（阈值 3）", st.isAlarm)

        st = VocabCalculator.computeState(r, S, "2026-08-01")
        assertEquals("错过三整天 → 晚 3 天", -3, st.aheadDays)
        assertTrue("晚 3 天报警", st.isAlarm)

        val days = daysCells(r, S, "2026-08-01")
        assertTrue("7/29 缺口", days[1].gap)
        assertFalse("8/1（今天）非缺口", days[4].gap)
        assertTrue("8/1 是今天", days[4].isToday)
    }

    // ---- 6. 报警只是视觉：数据不回退 ----
    @Test
    fun `06 alarm is visual only`() {
        val r = listOf(rec(D, 40))
        val st = VocabCalculator.computeState(r, S, "2026-08-01")
        assertEquals("报警态 doneWords 不回退", 80, st.doneWords)
    }

    // ---- 7. 补上进度后报警解除，冲线日按真实日期记 ----
    @Test
    fun `07 catch up clears alarm`() {
        val r = listOf(rec(D, 40), rec("2026-08-01", 160))
        val st = VocabCalculator.computeState(r, S, "2026-08-01")
        assertTrue("补齐后 finished", st.finished)
        assertFalse("finished 后不报警", st.isAlarm)
        assertEquals("冲线日 = 8/1", "2026-08-01", st.projFinishDate)
        assertEquals("比计划晚 1 天完成", -1, st.aheadDays)
    }

    // ---- 8. 同日多次点击 = 多条独立记录，不去重 ----
    @Test
    fun `08 same day multiple records`() {
        val r = listOf(rec(D, 20), rec(D, 20))
        val st = VocabCalculator.computeState(r, S, D)
        assertEquals("同日两条 20 累加", 80, st.doneWords)
        assertEquals("todayWords 只算今天新词", 40, st.todayWords)
    }

    // ---- 9. 积压：清一点少一点，不影响新词进度 ----
    @Test
    fun `09 backlog isolation`() {
        val r = listOf(rec(D, 40), rec(D, 40, "backlog"))
        val st = VocabCalculator.computeState(r, S, D)
        assertEquals("积压剩余 80-40", 40, st.backlogLeft)
        assertEquals("积压不计入 doneWords", 80, st.doneWords)
        assertEquals("积压不影响 aheadDays", 0, st.aheadDays)

        val r2 = listOf(rec(D, 40), rec(D, 200, "backlog"))
        assertEquals("积压超清夹到 0", 0, VocabCalculator.computeState(r2, S, D).backlogLeft)
    }

    // ---- 10. 纯积压记录不算「已开始」----
    @Test
    fun `10 backlog only is not started`() {
        val r = listOf(rec(D, 40, "backlog"))
        val st = VocabCalculator.computeState(r, S, D)
        assertTrue("只清积压仍是 notStarted", st.notStarted)
    }

    // ---- 11. 幂等 ----
    @Test
    fun `11 idempotent`() {
        val r = listOf(rec(D, 40), rec("2026-07-29", 20), rec("2026-07-30", 40))
        val a = VocabCalculator.computeState(r, S, "2026-07-30")
        val b = VocabCalculator.computeState(r, S, "2026-07-30")
        assertEquals("幂等", a, b)
        val without = listOf(r[0], r[2])
        val c = VocabCalculator.computeState(without, S, "2026-07-30")
        assertEquals("删除中间一条 = 独立重算", 40 + 40 + 40, c.doneWords)
    }

    // ---- 12. 日历结构：月份标签 + 周一对齐 + 旗标 ----
    @Test
    fun `12 calendar structure`() {
        val cal = VocabCalculator.computeCalendar(emptyList(), S, D)
        val cells = cal.cells
        assertTrue("第一格是月份标签", cells[0] is VocabCalendarCell.Month)
        assertEquals("7月", (cells[0] as VocabCalendarCell.Month).label)
        assertTrue("7/28 周二 → 1 个前导 pad", cells[1] is VocabCalendarCell.Pad)
        val day1 = cells[2] as VocabCalendarCell.Day
        assertEquals("随后是 7/28 格", D, day1.date)
        assertTrue("7/28 是今天", day1.isToday)
        val flag = cal.cells.filterIsInstance<VocabCalendarCell.Day>().firstOrNull { it.isPlanFinish }
        assertNotNull("有计划完成旗", flag)
        assertEquals("计划完成旗在 7/31", "2026-07-31", flag!!.date)
        val aug = cal.cells.filterIsInstance<VocabCalendarCell.Month>().firstOrNull { it.label == "8月" }
        assertNull("没有 8 月标签", aug)
    }

    // ---- 13. 日历连续填充 ----
    @Test
    fun `13 calendar continuous fill`() {
        val r = listOf(rec(D, 120))
        val days = daysCells(r, S, "2026-07-29")
        assertEquals("第 1 格满", 1.0, days[0].fill, 0.001)
        assertEquals("第 2 格满", 1.0, days[1].fill, 0.001)
        assertEquals("第 3 格满", 1.0, days[2].fill, 0.001)
        assertEquals("第 4 格空", 0.0, days[3].fill, 0.001)
        assertFalse("第 2 格已被超额填满 → 无 gap", days[1].gap)
    }

    // ---- 13b. 双色分界 ----
    @Test
    fun `13b dual color boundary`() {
        val r = listOf(rec(D, 120))
        val days = daysCells(r, S, D)
        assertEquals("今天这格满了", 1.0, days[0].fill, 0.001)
        assertFalse("今天这格满了也算计划内，不是多做", days[0].extra)
        assertTrue("明天那格被填满 → 多做的", days[1].extra)
        assertTrue("后天那格被填满 → 多做的", days[2].extra)
        assertFalse("没填的空格不算多做", days[3].extra)
        assertEquals("空格 fill=0", 0.0, days[3].fill, 0.001)

        // 匀速推进时一个多做的格子都不该有
        val flat = daysCells(listOf(rec(D, 40)), S, D)
        assertTrue("匀速无多做格", flat.none { it.extra })

        // 落后时更不该有
        val late = daysCells(listOf(rec(D, 40)), S, "2026-08-01")
        assertTrue("落后时无多做格", late.none { it.extra })
    }

    // ---- 13c. 显式开始日期 ----
    @Test
    fun `13c explicit start date`() {
        val S2 = S.copy(startDate = "2026-07-27")
        val st = VocabCalculator.computeState(emptyList(), S2, D)
        assertEquals("显式开始日生效", "2026-07-27", st.startDate)
        assertEquals("7/27 起 4 天 → 计划完成 7/30", "2026-07-30", st.planFinishDate)
        val days = daysCells(emptyList(), S2, D)
        assertEquals("日历第一格 = 7/27", "2026-07-27", days[0].date)

        // 记录比设置更早时以记录为准
        val earlier = listOf(rec("2026-07-20", 40))
        assertEquals("记录早于设置 → 用记录",
            "2026-07-20", VocabCalculator.computeState(earlier, S2, D).startDate)

        // 开始日设在未来
        val future = S.copy(startDate = "2026-08-10")
        val fs = VocabCalculator.computeState(emptyList(), future, D)
        assertEquals("未来开始日 elapsedDays = 0", 0, fs.elapsedDays)
        assertTrue("未来开始日 dueWords 不低于起点", fs.dueWords >= 40)

        // 不设时行为不变
        assertEquals("未设 startDate → 沿用老推导", D,
            VocabCalculator.computeState(emptyList(), S, D).startDate)
    }

    // ---- 14. 背完：finished 后预计旗消失 ----
    @Test
    fun `14 finished hides proj flag`() {
        val r = listOf(rec(D, 160))
        val st = VocabCalculator.computeState(r, S, D)
        assertTrue("160+40=200 → finished", st.finished)
        val cal = VocabCalculator.computeCalendar(r, S, D)
        assertTrue("finished 后无预计旗",
            cal.cells.filterIsInstance<VocabCalendarCell.Day>().none { it.isProjFinish })
    }

    // ---- 15. 背完之后日子流逝 ----
    @Test
    fun `15 finished stays finished over time`() {
        val st = VocabCalculator.computeState(listOf(rec(D, 160)), S, "2026-09-01")
        assertEquals("dueWords 夹到 totalWords", 200, st.dueWords)
        assertEquals("一个月后看，冲线日仍是 7/28", D, st.projFinishDate)
        assertEquals("提前 3 天完成，永久成立", 3, st.aheadDays)
        assertFalse("背完后不再报警", st.isAlarm)
    }

    // ---- 16. v1 迁移 ----
    @Test
    fun `16 v1 migration`() {
        val oldSettings = mapOf<String, Any?>(
            "groupNew" to 20, "groupReview" to 40,
            "bookName" to "旧词书", "totalWords" to 999, "examDate" to "2026-12-19",
            "debtFloor" to -3
        )
        val oldRecords = listOf<Map<String, Any?>>(
            mapOf("date" to D, "groups" to 1),
            mapOf("date" to D, "groups" to 0.5),
            mapOf("date" to "bad"),  // 结构不对 → 滤掉
        )
        val (config, records) = VocabCalculator.migrateV1(oldRecords, oldSettings)
        assertEquals("groups 1 → 20 词", 20, records[0].words)
        assertEquals("groups 0.5 → 10 词", 10, records[1].words)
        assertEquals("坏记录被滤掉", 2, records.size)
        assertEquals("保留 bookName", "旧词书", config.bookName)
    }

    // ---- 17. 真实参数抽查 ----
    @Test
    fun `17 real params spot check`() {
        val real = VocabConfig(totalWords = 1883, initialDone = 380, dailyWords = 20, startDate = "2026-07-27")
        val r27 = listOf(VocabRecord("2026-07-27", 20))
        val st = VocabCalculator.computeState(r27, real, "2026-07-28")

        assertEquals("起算日 = 7/27", "2026-07-27", st.startDate)
        assertEquals("真实参数计划完成日 = 10/10", "2026-10-10", st.planFinishDate)
        assertEquals("formatCn", "10月10日", VocabCalculator.formatCn(st.planFinishDate))
        assertEquals("7/28 已背 = 400", 400, st.doneWords)
        assertEquals("7/28 未学 = 1483", 1483, st.remainWords)
        assertEquals("积压仍为 288", 288, st.backlogLeft)

        val cal = VocabCalculator.computeCalendar(r27, real, "2026-07-28")
        val d27 = cal.cells.filterIsInstance<VocabCalendarCell.Day>().first { it.date == "2026-07-27" }
        assertEquals("7/27 格填满", 1.0, d27.fill, 0.001)
        assertFalse("7/27 不是缺口", d27.gap)
    }

    // ---- 18. 积压可以挂在任意过去的日子上 ----
    @Test
    fun `18 backlog on past dates`() {
        val real = VocabConfig(totalWords = 1883, initialDone = 380, dailyWords = 20,
            startDate = "2026-07-27", backlogTotal = 288)
        val base = listOf(VocabRecord("2026-07-27", 20))
        val withBacklog = base + listOf(
            VocabRecord("2026-07-27", 40, "backlog"),
            VocabRecord("2026-07-28", 40, "backlog"),
        )
        val a = VocabCalculator.computeState(base, real, "2026-07-28")
        val b = VocabCalculator.computeState(withBacklog, real, "2026-07-28")

        assertEquals("往期积压照样扣", 208, b.backlogLeft)
        assertEquals("积压不动 doneWords", a.doneWords, b.doneWords)
        assertEquals("积压不动 aheadDays", a.aheadDays, b.aheadDays)
        assertEquals("积压不动 planFinishDate", a.planFinishDate, b.planFinishDate)
        assertEquals("积压不动 projFinishDate", a.projFinishDate, b.projFinishDate)

        val cal = VocabCalculator.computeCalendar(withBacklog, real, "2026-07-28")
        val d27 = cal.cells.filterIsInstance<VocabCalendarCell.Day>().first { it.date == "2026-07-27" }
        assertEquals("积压不进日历填充", 1.0, d27.fill, 0.001)

        // 顺序无关
        val shuffled = listOf(withBacklog[2], withBacklog[0], withBacklog[1])
        assertEquals("积压记录顺序无关",
            VocabCalculator.computeState(shuffled, real, "2026-07-28"),
            b)
    }

    // ---- 18b. UTC+8 时区（算法层不直接测，BeijingClock 已有覆盖）----
    // JS 的 todayStr() 在 Kotlin 里由 BeijingClock 负责，这里只测 toEpochDay/fromEpochDay 的往返
    @Test
    fun `18b epoch day round trip`() {
        assertEquals("toEpochDay 往返", "2026-07-30",
            VocabCalculator.fromEpochDay(VocabCalculator.toEpochDay("2026-07-30")))
        assertEquals("daysBetweenInclusive 同一天=1", 1,
            VocabCalculator.daysBetweenInclusive("2026-07-30", "2026-07-30"))
    }

    // ---- 19. 先填自己那一格，溢出往后顺延 ----
    @Test
    fun `19 two pass allocation`() {
        val r = listOf(rec(D, 40), rec("2026-07-30", 80))
        val days = daysCells(r, S, "2026-07-30")

        assertEquals("7/28 自己记满", 40, days[0].ownWords)
        assertFalse("7/28 不是别人顺延来的", days[0].extra)
        assertEquals("7/29 断了 → 空格", 0.0, days[1].fill, 0.001)
        assertTrue("7/29 是红边缺口", days[1].gap)
        assertEquals("7/30 自己那格填满", 40, days[2].ownWords)
        assertFalse("7/30 自己填的不算超额", days[2].extra)
        assertEquals("多出来的 40 顺延到 7/31", 40, days[3].aheadWords)
        assertEquals("7/31 自己一个词没记", 0, days[3].ownWords)
        assertTrue("7/31 是绿的（提前做掉的）", days[3].extra)
        assertEquals("7/31 填满", 1.0, days[3].fill, 0.001)
    }

    // ---- 19b. 缺口只能就地补 ----
    @Test
    fun `19b gap only fixed by that day`() {
        val missed = listOf(rec(D, 40), rec("2026-07-30", 80))
        val fixed = missed + listOf(rec("2026-07-29", 40))
        val days = daysCells(fixed, S, "2026-07-30")
        assertFalse("补记到那一天 → 红边消失", days[1].gap)
        assertEquals("补记后 ownWords", 40, days[1].ownWords)
        assertEquals("7/31 仍是顺延来的", 40, days[3].aheadWords)
    }

    // ---- 19c. 一格两段 ----
    @Test
    fun `19c one cell two segments`() {
        val r = listOf(rec(D, 60), rec("2026-07-29", 20))
        val days = daysCells(r, S, "2026-07-29")
        assertEquals("7/29 自己记的那一段", 20, days[1].ownWords)
        assertEquals("7/29 顺延来的那一段", 20, days[1].aheadWords)
        assertEquals("两段加起来填满", 1.0, days[1].fill, 0.001)
        assertEquals("金色段占一半", 0.5, days[1].own, 0.001)
        assertFalse("填满了就不是缺口", days[1].gap)
    }

    // ---- 19d. 提前做掉的量落在今天这格也算「提前做的」----
    @Test
    fun `19d ahead on today cell`() {
        val r = listOf(rec(D, 80))
        val days = daysCells(r, S, "2026-07-29")
        assertTrue("今天这格是昨天顶满的 → extra", days[1].isToday && days[1].extra)
        assertEquals("今天自己确实一个没记", 0, days[1].ownWords)
        assertEquals("今天这格的量来自昨天", 40, days[1].aheadWords)
        assertEquals("todayWords 也说 0", 0,
            VocabCalculator.computeState(r, S, "2026-07-29").todayWords)
    }

    // ---- 19e. 不变量：填充总面积 = 新词总量 ----
    @Test
    fun `19e fill area invariant`() {
        val sets = listOf(
            listOf(rec(D, 40), rec("2026-07-30", 80)),
            listOf(rec(D, 60), rec("2026-07-29", 20)),
            listOf(rec(D, 20), rec("2026-07-29", 20), rec("2026-07-30", 20)),
        )
        for ((i, r) in sets.withIndex()) {
            val days = daysCells(r, S, "2026-07-30")
            val drawn = days.sumOf { it.ownWords + it.aheadWords }
            assertEquals("第 ${i + 1} 组：画出来的词数 = 记录总量",
                VocabCalculator.sumNewWords(r), drawn)
            // 打乱顺序必须得到完全一样的格子
            val shuffled = r.reversed()
            assertEquals("第 ${i + 1} 组：记录顺序无关",
                VocabCalculator.computeCalendar(r, S, "2026-07-30").cells,
                VocabCalculator.computeCalendar(shuffled, S, "2026-07-30").cells)
        }
    }

    // ---- 19f. 日历必须画得下每一条记录 ----
    @Test
    fun `19f calendar covers all records`() {
        val r = listOf(rec("2026-08-20", 40))
        val days = daysCells(r, S, D)
        val far = days.firstOrNull { it.date == "2026-08-20" }
        assertNotNull("晚于计划完成日的记录也有格子", far)
        assertEquals("记录词数在格子上", 40, far!!.ownWords)
        val drawn = days.sumOf { it.ownWords + it.aheadWords }
        assertEquals("一个词都没漏画", 40, drawn)
    }

    // ---- 20. 只复习没背新词的那天不算漏 ----
    @Test
    fun `20 review only day is not gap`() {
        val r = listOf(rec("2026-07-28", 40), rec("2026-07-30", 40))
        val withReview = r + listOf(rec("2026-07-29", 25, "backlog"))

        fun day(recs: List<VocabRecord>, date: String) =
            daysCells(recs, S, "2026-07-31").first { it.date == date }

        assertTrue("没复习记录时 7/29 是红边缺口", day(r, "2026-07-29").gap)
        assertFalse("那天复习过就不再是缺口", day(withReview, "2026-07-29").gap)
        assertTrue("改标成「复习日」", day(withReview, "2026-07-29").reviewOnly)
        assertEquals("复习量挂在格子上", 25, day(withReview, "2026-07-29").reviewWords)

        // 互斥
        daysCells(withReview, S, "2026-07-31").forEach { c ->
            assertFalse("${c.date} 不同时是缺口和复习日", c.gap && c.reviewOnly)
        }

        // 复习不进新词进度
        assertEquals("复习不进 doneWords",
            VocabCalculator.computeState(r, S, "2026-07-31").doneWords,
            VocabCalculator.computeState(withReview, S, "2026-07-31").doneWords)

        // 填满的那天有复习记录也不该被标成「复习日」
        assertFalse("填满的天不标复习日", day(withReview, "2026-07-28").reviewOnly)

        // 今天不算漏也不算复习日
        val todayCell = day(withReview, "2026-07-31")
        assertFalse("今天不是缺口", todayCell.gap)
        assertFalse("今天不是复习日", todayCell.reviewOnly)
    }

    // ---- 21. 分段日速 ----
    @Test
    fun `21 segmented daily rate`() {
        val base = VocabConfig(totalWords = 200, initialDone = 40, dailyWords = 20, alarmLateDays = 3)
        val seg = base.copy(rateChanges = listOf(RateChange("2026-08-01", 40)))

        assertEquals("生效日之前还是老速度", 20, VocabCalculator.dailyQuotaOn("2026-07-31", seg))
        assertEquals("生效日当天就是新速度", 40, VocabCalculator.dailyQuotaOn("2026-08-01", seg))
        assertEquals("生效日之后一直是新速度", 40, VocabCalculator.dailyQuotaOn("2026-09-30", seg))
        assertEquals("没有分段时恒等于基准值", 20, VocabCalculator.dailyQuotaOn("2026-09-30", base))

        // 跨段求和
        assertEquals("跨段求和", 160,
            VocabCalculator.plannedWordsInRange(seg,
                VocabCalculator.toEpochDay("2026-07-28"), VocabCalculator.toEpochDay("2026-08-02")))
        assertEquals("单段求和退化成乘法", 120,
            VocabCalculator.plannedWordsInRange(base,
                VocabCalculator.toEpochDay("2026-07-28"), VocabCalculator.toEpochDay("2026-08-02")))
        assertEquals("区间为空返回 0", 0,
            VocabCalculator.plannedWordsInRange(seg,
                VocabCalculator.toEpochDay("2026-08-02"), VocabCalculator.toEpochDay("2026-08-01")))

        // 反查天数
        assertEquals("反查天数（跨段）", 6,
            VocabCalculator.daysToAccumulate(seg, VocabCalculator.toEpochDay("2026-07-28"), 160))
        assertEquals("反查天数（不足一天也算一天）", 7,
            VocabCalculator.daysToAccumulate(seg, VocabCalculator.toEpochDay("2026-07-28"), 161))
        assertEquals("反查 0 需要 0 天", 0,
            VocabCalculator.daysToAccumulate(seg, VocabCalculator.toEpochDay("2026-07-28"), 0))
        assertEquals("单段反查 = ceil(need/日速)", 9,
            VocabCalculator.daysToAccumulate(base, VocabCalculator.toEpochDay("2026-07-28"), 161))

        // 过去那些格子的容量不许被后面的提速改掉
        val r = listOf(rec("2026-07-28", 20), rec("2026-08-01", 20))
        val cells = daysCells(r, seg, "2026-08-02")
        fun at(d: String) = cells.first { it.date == d }
        assertEquals("提速前那天的容量还是 20", 20, at("2026-07-28").quota)
        assertEquals("提速前那天仍是满格", 1.0, at("2026-07-28").fill, 0.001)
        assertEquals("提速后那天的容量是 40", 40, at("2026-08-01").quota)
        assertEquals("提速后同样记 20 只填半格", 0.5, at("2026-08-01").fill, 0.001)
        assertTrue("提速后没记满的那天是缺口", at("2026-08-01").gap)

        // 计划完成日跟着提速提前
        val st = VocabCalculator.computeState(r, seg.copy(startDate = "2026-07-28"), "2026-07-28")
        assertEquals("计划完成日按分段算", "2026-08-02", st.planFinishDate)
        assertEquals("单段时计划完成日不变", "2026-08-04",
            VocabCalculator.computeState(r, base.copy(startDate = "2026-07-28"), "2026-07-28").planFinishDate)
        assertEquals("今天的额度取今天那一段", 20, st.todayQuota)
        assertEquals("提速之后今天的额度变 40", 40,
            VocabCalculator.computeState(r, seg, "2026-08-05").todayQuota)

        // 脏数据洗干净
        val dirty = VocabConfig(dailyWords = 20, rateChanges = listOf(
            RateChange("x", 40), RateChange("2026-08-01", 0),
            RateChange("2026-08-02", 40),
        ))
        assertEquals("洗掉结构不对的分段",
            listOf(RateChange("2026-08-02", 40)),
            VocabCalculator.normalizeRateChanges(dirty))

        val unsorted = VocabConfig(dailyWords = 20, rateChanges = listOf(
            RateChange("2026-09-01", 60), RateChange("2026-08-01", 40),
        ))
        assertEquals("乱序的分段会被排好",
            listOf("2026-08-01", "2026-09-01"),
            VocabCalculator.normalizeRateChanges(unsorted).map { it.from })

        // normalizeRateChanges 返回新列表
        val defaults = VocabConfig()
        assertNotSame("normalizeRateChanges 返回新列表",
            defaults.rateChanges, VocabCalculator.normalizeRateChanges(defaults))
    }

    // ---- 22. 复习：每天到期量模式 ----
    @Test
    fun `22 daily review mode`() {
        val T = "2026-07-30"
        val r = listOf(rec("2026-07-28", 40), rec("2026-07-29", 30, "backlog"), rec(T, 12, "backlog"))

        val noDue = VocabCalculator.computeState(r, S, T)
        assertEquals("累计复习 = 所有复习记录之和", 42, noDue.reviewTotal)
        assertEquals("今天复习了多少", 12, noDue.todayReview)
        assertNull("今天没校准 → 目标为 null", noDue.reviewDueToday)
        assertNull("今天没校准 → 还差多少也是 null", noDue.reviewLeftToday)

        val stale = VocabCalculator.computeState(r,
            S.copy(reviewDue = 50, reviewDueDate = "2026-07-29"), T)
        assertNull("昨天设的目标今天不作数", stale.reviewDueToday)

        val due = S.copy(reviewDue = 20, reviewDueDate = T)
        assertEquals("今天的目标", 20, VocabCalculator.computeState(r, due, T).reviewDueToday)
        assertEquals("今天还差多少", 8, VocabCalculator.computeState(r, due, T).reviewLeftToday)
        assertEquals("超额完成夹到 0", 0,
            VocabCalculator.computeState(r, S.copy(reviewDue = 5, reviewDueDate = T), T).reviewLeftToday)
        assertEquals("累计不受 backlogTotal 影响", 42,
            VocabCalculator.computeState(r, S.copy(backlogTotal = 9999), T).reviewTotal)
        assertEquals("backlogLeft 公式没变", 80 - 42,
            VocabCalculator.computeState(r, S, T).backlogLeft)
    }

    // ---- 23. 复习累计的里程碑档位 ----
    @Test
    fun `23 review milestone levels`() {
        val T = "2026-07-30"
        fun step(n: Int) = S.copy(reviewStep = n)
        fun revs(total: Int) = if (total == 0) emptyList() else listOf(rec(T, total, "backlog"))
        fun st(total: Int, n: Int) = VocabCalculator.computeState(revs(total), step(n), T)

        assertEquals("一个没复习：第 1 档",
            listOf(1, 0), listOf(st(0, 500).reviewLevel, st(0, 500).reviewIntoLevel))
        assertEquals("一个没复习：距下一档 = 一整档", 500, st(0, 500).reviewToNext)

        assertEquals("档中间：526 → 第 2 档进了 26",
            listOf(2, 26), listOf(st(526, 500).reviewLevel, st(526, 500).reviewIntoLevel))
        assertEquals("档中间：距下一档 474", 474, st(526, 500).reviewToNext)

        assertEquals("正好满 500：停在第 1 档满格",
            listOf(1, 500), listOf(st(500, 500).reviewLevel, st(500, 500).reviewIntoLevel))
        assertEquals("正好满 500：距下一档 0", 0, st(500, 500).reviewToNext)
        assertEquals("满档后再复习 1 个：进第 2 档",
            listOf(2, 1), listOf(st(501, 500).reviewLevel, st(501, 500).reviewIntoLevel))
        assertEquals("正好满两档：停在第 2 档满格",
            listOf(2, 500), listOf(st(1000, 500).reviewLevel, st(1000, 500).reviewIntoLevel))

        // 条子只能往前走
        var prev = -1
        for (n in 0..1200 step 7) {
            val s = st(n, 500)
            val walked = (s.reviewLevel - 1) * s.reviewStep + s.reviewIntoLevel
            assertTrue("累计 $n 时里程碑进度不倒退", walked >= prev)
            prev = walked
        }

        // 档长脏数据
        assertEquals("档长填 0 → 退回 500", 500, st(526, 0).reviewStep)
        assertTrue("档长为负 → 至少 1", st(526, -20).reviewStep >= 1)
        assertEquals("换档长不动累计",
            listOf(526, 526), listOf(st(526, 500).reviewTotal, st(526, 100).reviewTotal))
        assertEquals("档长 100：526 → 第 6 档进了 26",
            listOf(6, 26), listOf(st(526, 100).reviewLevel, st(526, 100).reviewIntoLevel))
    }

    // ---- 24. planStartDone：把历史进度与新计划解耦 ----
    @Test
    fun `24 planStartDone decouples history from new plan`() {
        val cfg = VocabConfig(
            totalWords = 2416,
            initialDone = 380,
            dailyWords = 20,
            rateChanges = listOf(RateChange("2026-08-02", 40)),
            planStartDone = 420,
            examDate = "2026-12-19",
        )
        val records = listOf(
            rec("2026-07-27", 20),
            rec("2026-07-28", 20),
            rec("2026-08-02", 40),
            rec("2026-08-03", 40),
            rec("2026-08-04", 40),
            rec("2026-08-05", 40),
            rec("2026-08-06", 50),
            rec("2026-08-07", 40),
            rec("2026-08-08", 40),
            rec("2026-08-09", 40),
            rec("2026-08-10", 40),
            rec("2026-08-11", 40),
            rec("2026-08-12", 40),
            rec("2026-08-13", 40),
        )
        val st = VocabCalculator.computeState(records, cfg, "2026-08-13")
        assertEquals("doneWords 历史新词全算", 910, st.doneWords)
        assertEquals("计划完成日从 8/2 起算，约 9/20", "2026-09-20", st.planFinishDate)
        assertEquals("todayQuota 取新日速", 40, st.todayQuota)
    }

    // ---- 25. planStartDone 为空 = 旧算法，行为不变 ----
    @Test
    fun `25 null planStartDone keeps old behavior`() {
        val cfg = VocabConfig(
            totalWords = 200,
            initialDone = 40,
            dailyWords = 40,
            planStartDone = null,
        )
        val st = VocabCalculator.computeState(emptyList(), cfg, D)
        assertEquals("旧算法计划完成日不变", "2026-07-31", st.planFinishDate)
    }
}
