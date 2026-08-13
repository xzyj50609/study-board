package com.zyj.ritual.data.repository

import androidx.room.withTransaction
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.local.entity.HistoryEventEntity
import com.zyj.ritual.data.local.entity.PaperSessionEntity
import com.zyj.ritual.data.local.entity.TaskRecordEntity
import com.zyj.ritual.data.local.entity.VocabRecordEntity
import com.zyj.ritual.data.store.PlanStore
import com.zyj.ritual.data.store.VocabConfigStore

/**
 * 全应用唯一的完整备份入口。
 *
 * 本地文件与云端都必须走这里，避免文章数据和背词数据各自拼装、恢复一半。
 * 旧版备份若同时缺少两项背词字段，只替换文章数据并保留手机上的背词数据。
 */
class BackupRepository(
    private val db: AppDatabase,
    private val planStore: PlanStore,
    private val vocabConfigStore: VocabConfigStore,
    private val clock: BeijingClock,
) {
    private val taskDao = db.taskRecordDao()
    private val historyDao = db.historyEventDao()
    private val vocabDao = db.vocabRecordDao()
    private val paperSessionDao = db.paperSessionDao()

    suspend fun exportAll(): ExportData {
        val plan = planStore.getPlan() ?: error("Plan not set up, cannot export")
        return ExportData(
            plan = plan,
            records = taskDao.getAll().map { it.toDomain() },
            history = historyDao.getAll().map { it.toDomain() },
            exportedAt = clock.now(),
            vocabConfig = vocabConfigStore.getConfig(),
            vocabRecords = vocabDao.getAll().map { it.toDomain() },
            paperSessions = paperSessionDao.getAll().map { it.toDomain() },
        )
    }

    suspend fun importAll(data: ExportData) {
        val hasVocabConfig = data.vocabConfig != null
        val hasVocabRecords = data.vocabRecords != null
        require(hasVocabConfig == hasVocabRecords) {
            "背词配置和背词记录必须同时存在或同时缺失"
        }

        db.withTransaction {
            taskDao.deleteAll()
            historyDao.deleteAll()
            paperSessionDao.deleteAll()
            taskDao.insertAll(data.records.map { TaskRecordEntity.fromDomain(it) })
            historyDao.insertAll(data.history.map { HistoryEventEntity.fromDomain(it) })
            paperSessionDao.insertAll(data.paperSessions.map { PaperSessionEntity.fromDomain(it) })

            if (hasVocabRecords) {
                vocabDao.deleteAll()
                vocabDao.insertAll(data.vocabRecords.map { VocabRecordEntity.fromDomain(it) })
            }
        }

        planStore.savePlan(data.plan)
        if (hasVocabConfig) {
            vocabConfigStore.saveConfig(data.vocabConfig)
        }
    }
}
