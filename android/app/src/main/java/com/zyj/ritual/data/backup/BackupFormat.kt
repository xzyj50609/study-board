package com.zyj.ritual.data.backup

import com.zyj.ritual.data.repository.ExportData
import com.zyj.ritual.domain.model.*
import com.zyj.ritual.domain.vocab.RateChange
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.domain.vocab.VocabRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * 备份文件格式（v2）。
 *
 * 包含：
 * - 读文章 (plan, records, history)
 * - 背单词 (vocabConfig, vocabRecords) —— 可选，v1 备份导入时该项为 null
 */
@Serializable
data class BackupFile(
    val formatVersion: Int = 2,
    val exportedAt: String,  // ISO-8601 Instant
    val plan: BackupPlan,
    val records: List<BackupRecord>,
    val history: List<BackupHistoryEvent>,
    val vocabConfig: BackupVocabConfig? = null,
    val vocabRecords: List<BackupVocabRecord>? = null,
) {
    fun toDomain(): ExportData = ExportData(
        plan = plan.toDomain(),
        records = records.map { it.toDomain() },
        history = history.map { it.toDomain() },
        exportedAt = Instant.parse(exportedAt),
        vocabConfig = vocabConfig?.toDomain(),
        vocabRecords = vocabRecords?.map { it.toDomain() },
    )

    companion object {
        fun fromDomain(data: ExportData): BackupFile = BackupFile(
            formatVersion = CURRENT_VERSION,
            exportedAt = data.exportedAt.toString(),
            plan = BackupPlan.fromDomain(data.plan),
            records = data.records.map { BackupRecord.fromDomain(it) },
            history = data.history.map { BackupHistoryEvent.fromDomain(it) },
            vocabConfig = data.vocabConfig?.let { BackupVocabConfig.fromDomain(it) },
            vocabRecords = data.vocabRecords?.map { BackupVocabRecord.fromDomain(it) },
        )

        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class BackupPlan(
    val totalArticles: Int,
    val tasksPerArticle: Int,
    val startArticle: Int,
    val completedBeforeStart: Int,
    val planStartDate: String,    // ISO LocalDate
    val daysPerArticle: Int,
    val studyWeekdays: List<String>,  // DayOfWeek.name
    val timezone: String,
) {
    fun toDomain(): Plan = Plan(
        totalArticles = totalArticles,
        tasksPerArticle = tasksPerArticle,
        startArticle = startArticle,
        completedBeforeStart = completedBeforeStart,
        planStartDate = LocalDate.parse(planStartDate),
        daysPerArticle = daysPerArticle,
        studyWeekdays = studyWeekdays.map { DayOfWeek.valueOf(it) }.toSet(),
        timezone = timezone,
    )

    companion object {
        fun fromDomain(plan: Plan): BackupPlan = BackupPlan(
            totalArticles = plan.totalArticles,
            tasksPerArticle = plan.tasksPerArticle,
            startArticle = plan.startArticle,
            completedBeforeStart = plan.completedBeforeStart,
            planStartDate = plan.planStartDate.toString(),
            daysPerArticle = plan.daysPerArticle,
            studyWeekdays = plan.studyWeekdays.map { it.name },
            timezone = plan.timezone,
        )
    }
}

@Serializable
data class BackupRecord(
    val id: String,
    val articleIndex: Int,
    val taskIndex: Int,
    val completedAt: String? = null,  // ISO Instant；IMPORTED 为 null
    val plannedDate: String? = null,  // ISO LocalDate
    val source: String,
) {
    fun toDomain(): TaskRecord = TaskRecord(
        id = id,
        articleIndex = articleIndex,
        taskIndex = taskIndex,
        completedAt = completedAt?.let { Instant.parse(it) },
        plannedDate = plannedDate?.let { LocalDate.parse(it) },
        source = RecordSource.valueOf(source),
    )

    companion object {
        fun fromDomain(r: TaskRecord): BackupRecord = BackupRecord(
            id = r.id,
            articleIndex = r.articleIndex,
            taskIndex = r.taskIndex,
            completedAt = r.completedAt?.toString(),
            plannedDate = r.plannedDate?.toString(),
            source = r.source.name,
        )
    }
}

@Serializable
data class BackupHistoryEvent(
    val at: String,
    val type: String,
    val articleIndex: Int? = null,
    val taskIndex: Int? = null,
    val taskName: String? = null,
    val note: String? = null,
) {
    fun toDomain(): HistoryEvent = HistoryEvent(
        at = Instant.parse(at),
        type = HistoryEventType.valueOf(type),
        articleIndex = articleIndex,
        taskIndex = taskIndex,
        taskName = taskName,
        note = note,
    )

    companion object {
        fun fromDomain(e: HistoryEvent): BackupHistoryEvent = BackupHistoryEvent(
            at = e.at.toString(),
            type = e.type.name,
            articleIndex = e.articleIndex,
            taskIndex = e.taskIndex,
            taskName = e.taskName,
            note = e.note,
        )
    }
}

@Serializable
data class BackupVocabConfig(
    val bookName: String,
    val startDate: String,
    val totalWords: Int,
    val initialDone: Int,
    val dailyWords: Int,
    val examDate: String,
    val rateChanges: List<BackupRateChange> = emptyList(),
    val alarmLateDays: Int = 3,
    val reviewMode: String = "daily",
    val reviewDue: Int = 0,
    val reviewDueDate: String = "",
    val reviewStep: Int = 500,
    val backlogTotal: Int = 288,
) {
    fun toDomain(): VocabConfig = VocabConfig(
        bookName = bookName,
        startDate = startDate,
        totalWords = totalWords,
        initialDone = initialDone,
        dailyWords = dailyWords,
        examDate = examDate,
        rateChanges = rateChanges.map { it.toDomain() },
        alarmLateDays = alarmLateDays,
        reviewMode = reviewMode,
        reviewDue = reviewDue,
        reviewDueDate = reviewDueDate,
        reviewStep = reviewStep,
        backlogTotal = backlogTotal,
    )

    companion object {
        fun fromDomain(cfg: VocabConfig): BackupVocabConfig = BackupVocabConfig(
            bookName = cfg.bookName,
            startDate = cfg.startDate,
            totalWords = cfg.totalWords,
            initialDone = cfg.initialDone,
            dailyWords = cfg.dailyWords,
            examDate = cfg.examDate,
            rateChanges = cfg.rateChanges.map { BackupRateChange.fromDomain(it) },
            alarmLateDays = cfg.alarmLateDays,
            reviewMode = cfg.reviewMode,
            reviewDue = cfg.reviewDue,
            reviewDueDate = cfg.reviewDueDate,
            reviewStep = cfg.reviewStep,
            backlogTotal = cfg.backlogTotal,
        )
    }
}

@Serializable
data class BackupRateChange(
    val from: String,
    val dailyWords: Int,
) {
    fun toDomain(): RateChange = RateChange(from, dailyWords)

    companion object {
        fun fromDomain(rc: RateChange): BackupRateChange = BackupRateChange(rc.from, rc.dailyWords)
    }
}

@Serializable
data class BackupVocabRecord(
    val date: String,
    val words: Int,
    val kind: String = "new",
) {
    fun toDomain(): VocabRecord = VocabRecord(date, words, kind)

    companion object {
        fun fromDomain(r: VocabRecord): BackupVocabRecord = BackupVocabRecord(r.date, r.words, r.kind)
    }
}

/**
 * 备份 JSON 序列化器。
 * 支持版本 1 和版本 2。
 */
object BackupSerializer {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(data: ExportData): String {
        val file = BackupFile.fromDomain(data)
        return json.encodeToString(file)
    }

    fun decode(raw: String): ExportData {
        val file = json.decodeFromString<BackupFile>(raw)

        if (file.formatVersion !in 1..BackupFile.CURRENT_VERSION) {
            throw IllegalArgumentException(
                "Unsupported backup format version: ${file.formatVersion}, " +
                    "expected 1..${BackupFile.CURRENT_VERSION}"
            )
        }

        require(file.plan.totalArticles > 0) { "Invalid totalArticles" }
        require(file.plan.daysPerArticle in 1..3) { "Invalid daysPerArticle" }

        return file.toDomain()
    }
}

