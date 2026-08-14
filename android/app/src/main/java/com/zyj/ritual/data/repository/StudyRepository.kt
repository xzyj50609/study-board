package com.zyj.ritual.data.repository

import android.util.Log
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.local.entity.HistoryEventEntity
import com.zyj.ritual.data.local.entity.PaperSessionEntity
import com.zyj.ritual.data.local.entity.TaskRecordEntity
import com.zyj.ritual.data.store.PlanStore
import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.calculator.TodayCopyResolver
import com.zyj.ritual.domain.calculator.CreditCalculator
import com.zyj.ritual.domain.calculator.DigestionCalculator
import com.zyj.ritual.domain.calculator.ProgressCalculator
import androidx.room.withTransaction
import com.zyj.ritual.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import kotlinx.coroutines.flow.flatMapLatest

private const val STATE_LOG_TAG = "RitualState"

/** v1.0 承诺补记、却从来没写进代码的那套卷 */
private val LEGACY_PAPER_DATE: java.time.LocalDate = java.time.LocalDate.of(2026, 8, 13)
private const val LEGACY_PAPER_NAME = "2016 年卷"

/**
 * 唯一数据入口。
 *
 * 向上暴露组合好的 domain 层对象，向下管理 Room + DataStore。
 * UI 层只跟它打交道，不直接碰数据库。
 *
 * 为什么不把 Repository 拆成接口 + 实现：第一版只有 Room 一个数据源，
 * 拆接口是过度设计。等真的有了"加内存缓存"或"换数据源"的需要再拆。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyRepository(
    private val db: AppDatabase,
    private val planStore: PlanStore,
    private val clock: BeijingClock,
) {
    private val taskDao = db.taskRecordDao()
    private val historyDao = db.historyEventDao()
    private val paperSessionDao = db.paperSessionDao()

    // ——— Plan ———

    fun planFlow(): Flow<Plan?> = planStore.planFlow()

    suspend fun getPlan(): Plan? = planStore.getPlan()

    // ——— Records ———

    fun recordsFlow(): Flow<List<TaskRecord>> = taskDao.observeAll().map { it.toDomainSkippingBadRows() }

    /** 整套卷登记记录（消化期来源）。 */
    fun paperSessionsFlow(): Flow<List<PaperSession>> =
        paperSessionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    // ——— 组合派生 ———

    /**
     * 今日页的聚合状态。
     * Plan + Records + History + 今天 一起组合出进度、额度、文案。
     *
     * 返回 null 只表示一件事：**还没有 Plan**（没设置过）。
     * 上游异常不在这里吞——让它抛出去，由 ViewModel 转成 Error 状态显示在屏幕上。
     */
    fun todayStateFlow(): Flow<TodayState?> {
        // 单独一条坏行不该让整页瘫掉，所以行级容错；但整个流的异常必须往上抛。
        val recordsFlow = taskDao.observeAll().map { it.toDomainSkippingBadRows() }
        val historyFlow = historyDao.observeAll().map { entities ->
            entities.mapNotNull { entity ->
                runCatching { entity.toDomain() }
                    .onFailure { Log.e(STATE_LOG_TAG, "跳过损坏的历史记录 id=${entity.id}", it) }
                    .getOrNull()
            }
        }

        return combine(
            planStore.planFlow(),
            recordsFlow,
            historyFlow,
            paperSessionsFlow(),
            clock.dateFlow(),
        ) { plan, records, history, paperSessions, today ->
            if (plan == null) return@combine null
            val pausedDates = DigestionCalculator.pausedDates(paperSessions)
            val calendar = PlanCalendar.create(plan, pausedDates)
            val progress = ProgressCalculator.calculate(plan, records)
            val credit = CreditCalculator.calculate(plan, calendar, records, today, pausedDates)
            val todayPaper = DigestionCalculator.sessionCovering(today, paperSessions)
            val resumeDate = if (todayPaper != null) {
                DigestionCalculator.resumeDate(today, paperSessions)
            } else null
            val copy = TodayCopyResolver.resolve(
                plan, calendar, records, progress, credit, today, todayPaper, resumeDate
            )

            TodayState(
                plan = plan,
                records = records,
                history = history,
                calendar = calendar,
                progress = progress,
                credit = credit,
                copy = copy,
                today = today,
                pausedDates = pausedDates,
                paperSessions = paperSessions,
                resumeDate = resumeDate,
            )
        }
    }

    // ——— 操作 ———

    /**
     * 勾选一项。
     *
     * @param articleIndex 篇号
     * @param taskIndex 任务号 1..6
     * @param plannedDate 完成时这项的计划日期快照
     * @param source 来源（当天勾选 / 补打卡 / 提前）
     */
    suspend fun checkTask(
        articleIndex: Int,
        taskIndex: Int,
        plannedDate: java.time.LocalDate,
        source: RecordSource,
    ) {
        val now = clock.now()
        val id = TaskRecord.makeId(articleIndex, taskIndex)
        val record = TaskRecord(
            id = id,
            articleIndex = articleIndex,
            taskIndex = taskIndex,
            completedAt = now,
            plannedDate = plannedDate,
            source = source,
        )
        taskDao.insert(TaskRecordEntity.fromDomain(record))

        // 写历史
        val eventType = when (source) {
            RecordSource.BACKFILL -> HistoryEventType.BACKFILL
            RecordSource.ADVANCED -> HistoryEventType.ADVANCED
            else -> HistoryEventType.CHECKED
        }
        historyDao.insert(
            HistoryEventEntity(
                at = now.toString(),
                type = eventType.name,
                articleIndex = articleIndex,
                taskIndex = taskIndex,
                taskName = TodayCopyResolver.taskName(taskIndex),
            )
        )
    }

    /**
     * 撤销一项。
     */
    suspend fun undoTask(articleIndex: Int, taskIndex: Int) {
        val now = clock.now()
        val id = TaskRecord.makeId(articleIndex, taskIndex)
        taskDao.deleteById(id)

        // 写历史（记录被删了，但"我撤销过"这件事要留痕）
        historyDao.insert(
            HistoryEventEntity(
                at = now.toString(),
                type = HistoryEventType.UNDO.name,
                articleIndex = articleIndex,
                taskIndex = taskIndex,
                taskName = TodayCopyResolver.taskName(taskIndex),
            )
        )
    }

    /**
     * 撤销整篇（同时删 6 条记录）。
     */
    suspend fun undoArticle(articleIndex: Int) {
        val now = clock.now()
        taskDao.deleteByArticle(articleIndex)

        historyDao.insert(
            HistoryEventEntity(
                at = now.toString(),
                type = HistoryEventType.UNDO_ARTICLE.name,
                articleIndex = articleIndex,
                note = "撤销第 $articleIndex 篇全部 6 项",
            )
        )
    }

    /**
     * 一键登记整套卷：完成一套英语卷后进入消化期。
     * 不产生任何 TaskRecord——整卷和六步精读是两套统计，不能混（用户拍板）。
     */
    suspend fun registerPaperSession(
        name: String,
        completedDate: java.time.LocalDate,
        partsCount: Int = PaperSession.PARTS_COUNT,
        digestionDays: Int = PaperSession.DIGESTION_DAYS,
    ) {
        val now = clock.now()
        val session = PaperSession(
            name = name,
            completedDate = completedDate,
            partsCount = partsCount,
            digestionDays = digestionDays,
            createdAt = now,
        )
        paperSessionDao.insert(PaperSessionEntity.fromDomain(session))

        historyDao.insert(
            HistoryEventEntity(
                at = now.toString(),
                type = HistoryEventType.PAPER_SESSION.name,
                note = "登记整套卷：$name（${completedDate}）",
            )
        )
    }

    /**
     * 一次性补记 v1.0 漏掉的那套卷。
     *
     * 背景：v1.0 上线时说好「2016 年卷记为 2026-08-13 完成」，但代码里没有任何地方做，
     * 结果用户一装上就是「8/13 的阅读任务没做 → 欠账 → 暗铜色缺额卡」。
     * 这个函数把那条既成事实补进去，让空档从 8/13 当天算起。
     *
     * 三道闸门，缺一不可：
     * 1. 只做一次（PlanStore 的标记）——否则用户撤销后会被重新插回来；
     * 2. 已经有任何一套卷了就不补——用户可能自己先记过，重复会白放四天假；
     * 3. 计划开始日晚于那天就不补——新装的用户不该凭空多出一套别人的卷子。
     *
     * 计划还没建过时直接返回且**不落标记**，等用户设置完计划后下次启动再判断。
     */
    suspend fun seedLegacyPaperSessionIfNeeded() {
        if (planStore.isLegacyPaperSeeded()) return
        val plan = planStore.getPlan() ?: return

        val alreadyHasSessions = paperSessionDao.getAll().isNotEmpty()
        val planCoversThatDay = !plan.planStartDate.isAfter(LEGACY_PAPER_DATE)

        if (!alreadyHasSessions && planCoversThatDay) {
            registerPaperSession(
                name = LEGACY_PAPER_NAME,
                completedDate = LEGACY_PAPER_DATE,
            )
        }
        planStore.markLegacyPaperSeeded()
    }

    /** 撤销一套卷登记（消化期随之消失）。 */
    suspend fun undoPaperSession(id: Long) {
        val target = paperSessionDao.getAll().firstOrNull { it.id == id }
        paperSessionDao.deleteById(id)

        historyDao.insert(
            HistoryEventEntity(
                at = clock.now().toString(),
                type = HistoryEventType.PAPER_SESSION_UNDO.name,
                note = target?.let { "撤销整套卷：${it.name}" } ?: "撤销整套卷 #$id",
            )
        )
    }

    /**
     * 保存 Plan（首次设置 / 修改设置 / 重新排期）。
     *
     * 注意：重排只会改 Plan 的参数，不会动任何已完成记录（R39）。
     */
    suspend fun savePlan(plan: Plan, isReschedule: Boolean = false) {
        planStore.savePlan(plan)

        if (isReschedule) {
            val now = clock.now()
            historyDao.insert(
                HistoryEventEntity(
                    at = now.toString(),
                    type = HistoryEventType.RESCHEDULE.name,
                    articleIndex = plan.startArticle,
                    note = "重新排期：从第 ${plan.startArticle} 篇开始",
                )
            )
        }
    }

    /**
     * 首次设置：写入 Plan + 批量生成 IMPORTED 记录。
     *
     * 这个函数是**可以重复执行**的——设置页的「调整起点与总篇数」会再次走到这里。
     * 所以起点篇数调小的时候，要把多出来的 IMPORTED 行删掉，
     * 否则旧的幻影进度会一直挂在总进度里。真实完成记录（勾选/补记/提前）一律不动（R39）。
     *
     * 整个过程放在一个 Room 事务里：要么全成，要么全不动，不留半拉子状态。
     */
    suspend fun initialSetup(plan: Plan) {
        db.withTransaction {
            // 1. 清掉超出新起点的 IMPORTED 行（起点调小 / 总篇数调小）
            taskDao.deleteImportedAbove(plan.completedBeforeStart)

            // 2. 生成 IMPORTED 记录（起点已学完的篇）
            if (plan.completedBeforeStart > 0) {
                val imported = (1..plan.completedBeforeStart).flatMap { art ->
                    (1..plan.tasksPerArticle).map { task ->
                        TaskRecordEntity(
                            id = TaskRecord.makeId(art, task),
                            articleIndex = art,
                            taskIndex = task,
                            completedAt = null,
                            plannedDate = null,
                            source = RecordSource.IMPORTED.name,
                        )
                    }
                }
                taskDao.insertAll(imported)

                // 3. 写一条 IMPORT 历史事件
                historyDao.insert(
                    HistoryEventEntity(
                        at = clock.now().toString(),
                        type = HistoryEventType.IMPORT.name,
                        note = "导入起点 ${plan.completedBeforeStart} 篇（${plan.completedBeforeStart * plan.tasksPerArticle} 项）",
                    )
                )
            }
        }

        // 4. Plan 最后写。DataStore 不在 Room 事务里，放最后可以保证：
        //    只要首页读到了 Plan，它依赖的起点记录就一定已经落库了。
        planStore.savePlan(plan)
    }

    /** Room 行 → domain，遇到损坏的行跳过而不是整页崩掉。 */
    private fun List<TaskRecordEntity>.toDomainSkippingBadRows(): List<TaskRecord> =
        mapNotNull { entity ->
            runCatching { entity.toDomain() }
                .onFailure { Log.e(STATE_LOG_TAG, "跳过损坏的任务记录 id=${entity.id}", it) }
                .getOrNull()
        }

}

/**
 * 今日页的聚合状态。
 * 所有 UI 需要的东西都在这里，不用再自己拼。
 */
data class TodayState(
    val plan: Plan,
    val records: List<TaskRecord>,
    val history: List<HistoryEvent>,
    val calendar: PlanCalendar,
    val progress: ProgressResult,
    val credit: CreditResult,
    val copy: TodayCopyResult,
    val today: java.time.LocalDate,
    /** 整套卷换来的消化暂停日集合（含完成日当天 + 之后完整消化日） */
    val pausedDates: Set<java.time.LocalDate>,
    /** 全部整套卷登记记录 */
    val paperSessions: List<PaperSession>,
    /** 今天在空档里时，计划恢复的那一天；不在空档里为 null */
    val resumeDate: java.time.LocalDate? = null,
)

/**
 * 导出数据结构（与备份格式对应）。
 */
data class ExportData(
    val plan: Plan,
    val records: List<TaskRecord>,
    val history: List<HistoryEvent>,
    val exportedAt: java.time.Instant,
    val vocabConfig: VocabConfig? = null,
    val vocabRecords: List<VocabRecord>? = null,
    val paperSessions: List<PaperSession> = emptyList(),
)
