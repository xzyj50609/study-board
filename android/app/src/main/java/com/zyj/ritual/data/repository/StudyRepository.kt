package com.zyj.ritual.data.repository

import android.util.Log
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.local.entity.HistoryEventEntity
import com.zyj.ritual.data.local.entity.TaskRecordEntity
import com.zyj.ritual.data.store.PlanStore
import com.zyj.ritual.domain.calendar.PlanCalendar
import com.zyj.ritual.domain.calculator.TodayCopyResolver
import com.zyj.ritual.domain.calculator.CreditCalculator
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

    // ——— Plan ———

    fun planFlow(): Flow<Plan?> = planStore.planFlow()

    suspend fun getPlan(): Plan? = planStore.getPlan()

    // ——— Records ———

    fun recordsFlow(): Flow<List<TaskRecord>> = taskDao.observeAll().map { it.toDomainSkippingBadRows() }

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
            clock.dateFlow(),
        ) { plan, records, history, today ->
            if (plan == null) return@combine null
            val calendar = PlanCalendar.create(plan)
            val progress = ProgressCalculator.calculate(plan, records)
            val credit = CreditCalculator.calculate(plan, calendar, records, today)
            val copy = TodayCopyResolver.resolve(plan, calendar, records, progress, credit, today)

            TodayState(
                plan = plan,
                records = records,
                history = history,
                calendar = calendar,
                progress = progress,
                credit = credit,
                copy = copy,
                today = today,
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
)
