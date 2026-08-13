package com.zyj.ritual.domain.vocab

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 背词指南灯核心算法 — 从 model.js v2 逐函数翻译。
 *
 * 所有函数都是纯函数：同一组 (records, config, todayStr) 无论算多少次、
 * 在哪台设备算，结果必须一致（幂等）。
 *
 * ⚠️ 三个绝不能翻错的地方（来自合并计划）：
 * 1. 两遍分配：第一遍每天填自己的，第二遍才把溢出往后顺延。
 * 2. 日速是分段的（rateChanges），不是常量。直接改 dailyWords 会追溯改写过去的成绩。
 * 3. 只复习没背新词的那天不算漏，画 reviewOnly 不画 gap。
 */
object VocabCalculator {

    // ════════════════════════════════════════════════════════════════
    //  日期工具
    // ════════════════════════════════════════════════════════════════

    /** "YYYY-MM-DD" → epoch day（自 1970-01-01 起的整数天数） */
    fun toEpochDay(dateStr: String): Long =
        LocalDate.parse(dateStr).toEpochDay()

    /** epoch day → "YYYY-MM-DD" */
    fun fromEpochDay(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).toString()

    /** 两个日期字符串之间的天数差，含起点和终点（同一天 = 1） */
    fun daysBetweenInclusive(startStr: String, endStr: String): Int =
        (toEpochDay(endStr) - toEpochDay(startStr) + 1).toInt()

    /** "YYYY-MM-DD" → "M月D日" */
    fun formatCn(dateStr: String): String {
        val d = LocalDate.parse(dateStr)
        return "${d.monthValue}月${d.dayOfMonth}日"
    }

    // ════════════════════════════════════════════════════════════════
    //  展示派生量
    // ════════════════════════════════════════════════════════════════

    /**
     * 已背百分比（0..100 的整数）。
     *
     * 「还剩 1503 个」和「完成了 20%」讲的是同一件事，但心理感觉截然不同——
     * 用户点名要百分比当主角。这个函数是全 App 唯一的算法出处：
     * 背词看板和进度页摘要卡都调它，两处各算一遍迟早会在边界上对不上。
     *
     * ⚠️ 两条规矩，都是「故障会伪装成正常」的位置：
     * 1. **向下取整**，不四舍五入。
     * 2. **没背完时封顶 99**。1882/1883 是 99.9%，四舍五入或者直接取整都会显示
     *    「100%」——屏幕上没有任何别的地方能反驳它，用户会以为自己背完了。
     *    只有 finished（真的一个不剩）才准出 100。
     */
    fun donePercent(doneWords: Int, totalWords: Int, finished: Boolean): Int {
        if (finished) return 100
        if (totalWords <= 0) return 0
        val ratio = doneWords.toDouble() / totalWords
        val floored = floor(ratio * 100).toInt()
        return floored.coerceIn(0, 99)
    }

    // ════════════════════════════════════════════════════════════════
    //  Records 聚合（纯函数，不修改传入列表）
    // ════════════════════════════════════════════════════════════════

    /**
     * 计划起算日。优先级：显式设置 > 最早一条新词记录 > todayStr。
     * 显式设置和记录冲突时取更早的那个——记录是既成事实，不能被设置抹掉。
     */
    fun getStartDate(records: List<VocabRecord>, todayStr: String, explicitStart: String): String {
        val news = records.filter { it.kind != "backlog" }
        val earliest = if (news.isNotEmpty()) news.minOf { it.date } else null
        if (explicitStart.isNotEmpty()) {
            return if (earliest != null && earliest < explicitStart) earliest else explicitStart
        }
        return earliest ?: todayStr
    }

    /** 新词累计（不含 backlog） */
    fun sumNewWords(records: List<VocabRecord>): Int =
        records.filter { it.kind != "backlog" }.sumOf { it.words }

    /** 积压/复习累计 */
    fun sumBacklogWords(records: List<VocabRecord>): Int =
        records.filter { it.kind == "backlog" }.sumOf { it.words }

    // ════════════════════════════════════════════════════════════════
    //  分段日速
    // ════════════════════════════════════════════════════════════════

    /**
     * 把 rateChanges 洗成干净的升序列表。外部数据一律不信任。
     * 返回新列表，绝不改传进来的那个。
     */
    fun normalizeRateChanges(cfg: VocabConfig): List<RateChange> =
        ArrayList(
            cfg.rateChanges
                .filter { rc ->
                    rc.from.matches(Regex("""\d{4}-\d{2}-\d{2}""")) && rc.dailyWords > 0
                }
                .map { rc -> RateChange(rc.from, max(1, rc.dailyWords)) }
                .sortedBy { it.from }
        )

