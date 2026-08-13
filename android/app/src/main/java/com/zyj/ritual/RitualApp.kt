package com.zyj.ritual

import android.app.Application
import com.zyj.ritual.core.time.BeijingClock
import com.zyj.ritual.core.time.SystemBeijingClock
import com.zyj.ritual.data.local.AppDatabase
import com.zyj.ritual.data.repository.StudyRepository
import com.zyj.ritual.data.store.PlanStore

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

    lateinit var clock: BeijingClock
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        clock = SystemBeijingClock()
        val db = AppDatabase.getInstance(this)
        val planStore = PlanStore(this)
        repository = StudyRepository(db, planStore, clock)
        vocabRepository = com.zyj.ritual.data.repository.VocabRepository(
            db = db,
            configStore = com.zyj.ritual.data.store.VocabConfigStore(this),
            clock = clock
        )
    }

    companion object {
        lateinit var instance: RitualApp
            private set
    }
}
