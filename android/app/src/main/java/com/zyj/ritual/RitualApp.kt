package com.zyj.ritual

import android.app.Application
import android.util.Log
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.core.time.SystemBeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.repository.BackupRepository
import com.zyj.ritual.data.repository.StudyRepository
import com.zyj.ritual.data.store.PlanStore
import com.zyj.ritual.data.store.VocabConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 类。
 *
 * 为什么不用 Hilt/Dagger：第一版只有 1 个 Repository + 2 个数据源，
 * 手动 wiring 比引入一整个 DI 框架轻得多。以后数据源多了再换不迟。
 */
class RitualApp : Application() {

    lateinit var repository: StudyRepository
        private set

    lateinit var vocabRepository: com.zyj.ritual.data.repository.VocabRepository
        private set

    lateinit var backupRepository: BackupRepository
        private set

    lateinit var clock: BeijingClock
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        clock = SystemBeijingClock()
        val db = AppDatabase.getInstance(this)
        val planStore = PlanStore(this)
        val vocabConfigStore = VocabConfigStore(this)
        repository = StudyRepository(db, planStore, clock)
        vocabRepository = com.zyj.ritual.data.repository.VocabRepository(
            db = db,
            configStore = vocabConfigStore,
            clock = clock
        )
        backupRepository = BackupRepository(db, planStore, vocabConfigStore, clock)

        // v1.0 漏掉的那套卷在这里补记。放在 Application 而不是某个屏幕里，
        // 是因为它必须在任何一页读到 paperSessions 之前跑完，
        // 否则用户会先看见一张欠账红卡、再看见它自己消失。
        applicationScope.launch {
            runCatching { repository.seedLegacyPaperSessionIfNeeded() }
                .onFailure { Log.e("RitualApp", "补记整套卷失败", it) }
        }
    }

    /** 跟 App 同生命周期的协程作用域，只用于启动期的一次性数据修补。 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: RitualApp
            private set
    }
}