    /** 某一天计划背几个新词。没有 rateChanges 时恒等于 cfg.dailyWords */
    fun dailyQuotaOn(dateStr: String, cfg: VocabConfig): Int {
        var quota = max(1, cfg.dailyWords)
        for (seg in normalizeRateChanges(cfg)) {
            if (seg.from <= dateStr) quota = seg.dailyWords
            else break
        }
        return quota
    }

    /** 内部区间段：从 start（epoch day）起，每天额度为 quota */
    data class QuotaSegment(val start: Long, val quota: Int)

    /**
     * 把「日速随时间变化」摊成一串区间 [{ start: epochDay, quota }]。
     * 第一段一定从 fromEpoch 开始。
     */
    fun quotaSegments(cfg: VocabConfig, fromEpoch: Long): List<QuotaSegment> {
        val changes = normalizeRateChanges(cfg)
        var base = max(1, cfg.dailyWords)
        val later = mutableListOf<QuotaSegment>()
        for (c in changes) {
            val e = toEpochDay(c.from)
            if (e <= fromEpoch) {
                base = c.dailyWords
            } else {
                later.add(QuotaSegment(e, c.dailyWords))
            }
        }
        return listOf(QuotaSegment(fromEpoch, base)) + later
    }

    /** [fromEpoch, toEpoch] 闭区间内计划要背的新词总数。区间为空返回 0 */
    fun plannedWordsInRange(cfg: VocabConfig, fromEpoch: Long, toEpoch: Long): Int {
        if (toEpoch < fromEpoch) return 0
        val segs = quotaSegments(cfg, fromEpoch)
        var total = 0
        for (i in segs.indices) {
            val s = segs[i].start
            if (s > toEpoch) break
            val nextStart = if (i + 1 < segs.size) segs[i + 1].start else Long.MAX_VALUE
            val e = min(toEpoch, nextStart - 1)
            if (e >= s) total += ((e - s + 1) * segs[i].quota).toInt()
        }
        return total
    }

    /**
     * 从 fromEpoch 那天开始，要几天才能累计背够 need 个词。
     * need ≤ 0 返回 0。单一日速时 = ceil(need / dailyWords)。
     */
    fun daysToAccumulate(cfg: VocabConfig, fromEpoch: Long, need: Int): Int {
        if (need <= 0) return 0
        val segs = quotaSegments(cfg, fromEpoch)
        var left = need
        var days = 0
        for (i in segs.indices) {
            val isLast = i + 1 == segs.size
            val span = if (isLast) Long.MAX_VALUE else segs[i + 1].start - segs[i].start
            if (span <= 0 && !isLast) continue
            val quota = segs[i].quota
            if (isLast || left.toLong() <= span * quota) {
                return days + ceil(left.toDouble() / quota).toInt()
            }
            left -= (span * quota).toInt()
            days += span.toInt()
        }
        return days
    }

    // ════════════════════════════════════════════════════════════════
    //  computeState — 核心状态计算
    // ════════════════════════════════════════════════════════════════

