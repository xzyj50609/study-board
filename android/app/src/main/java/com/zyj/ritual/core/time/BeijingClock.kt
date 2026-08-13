package com.zyj.ritual.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant

/**
 * 北京时间时钟。
 *
 * 所有"今天""日期"判定一律走 Asia/Shanghai，与设备时区无关（R43）。
 *
 * 为什么要封装成接口：测试里可以注入固定时钟，否则用真实时间的单测不稳定。
 * 同时提供 now() 的 Flow，UI 层可以收集它来做跨天自动刷新（R45）。
 */
interface BeijingClock {
    val zone: ZoneId

    fun now(): Instant
    fun today(): LocalDate = now().atZone(zone).toLocalDate()
    fun nowDateTime(): LocalDateTime = now().atZone(zone).toLocalDateTime()

    /**
     * 每次跨北京零点时发射新的 LocalDate。
     * App 在前台跨过 00:00 时，所有派生量要重算（R45）。
     */
    fun dateFlow(): Flow<LocalDate>
}

/**
 * 真实时钟，用于生产环境。
 */
class SystemBeijingClock : BeijingClock {
    override val zone: ZoneId = BEIJING_ZONE

    private val systemClock: Clock = Clock.system(BEIJING_ZONE)

    override fun now(): Instant = Instant.now(systemClock)

    /**
     * 冷流：先发当前日期，然后睡到北京时间下一个零点再发一次（R45）。
     *
     * 上一版这里 `return MutableStateFlow(today()).asStateFlow()`——
     * 每次调用新建一个 StateFlow，发完首值就再也不动，跨天刷新等于没做。
     * 现在按"睡到零点"排，一天最多醒一次，比每秒轮询省电，也不用 AlarmManager。
     * 页面退到后台时 WhileSubscribed 会停掉收集，回前台重新订阅会立刻拿到当天日期。
     */
    override fun dateFlow(): Flow<LocalDate> = flow {
        while (true) {
            val today = today()
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
            val waitMillis = nextMidnight.toEpochMilli() - now().toEpochMilli()
            // 系统时间被改动时可能算出负数，兜一秒防止空转
            delay(waitMillis.coerceAtLeast(1_000L))
        }
    }.distinctUntilChanged()

    companion object {
        val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}

/**
 * 测试用固定时钟。可以手动推进时间。
 */
class TestBeijingClock(
    private var fixedInstant: Instant,
) : BeijingClock {
    override val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val _dateFlow = MutableStateFlow(
        fixedInstant.atZone(zone).toLocalDate()
    )
    override fun dateFlow(): Flow<LocalDate> = _dateFlow.asStateFlow()

    override fun now(): Instant = fixedInstant

    fun setInstant(instant: Instant) {
        fixedInstant = instant
        _dateFlow.value = instant.atZone(zone).toLocalDate()
    }

    /** 推进指定时间量 */
    fun advance(duration: java.time.Duration) {
        setInstant(fixedInstant.plus(duration))
    }
}
