package com.zyj.ritual.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zyj.ritual.domain.model.Plan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * DataStore 实例。
 *
 * 必须是文件顶层的单例：DataStore 对同一个文件只允许存在一个活动实例，
 * 放在类里当成员委托的话，一旦 PlanStore 被创建两次（测试里很容易发生）
 * 就会抛 "There are multiple DataStores active for the same file"。
 */
private val Context.planDataStore by preferencesDataStore(name = "plan")

/**
 * Plan 配置的 DataStore 存储。
 *
 * 为什么不用 Room：计划只有一份，结构不复杂，DataStore 轻量够用。
 * 而且首启动时 splash screen 等一下 DataStore 首读比等 Room 建库快。
 */
class PlanStore(private val dataStore: DataStore<Preferences>) {

    /** 生产环境入口：用 App 自己的 DataStore 文件。 */
    constructor(context: Context) : this(context.applicationContext.planDataStore)

    private object Keys {
        val isSetUp = booleanPreferencesKey("is_setup")
        val totalArticles = intPreferencesKey("total_articles")
        val startArticle = intPreferencesKey("start_article")
        val completedBeforeStart = intPreferencesKey("completed_before_start")
        val planStartDate = stringPreferencesKey("plan_start_date")  // ISO 日期
        val daysPerArticle = intPreferencesKey("days_per_article")
        val timezone = stringPreferencesKey("timezone")
        // studyWeekdays 用逗号分隔的 DayOfWeek 名字
        val studyWeekdays = stringPreferencesKey("study_weekdays")
        // 一次性补记：v1.0 漏掉的 2026-08-13 那套卷是否已经补进去过
        val legacyPaperSeeded = booleanPreferencesKey("legacy_paper_seeded")
    }

    /**
     * 一次性补记标记。
     *
     * 为什么要有它：补记必须只发生一次。没有这个标记的话，用户手动撤销那条记录后，
     * 下次冷启动又会给他插回来——撤销按钮变成一个按了没用的按钮，
     * 而且屏幕上完全看不出这是故障，只会觉得"这 App 有毛病"。
     */
    suspend fun isLegacyPaperSeeded(): Boolean =
        dataStore.data.map { it[Keys.legacyPaperSeeded] ?: false }.first()

    suspend fun markLegacyPaperSeeded() {
        dataStore.edit { prefs -> prefs[Keys.legacyPaperSeeded] = true }
    }

    /**
     * 观察 Plan 的 Flow。
     * 首次启动、还没设置过时，返回一个默认 Plan（isSetUp=false 由上层判断）。
     */
    fun planFlow(): Flow<Plan?> = dataStore.data.map { prefs ->
        val isSetUp = prefs[Keys.isSetUp] ?: false
        if (!isSetUp) null
        else {
            val total = prefs[Keys.totalArticles] ?: 42
            val startArt = prefs[Keys.startArticle] ?: 1
            val completed = prefs[Keys.completedBeforeStart] ?: 0
            val dateStr = prefs[Keys.planStartDate]
            val startDate = dateStr?.let { LocalDate.parse(it) }
                ?: LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
            val daysPer = prefs[Keys.daysPerArticle] ?: 2
            val tz = prefs[Keys.timezone] ?: "Asia/Shanghai"
            val weekdaysStr = prefs[Keys.studyWeekdays]
            val weekdays = parseWeekdays(weekdaysStr)

            Plan(
                totalArticles = total,
                startArticle = startArt,
                completedBeforeStart = completed,
                planStartDate = startDate,
                daysPerArticle = daysPer,
                studyWeekdays = weekdays,
                timezone = tz,
            )
        }
    }

    suspend fun getPlan(): Plan? = planFlow().first()

    /**
     * 保存 Plan（首次设置或重新排期时调用）。
     */
    suspend fun savePlan(plan: Plan) {
        dataStore.edit { prefs ->
            prefs[Keys.isSetUp] = true
            prefs[Keys.totalArticles] = plan.totalArticles
            prefs[Keys.startArticle] = plan.startArticle
            prefs[Keys.completedBeforeStart] = plan.completedBeforeStart
            prefs[Keys.planStartDate] = plan.planStartDate.toString()
            prefs[Keys.daysPerArticle] = plan.daysPerArticle
            prefs[Keys.timezone] = plan.timezone
            prefs[Keys.studyWeekdays] = plan.studyWeekdays.joinToString(",") { it.name }
        }
    }

    private fun parseWeekdays(str: String?): Set<DayOfWeek> {
        if (str.isNullOrEmpty()) return DayOfWeek.entries.toSet()
        return str.split(",").mapNotNull { name ->
            runCatching { DayOfWeek.valueOf(name.trim()) }.getOrNull()
        }.toSet().ifEmpty { DayOfWeek.entries.toSet() }
    }
}