    fun computeState(
        records: List<VocabRecord>,
        settings: VocabConfig,
        todayStr: String,
    ): VocabState {
        val cfg = settings  // JS 里是 Object.assign({}, DEFAULTS, settings)，我们用 data class 默认值代替
        val safeRecords = records
        val notStarted = safeRecords.none { it.kind != "backlog" }

        val startDate = getStartDate(safeRecords, todayStr, cfg.startDate)
        val elapsedDays = max(0, daysBetweenInclusive(startDate, todayStr))
        val startEpoch = toEpochDay(startDate)
        val todayEpoch = toEpochDay(todayStr)
        val todayQuota = dailyQuotaOn(todayStr, cfg)

        // 真实进度：起点存量 + 记录累计，夹到词书总量
        val newWords = sumNewWords(safeRecords)
        val doneWords = min(cfg.initialDone + newWords, cfg.totalWords)

        // 计划线：从起点起逐日累加当天的额度，夹到词书总量
        val dueWords = min(
            cfg.initialDone + plannedWordsInRange(cfg, startEpoch, todayEpoch),
            cfg.totalWords
        )

        // 计划完成日
        val planDays = max(1, daysToAccumulate(cfg, startEpoch, cfg.totalWords - cfg.initialDone))
        val planFinishDate = fromEpochDay(startEpoch + planDays - 1)

        val todayWords = safeRecords
            .filter { it.kind != "backlog" && it.date == todayStr }
            .sumOf { it.words }

        val remainWords = cfg.totalWords - doneWords
        val finished = remainWords <= 0

        // 预计完成日
        val projFinishDate: String
        if (finished) {
            // 已背完：预计完成日 = 真实冲线那天
            var pf = todayStr
            val byDate = safeRecords
                .filter { it.kind != "backlog" }
                .sortedBy { it.date }
            var acc = cfg.initialDone
            for (r in byDate) {
                acc += r.words
                if (acc >= cfg.totalWords) {
                    pf = r.date
                    break
                }
            }
            projFinishDate = pf
        } else {
            // 今天还没用完的额度算今天的机会，不算落后
            val todayCapacity = max(0, todayQuota - todayWords)
            val futureWords = max(0, remainWords - todayCapacity)
            projFinishDate = fromEpochDay(
                todayEpoch + daysToAccumulate(cfg, todayEpoch + 1, futureWords)
            )
        }

        // 提前/落后
        val aheadDays = (toEpochDay(planFinishDate) - toEpochDay(projFinishDate)).toInt()
        val isAlarm = !notStarted && !finished && aheadDays <= -cfg.alarmLateDays

        // 积压
        val backlogLeft = max(0, cfg.backlogTotal - sumBacklogWords(safeRecords))

        // ── 复习 ──
        val reviewTotal = sumBacklogWords(safeRecords)
        val todayReview = safeRecords
            .filter { it.kind == "backlog" && it.date == todayStr }
            .sumOf { it.words }
        val reviewDueToday: Int? = if (cfg.reviewDueDate == todayStr) {
            max(0, cfg.reviewDue)
        } else null
        val reviewLeftToday: Int? = if (reviewDueToday != null) {
            max(0, reviewDueToday - todayReview)
        } else null

        // 里程碑
        val reviewStep = max(1, if (cfg.reviewStep > 0) cfg.reviewStep else 500)
        val justHitStep = reviewTotal > 0 && reviewTotal % reviewStep == 0
        val reviewLevel = if (justHitStep) {
            reviewTotal / reviewStep
        } else {
            reviewTotal / reviewStep + 1
        }
        val reviewIntoLevel = if (justHitStep) reviewStep else reviewTotal % reviewStep
        val reviewToNext = reviewStep - reviewIntoLevel

        val daysToExam = (toEpochDay(cfg.examDate) - toEpochDay(todayStr)).toInt()

        return VocabState(
            notStarted = notStarted,
            startDate = startDate,
            elapsedDays = elapsedDays,
            doneWords = doneWords,
            dueWords = dueWords,
            todayQuota = todayQuota,
            aheadDays = aheadDays,
            isAlarm = isAlarm,
            planFinishDate = planFinishDate,
            projFinishDate = projFinishDate,
            remainWords = remainWords,
            finished = finished,
            backlogLeft = backlogLeft,
            reviewTotal = reviewTotal,
            todayReview = todayReview,
            reviewDueToday = reviewDueToday,
            reviewLeftToday = reviewLeftToday,
            reviewStep = reviewStep,
            reviewLevel = reviewLevel,
            reviewIntoLevel = reviewIntoLevel,
            reviewToNext = reviewToNext,
            daysToExam = daysToExam,
            todayWords = todayWords,
        )
    }

    // ════════════════════════════════════════════════════════════════
    //  computeCalendar — 日历 = 进度条 + 考勤表
    //
    //  ⚠️ 两遍分配在这里，是整个翻译最危险的部分。
    //  一遍走完的话，前一天的溢出会抢占后一天的自有额度，
    //  导致天天超额的人在日历上看到的全是「提前做的」。
    // ════════════════════════════════════════════════════════════════

