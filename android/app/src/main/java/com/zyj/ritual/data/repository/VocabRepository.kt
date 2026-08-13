package com.zyj.ritual.data.repository

import androidx.room.withTransaction
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.local.entity.VocabRecordEntity
import com.zyj.ritual.data.store.VocabConfigStore
import com.zyj.ritual.domain.vocab.VocabCalculator
import com.zyj.ritual.domain.vocab.VocabCalendarResult
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import com.zyj.ritual.domain.vocab.VocabState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class VocabAggregateState(
    val config: VocabConfig,
    val records: List<VocabRecord>,
    val state: VocabState,
    val calendar: VocabCalendarResult,
    val today: java.time.LocalDate,
)

@OptIn(ExperimentalCoroutinesApi::class)
class VocabRepository(
    private val db: AppDatabase,
    private val configStore: VocabConfigStore,
    private val clock: BeijingClock,
) {
    private val vocabDao = db.vocabRecordDao()

    fun configFlow(): Flow<VocabConfig> = configStore.configFlow()

    suspend fun getConfig(): VocabConfig = configStore.getConfig()

    fun recordsFlow(): Flow<List<VocabRecord>> =
        vocabDao.observeAll().combine(clock.dateFlow()) { entities, _ ->
            entities.map { it.toDomain() }
        }

    /**
     * 组合背词的全部状态：Config + Records + Today Date -> State & Calendar.
     */
    fun aggregateStateFlow(): Flow<VocabAggregateState> {
        val recordsFlow = vocabDao.observeAll()

        return combine(
            configStore.configFlow(),
            recordsFlow,
            clock.dateFlow(),
        ) { config, entities, today ->
            val records = entities.map { it.toDomain() }
            val todayStr = today.toString()
            val state = VocabCalculator.computeState(records, config, todayStr)
            val calendar = VocabCalculator.computeCalendar(records, config, todayStr)

            VocabAggregateState(
                config = config,
                records = records,
                state = state,
                calendar = calendar,
                today = today,
            )
        }
    }

    suspend fun addRecord(words: Int, kind: String = "new", dateStr: String? = null) {
        val date = dateStr ?: clock.today().toString()
        val record = VocabRecordEntity(
            date = date,
            words = words,
            kind = kind,
            // 走 BeijingClock，不用 System.currentTimeMillis()：全工程时间只有一个来源，
            // 测试才能注入固定时钟
            createdAt = clock.now().toEpochMilli(),
        )
        vocabDao.insert(record)
    }

    /**
     * 存「今天要复习多少」——照不背单词 APP 首页那个数抄来的当日到期量。
     *
     * ⚠️ 日期必须走 `clock.today()`（北京时区）。用设备时区的话，本机在 Pacific 时
     * 跟北京差 16 小时，存下来的日期有大半天对不上「今天」，
     * `reviewDueToday` 会退回 null，复习那条进度条整天画不出来——
     * 而屏幕上看起来就只是「你今天还没填」，一点都不像故障。
     */
    suspend fun saveReviewDue(due: Int) {
        val current = configStore.getConfig()
        configStore.saveConfig(
            current.copy(
                reviewDue = due.coerceAtLeast(0),
                reviewDueDate = clock.today().toString(),
            )
        )
    }

    suspend fun saveConfig(config: VocabConfig) {
        configStore.saveConfig(config)
    }

    suspend fun importBeiciData(config: VocabConfig, records: List<VocabRecord>) {
        db.withTransaction {
            vocabDao.deleteAll()
            vocabDao.insertAll(records.map { VocabRecordEntity.fromDomain(it) })
        }
        configStore.saveConfig(config)
    }

    suspend fun deleteAll() {
        db.withTransaction {
            vocabDao.deleteAll()
        }
    }
}
