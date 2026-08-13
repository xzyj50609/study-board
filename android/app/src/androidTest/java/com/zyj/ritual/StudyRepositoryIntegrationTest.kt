package com.zyj.ritual

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zyj.ritual.domain.model.Plan
import com.zyj.ritual.domain.model.RecordSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Room + DataStore + Repository 的真机集成测试。
 *
 * JVM 单元测试只覆盖到纯算法，证明不了「存进去能读出来」。
 * 上一轮翻车正好卡在这一层，所以这里专门盯存取链路。
 */
@RunWith(AndroidJUnit4::class)
class StudyRepositoryIntegrationTest {

    private val today = LocalDate.of(2026, 8, 6)
    private lateinit var env: TestEnv

    @Before
    fun setUp() {
        env = TestEnv(fixedDate = today)
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun plan(total: Int = 42, completed: Int = 12, days: Int = 2) = Plan(
        totalArticles = total,
        startArticle = completed + 1,
        completedBeforeStart = completed,
        planStartDate = today,
        daysPerArticle = days,
    )

    @Test
    fun 没设置过时首页状态为空() = runBlocking {
        assertNull(env.repository.getPlan())
        assertNull(env.repository.todayStateFlow().first())
    }

    @Test
    fun 首次设置后计划与起点记录都能读回来() = runBlocking {
        env.repository.initialSetup(plan())

        val saved = env.repository.getPlan()
        assertNotNull(saved)
        assertEquals(42, saved!!.totalArticles)
        assertEquals(13, saved.startArticle)

        val state = env.repository.todayStateFlow().first()
        assertNotNull(state)
        assertEquals(72, state!!.records.size)
        assertTrue(state.records.all { it.source == RecordSource.IMPORTED })
        assertEquals(12, state.progress.completeArticles)
        assertEquals(13, state.progress.currentArticle)
        assertEquals(today, state.today)
    }

    @Test
    fun 勾选一项之后进度和历史同步更新() = runBlocking {
        env.repository.initialSetup(plan())
        env.repository.checkTask(13, 1, today, RecordSource.CHECKED)

        val state = env.repository.todayStateFlow().first()!!
        assertEquals(73, state.records.size)
        assertEquals(1, state.progress.currentArticleCompleted)
        // IMPORT + CHECKED 两条历史
        assertEquals(2, state.history.size)
    }

    @Test
    fun 重新设置起点时要清掉多余的导入记录但保留真实完成记录() = runBlocking {
        env.repository.initialSetup(plan(completed = 12))
        // 第 20 篇第 1 项是用户真的勾的
        env.repository.checkTask(20, 1, today, RecordSource.CHECKED)

        // 起点改成 5 篇
        env.repository.initialSetup(plan(completed = 5))

        val state = env.repository.todayStateFlow().first()!!
        val imported = state.records.filter { it.source == RecordSource.IMPORTED }
        val real = state.records.filter { it.source != RecordSource.IMPORTED }

        assertEquals("导入记录应缩到 5 篇 × 6 项", 30, imported.size)
        assertFalse("超出新起点的导入记录必须清掉", imported.any { it.articleIndex > 5 })
        assertEquals("用户真实勾选的记录不能被动", 1, real.size)
        assertEquals(20, real.first().articleIndex)
    }

    @Test
    fun 撤销一项之后记录减少但历史留痕() = runBlocking {
        env.repository.initialSetup(plan())
        env.repository.checkTask(13, 1, today, RecordSource.CHECKED)
        env.repository.undoTask(13, 1)

        val state = env.repository.todayStateFlow().first()!!
        assertEquals(72, state.records.size)
        assertEquals(3, state.history.size)  // IMPORT + CHECKED + UNDO
    }

    @Test
    fun 备份导出再导入能还原全部数据() = runBlocking {
        env.repository.initialSetup(plan())
        env.repository.checkTask(13, 1, today, RecordSource.CHECKED)
        val exported = env.backupRepository.exportAll()

        // 清空后再导入
        env.db.taskRecordDao().deleteAll()
        env.db.historyEventDao().deleteAll()
        assertEquals(0, env.db.taskRecordDao().count())

        env.backupRepository.importAll(exported)
        val state = env.repository.todayStateFlow().first()!!
        assertEquals(73, state.records.size)
        assertEquals(exported.plan.totalArticles, state.plan.totalArticles)
    }

    @Test
    fun 总篇数调小之后旧记录不能让页面崩掉() = runBlocking {
        env.repository.initialSetup(plan(total = 42, completed = 12))
        env.repository.checkTask(40, 1, today, RecordSource.CHECKED)

        // 总篇数缩到 20，第 40 篇的记录就越界了
        env.repository.savePlan(plan(total = 20, completed = 12))

        val state = env.repository.todayStateFlow().first()
        assertNotNull("越界记录必须被忽略，而不是抛异常让页面停在加载中", state)
        assertEquals(20 * 6, state!!.progress.totalTasks)
    }
}