    fun computeCalendar(
        records: List<VocabRecord>,
        settings: VocabConfig,
        todayStr: String,
    ): VocabCalendarResult {
        val cfg = settings
        val state = computeState(records, settings, todayStr)
        val todayEpoch = toEpochDay(todayStr)
        val startEpoch = toEpochDay(state.startDate)

        // 新词记录按日期聚合
        val ownByDate = mutableMapOf<String, Int>()
        // 复习记录也要按日期聚合
        val reviewByDate = mutableMapOf<String, Int>()
        var lastRecordEpoch = startEpoch

        for (r in records) {
            if (r.kind == "backlog") {
                reviewByDate[r.date] = (reviewByDate[r.date] ?: 0) + r.words
            } else {
                ownByDate[r.date] = (ownByDate[r.date] ?: 0) + r.words
                lastRecordEpoch = max(lastRecordEpoch, toEpochDay(r.date))
            }
        }

        val endEpoch = maxOf(
            toEpochDay(state.planFinishDate),
            toEpochDay(state.projFinishDate),
            todayEpoch,
            lastRecordEpoch
        )

        val n = (endEpoch - startEpoch + 1).toInt()

        // 每格的容量各算各的：日速可以分段
        val quotaAt = IntArray(n)
        for (i in 0 until n) {
            quotaAt[i] = dailyQuotaOn(fromEpochDay(startEpoch + i), cfg)
        }

        val ownWords = IntArray(n)
        val aheadWords = IntArray(n)

        // ★★★ 分两遍，不能一遍走完 ★★★

        val overflow = IntArray(n)

        // 第一遍：自己的词优先占自己那一格
        for (i in 0 until n) {
            val mine = ownByDate[fromEpochDay(startEpoch + i)] ?: 0
            if (mine <= 0) continue
            ownWords[i] = min(mine, quotaAt[i])
            overflow[i] = mine - ownWords[i]
        }

        // 第二遍：装不下的按日期先后往后顺延
        // 只往后不往前——往前回填等于把断过的那天抹平
        for (i in 0 until n) {
            var left = overflow[i]
            if (left <= 0) continue
            var j = i + 1
            while (j < n && left > 0) {
                val spare = quotaAt[j] - ownWords[j] - aheadWords[j]
                if (spare > 0) {
                    val t = min(left, spare)
                    aheadWords[j] += t
                    left -= t
                }
                j++
            }
            // 到这儿还有剩 = 超出词书末尾，日历上画不下了；doneWords 里照样算数
        }

        // 组装 cells
        val cells = mutableListOf<VocabCalendarCell>()
        var lastMonth: Int? = null

        var e = startEpoch
        while (e <= endEpoch) {
            val date = fromEpochDay(e)
            val ld = LocalDate.parse(date)
            val month = ld.monthValue
            val dayNum = ld.dayOfMonth

            // 月份变化 → 插入月份标签 + 周一对齐的前导空位
            if (month != lastMonth) {
                cells.add(VocabCalendarCell.Month("${month}月"))
                // 周一=0, JS: (new Date(date+'T00:00:00Z').getUTCDay() + 6) % 7
                val dow = (ld.dayOfWeek.value - 1)  // DayOfWeek.MONDAY=1 → 0
                for (p in 0 until dow) {
                    cells.add(VocabCalendarCell.Pad)
                }
                lastMonth = month
            }

            val i = (e - startEpoch).toInt()
            val own = ownWords[i]
            val ahead = aheadWords[i]
            val quota = quotaAt[i]
            val fill = (own + ahead).toDouble() / quota
            val review = reviewByDate[date] ?: 0
            val past = e < todayEpoch

            cells.add(
                VocabCalendarCell.Day(
                    date = date,
                    dayNum = dayNum,
                    fill = fill,
                    own = own.toDouble() / quota,
                    ownWords = own,
                    aheadWords = ahead,
                    quota = quota,
                    reviewWords = review,
                    isToday = date == todayStr,
                    isPlanFinish = date == state.planFinishDate,
                    isProjFinish = date == state.projFinishDate && !state.finished,
                    // 缺口：严格早于今天、没填满、且那天什么都没干
                    gap = past && fill < 1.0 && review <= 0,
                    // 复习日：早于今天、新词没填满，但复习过
                    reviewOnly = past && fill < 1.0 && review > 0,
                    // 超额：这格有 aheadWords
                    extra = ahead > 0,
                )
            )

            e++
        }

        return VocabCalendarResult(cells)
    }

    // ════════════════════════════════════════════════════════════════
    //  v1 迁移（给导入指南灯旧备份用）
    // ════════════════════════════════════════════════════════════════

    /**
     * v1 存档（groups 记录）→ v2（words 记录）。
     * 对应 JS 的 migrateV1。
     */
    fun migrateV1(
        oldRecords: List<Map<String, Any?>>,
        oldSettings: Map<String, Any?>,
    ): Pair<VocabConfig, List<VocabRecord>> {
        val wordsPerGroup = (oldSettings["groupNew"] as? Number)?.toInt() ?: 20

        val records = oldRecords
            .filter { r ->
                r["date"] is String && r["groups"] is Number
            }
            .map { r ->
                VocabRecord(
                    date = r["date"] as String,
                    words = ((r["groups"] as Number).toDouble() * wordsPerGroup).toInt(),
                    kind = "new",
                )
            }

        val config = VocabConfig(
            bookName = (oldSettings["bookName"] as? String) ?: VocabConfig().bookName,
            totalWords = (oldSettings["totalWords"] as? Number)?.toInt() ?: VocabConfig().totalWords,
            examDate = (oldSettings["examDate"] as? String) ?: VocabConfig().examDate,
        )

        return config to records
    }
}
