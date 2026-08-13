package com.zyj.ritual.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zyj.ritual.domain.vocab.RateChange
import com.zyj.ritual.domain.vocab.VocabConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.vocabDataStore: DataStore<Preferences> by preferencesDataStore(name = "vocab_config")

class VocabConfigStore(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.vocabDataStore)

    private object Keys {
        val BOOK_NAME = stringPreferencesKey("book_name")
        val START_DATE = stringPreferencesKey("start_date")
        val TOTAL_WORDS = intPreferencesKey("total_words")
        val INITIAL_DONE = intPreferencesKey("initial_done")
        // 可空字段：键不存在 = null（旧算法），存在 = 新计划起点量
        val PLAN_START_DONE = intPreferencesKey("plan_start_done")
        val DAILY_WORDS = intPreferencesKey("daily_words")
        val EXAM_DATE = stringPreferencesKey("exam_date")
        val RATE_CHANGES_JSON = stringPreferencesKey("rate_changes_json")
        val ALARM_LATE_DAYS = intPreferencesKey("alarm_late_days")
        val REVIEW_MODE = stringPreferencesKey("review_mode")
        val REVIEW_DUE = intPreferencesKey("review_due")
        val REVIEW_DUE_DATE = stringPreferencesKey("review_due_date")
        val REVIEW_STEP = intPreferencesKey("review_step")
        val BACKLOG_TOTAL = intPreferencesKey("backlog_total")
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun configFlow(): Flow<VocabConfig> = dataStore.data.map { prefs ->
        val defaults = VocabConfig()
        val rateChangesRaw = prefs[Keys.RATE_CHANGES_JSON]
        val rateChanges = if (!rateChangesRaw.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<List<RateChangeSerializable>>(rateChangesRaw).map { it.toDomain() }
            }.getOrDefault(defaults.rateChanges)
        } else defaults.rateChanges

        VocabConfig(
            bookName = prefs[Keys.BOOK_NAME] ?: defaults.bookName,
            startDate = prefs[Keys.START_DATE] ?: defaults.startDate,
            totalWords = prefs[Keys.TOTAL_WORDS] ?: defaults.totalWords,
            initialDone = prefs[Keys.INITIAL_DONE] ?: defaults.initialDone,
            planStartDone = prefs[Keys.PLAN_START_DONE],
            dailyWords = prefs[Keys.DAILY_WORDS] ?: defaults.dailyWords,
            examDate = prefs[Keys.EXAM_DATE] ?: defaults.examDate,
            rateChanges = rateChanges,
            alarmLateDays = prefs[Keys.ALARM_LATE_DAYS] ?: defaults.alarmLateDays,
            reviewMode = prefs[Keys.REVIEW_MODE] ?: defaults.reviewMode,
            reviewDue = prefs[Keys.REVIEW_DUE] ?: defaults.reviewDue,
            reviewDueDate = prefs[Keys.REVIEW_DUE_DATE] ?: defaults.reviewDueDate,
            reviewStep = prefs[Keys.REVIEW_STEP] ?: defaults.reviewStep,
            backlogTotal = prefs[Keys.BACKLOG_TOTAL] ?: defaults.backlogTotal,
        )
    }

    suspend fun getConfig(): VocabConfig = configFlow().first()

    suspend fun saveConfig(config: VocabConfig) {
        val rateChangesList = config.rateChanges.map { RateChangeSerializable.fromDomain(it) }
        val rateChangesJsonStr = json.encodeToString(rateChangesList)

        dataStore.edit { prefs ->
            prefs[Keys.BOOK_NAME] = config.bookName
            prefs[Keys.START_DATE] = config.startDate
            prefs[Keys.TOTAL_WORDS] = config.totalWords
            prefs[Keys.INITIAL_DONE] = config.initialDone
            if (config.planStartDone != null) {
                prefs[Keys.PLAN_START_DONE] = config.planStartDone
            } else {
                prefs.remove(Keys.PLAN_START_DONE)
            }
            prefs[Keys.DAILY_WORDS] = config.dailyWords
            prefs[Keys.EXAM_DATE] = config.examDate
            prefs[Keys.RATE_CHANGES_JSON] = rateChangesJsonStr
            prefs[Keys.ALARM_LATE_DAYS] = config.alarmLateDays
            prefs[Keys.REVIEW_MODE] = config.reviewMode
            prefs[Keys.REVIEW_DUE] = config.reviewDue
            prefs[Keys.REVIEW_DUE_DATE] = config.reviewDueDate
            prefs[Keys.REVIEW_STEP] = config.reviewStep
            prefs[Keys.BACKLOG_TOTAL] = config.backlogTotal
        }
    }
}

@kotlinx.serialization.Serializable
private data class RateChangeSerializable(val from: String, val dailyWords: Int) {
    fun toDomain(): RateChange = RateChange(from, dailyWords)
    companion object {
        fun fromDomain(rc: RateChange): RateChangeSerializable = RateChangeSerializable(rc.from, rc.dailyWords)
    }
}
