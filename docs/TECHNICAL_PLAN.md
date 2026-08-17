# TECHNICAL_PLAN · 技术方案

配套文档：`REQUIREMENTS.md`（需求）、`DESIGN_AUDIT.md`（设计审查）、`OPEN_QUESTIONS.md`（待裁决）

## 1. 技术栈

你在说明书第八节列的方向我全部认可，逐条评估如下，只有两处改动。

| 方向 | 结论 | 说明 |
|---|---|---|
| Kotlin | 采用 | |
| Jetpack Compose | 采用 | 这套设计（自定义进度环、9 态日历格、大量非标准间距）用 Compose 比 XML 省一半代码 |
| Material 3 | **只用它的组件能力，不用它的配色** | `ModalBottomSheet`、`AlertDialog`、`NavigationBar` 的行为直接用；`MaterialTheme` 的 ColorScheme 全部替换成 design-tokens 的值，另建 `RitualTheme` 承载 token，不让 M3 默认色泄漏到任何一个像素 |
| Navigation Compose | 采用 | 用 type-safe 路由（`@Serializable` 对象），不用字符串拼接 |
| Room | 采用 | 记录、历史事件两张表 |
| DataStore | 采用 | Plan（计划参数）与设置，Proto 太重，用 Preferences DataStore + 手写序列化 |
| 通知 + WorkManager | **第一版不做**（2026-08-06 定） | 不引入通知权限、不引入 WorkManager/AlarmManager，也不加任何后台组件。备注：将来若要做，用 `AlarmManager.setExactAndAllowWhileIdle` 而不是 WorkManager——后者最短周期 15 分钟且允许系统合并延迟，定点提醒会漂 |
| java.time + Asia/Shanghai | 采用 | minSdk 26 即可原生支持，不需要 desugaring |
| 单向数据流 / ViewModel / Repository / Coroutines / Flow | 采用 | 见第 5 节 |

补充选型：

- **minSdk 26 / compileSdk 37 / targetSdk 36**。你的手机是一加 13（Android 16 = API 36），所以运行目标定 36；编译用 37 是被迫的——当前的 AndroidX 库（core-ktx 1.19 等）已经要求 compileSdk 37 + AGP 9.1 以上，编译版本高于目标版本是正常且安全的。minSdk 26 只是为了省事，不影响你。
- 版本（2026-08-06 实际构建通过的组合，锁在 `libs.versions.toml`）：Gradle **9.6.1**、AGP **9.3.1**、Kotlin 2.3.21、KSP 2.3.11、Compose BOM 2026.06.01、Room 2.8.4、Navigation Compose 2.9.8、DataStore 1.2.1。JDK 用本机已有的 17。
- **AGP 9 的两个坑**（我第一次按 AGP 8.13.2 配，撞了才改过来，记在这里免得以后重犯）：
  1. AGP 9.x 要求 Gradle ≥ 9.5.0，8.x 的 Gradle 直接拒绝加载。
  2. **AGP 9 内置 Kotlin 支持，不能再声明 `org.jetbrains.kotlin.android` 插件**，声明了会报 "no longer required for Kotlin support since AGP 9.0" 并构建失败。`org.jetbrains.kotlin.plugin.compose` 仍然要单独声明。
- **工程路径必须是纯 ASCII**（2026-08-07 起目录名从 `252原子任务学习进度` 改为 `study-board`）。此前靠 `gradle.properties` 里的 `android.overridePathCheck=true` 让 AGP 放行中文路径，`assembleDebug` 确实能过；但接 Compose 截图测试插件时暴露出中文路径下**两处独立的 bug**——渲染器把路径按 Latin-1 解出乱码找不到文件，写参考图时还会凭空建出一个乱码名的兄弟目录。绕不干净，所以改名根治，`overridePathCheck` 一并删掉。⚠️ 别把工程搬回含中文或空格的路径。
- **依赖注入不用 Hilt**。这个 App 只有 4 个页面、1 个 Repository、1 个数据库，Hilt 带来的 KSP 编译开销和样板代码不划算。用一个手写的 `AppContainer`（Application 持有，ViewModel 通过 `viewModelFactory` 拿），30 行搞定。如果你更希望用 Hilt（比如打算长期扩展），说一声，换过去成本也不高。
- **测试**：JUnit5 + kotlin-test（纯逻辑）、Turbine（Flow）、Room 的 in-memory 数据库（DAO 测试）、Compose UI Test（关键交互）。
- **不引入的东西**：任何网络库、任何图片库、任何分析/崩溃上报 SDK、任何第三方 UI 库。

**字体方案（重要）**：
- Manrope（300/500）和 IBM Plex Mono（400/500）打包进 `res/font/`，**只保留拉丁字符子集**，两者合计约 150 KB。它们承担所有数字、英文标签、时间戳。
- 中文用系统默认 sans-serif，通过 `FontWeight.Light/Normal/Medium` 取字重。国产机型的系统中文字体与思源黑体同源，观感差异很小。
- 不打包 Noto Sans SC 完整字重（单个字重 8–10 MB，三个字重会让 APK 超过 25 MB）。
- 如果实机看下来中文字重差异明显，再考虑用 pyftsubset 对 Noto Sans SC 做常用字子集化——**那样的话每次改文案都必须重跑子集脚本，否则手机上会静默缺字（电脑上看不出来）**。

## 2. 项目目录结构

安卓工程放在 `android/` 子目录，与设计交付完全隔离，不动交付文件一个字节。

```
study-board/
├── 交接说明.md / design-tokens.json / *.dc.html / assets/     ← Claude Design 交付，只读
├── DESIGN_AUDIT.md / REQUIREMENTS.md / OPEN_QUESTIONS.md
├── TECHNICAL_PLAN.md / IMPLEMENTATION_PLAN.md
├── dev/prototype-screens/                                     ← 原型逐屏截图（开发对照用）
└── android/
    ├── settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml
    └── app/src/
        ├── main/java/com/zyj/ritual/
        │   ├── RitualApp.kt                 Application + AppContainer
        │   ├── MainActivity.kt              edge-to-edge + NavHost 宿主
        │   │
        │   ├── core/
        │   │   ├── time/BeijingClock.kt     唯一的"今天"来源
        │   │   └── ext/                     格式化（百分比两位小数、08.04 22:14）
        │   │
        │   ├── domain/                      ★ 纯 Kotlin，零 Android 依赖，全部可单测
        │   │   ├── model/                   Article, TaskId, TaskRecord, Plan, DayStatus…
        │   │   ├── PlanCalendar.kt          计划日 ↔ (篇, 相位) 映射（休息日扩展点）
        │   │   ├── ProgressCalculator.kt    整体进度 / 完整篇数 / 当前篇 / 当前篇进度
        │   │   ├── CreditCalculator.kt      额度、缺额、最早缺口日、预计完成日
        │   │   ├── DayStatusResolver.kt     日历格 9 态判定（R26–R30）
        │   │   └── TodayCopyResolver.kt     首页标题与主按钮文案（R31–R38）
        │   │
        │   ├── data/
        │   │   ├── db/                      RitualDatabase, TaskRecordDao, HistoryDao, Entity
        │   │   ├── prefs/PlanStore.kt       DataStore 里的 Plan 与设置
        │   │   └── repo/StudyRepository.kt  唯一对外数据入口，暴露 Flow
        │   │
        │   ├── ui/
        │   │   ├── theme/                   Color.kt Type.kt Dimens.kt Motion.kt RitualTheme.kt
        │   │   ├── component/               TaskRow, ProgressRing, CalendarCell, CreditCard,
        │   │   │                            PrimaryPill, StatusChip, SegmentedTicks…
        │   │   ├── nav/RitualNavHost.kt     路由与返回键
        │   │   ├── setup/    SetupScreen.kt    + SetupViewModel.kt   （无 notify/ 目录）
        │   │   ├── today/    TodayScreen.kt    + TodayViewModel.kt   （含 ArticleScreen）
        │   │   ├── calendar/ CalendarScreen.kt + CalendarViewModel.kt（含 DaySheet）
        │   │   ├── progress/ ProgressScreen.kt + ProgressViewModel.kt
        │   │   ├── settings/ SettingsScreen.kt + SettingsViewModel.kt
        │   │   └── done/     DoneScreen.kt
        │
        ├── test/            JVM 单元测试（domain 层为主）
        └── androidTest/     Room DAO 测试 + Compose UI 测试
```

**为什么 domain 层要完全不依赖 Android**：这个 App 的难点全在算术上（额度、逾期、跨天、重排后的重算），而不在界面。把这些抽成纯 Kotlin 函数，就能用秒级的 JVM 测试覆盖所有边界，不用等模拟器。这也是"所有重要业务逻辑都需要测试"最省力的落法。

## 3. 数据模型

### 3.1 领域模型

```kotlin
@JvmInline value class TaskId(val raw: String)   // "13-4"

data class TaskRecord(
    val id: TaskId,
    val articleIndex: Int,          // 1..42
    val taskIndex: Int,             // 1..6
    val completedAt: Instant?,      // 起点导入的记录为 null
    val plannedDate: LocalDate?,    // 完成那一刻的计划日快照，之后永不改写
    val source: RecordSource        // CHECKED / BACKFILL / IMPORTED
)

data class Plan(
    val totalArticles: Int = 42,
    val startArticle: Int,               // 第一篇要做的
    val completedBeforeStart: Int,       // 起点已学完篇数
    val planStartDate: LocalDate,
    val daysPerArticle: Int = 2,
    val studyWeekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),  // Q2，默认全选
    val reminder: Reminder? = null                                   // Q1
)

enum class DayStatus { BEFORE_PLAN, TODAY, ON_TIME, PARTIAL, LATE_MADE_UP,
                       EARLY, CREDIT_COVERED, MISSING, PLANNED /*, REST_DAY */ }
```

### 3.2 起点那 12 篇怎么存（一个必须先定下来的细节）

原型里用 `baseArts = 11/12` 这样一个"基数"表示已学完的篇，它们不产生任何记录。这在数据库里会造成两种"已完成"——有记录的和没记录的——导致进度计算、撤销、42 格矩阵每处都要写分支，`fullArts()` 和 `totalDone()` 里就能看到这种别扭。

**方案：首次设置时为起点的 12 篇生成 72 条 `source = IMPORTED` 的记录，`completedAt = null`、`plannedDate = null`。**

这样全 App 只有一条规则：**一条记录 = 一个已完成的小任务**。进度、完整篇数、撤销、矩阵全部统一成对记录的计数，没有分支。代价只有两处，都很好处理：

- 历史记录列表过滤掉 `IMPORTED`（它们没有真实时间，不该出现在时间轴上）
- 额度计算只统计 `completedAt != null && completedAt.date >= planStartDate` 的记录（R15）

改起点篇数时，同步增删这批 IMPORTED 记录，但**绝不碰用户真实勾过的记录**。

### 3.3 Room 表

```
task_record(id TEXT PK, article INT, task INT, completed_at INTEGER?,
            planned_date TEXT?, source TEXT)
    index(article), index(completed_at)

history_event(id INTEGER PK AUTO, at INTEGER, type TEXT,
              article INT?, task INT?, note TEXT)
    index(at DESC)
```

Plan 放 DataStore 而不是 Room：它是单例配置、字段少、要在 App 启动最早期读到（决定 startDestination 是 setup 还是 today）。

### 3.4 派生量一律不落库

整体进度、完整篇数、当前篇、额度、缺额、最早缺口、预计完成日、每个日历格的状态——全部由 `records + plan + 今天` 现算。原因：只要落库就有失同步的风险，而任何一次撤销、补打卡、重排都要求全量重算（你说明书第七节的硬要求）。这些计算的输入规模是 252 条记录级别，现算的开销可以忽略。

## 4. 页面 ↔ ViewModel

| 页面 | ViewModel | 状态来源 | 主要动作 |
|---|---|---|---|
| `SetupScreen` | `SetupViewModel` | 本地表单状态 + 现有 Plan | 加减起点、选节奏、选开始日、预览剩余天数、保存并生成 IMPORTED 记录 |
| `TodayScreen` | `TodayViewModel` | `combine(records, plan, todayFlow)` | 勾选 / 请求撤销 / 优先补上 / 主按钮 / 跳文章详情 |
| `ArticleScreen` | 复用 `TodayViewModel` | 同上，取当前篇 6 项 | 勾选 / 撤销 |
| `CalendarScreen` + `DaySheet` | `CalendarViewModel` | `combine(records, plan, todayFlow, 当前月)` | 切月 / 开面板 / 面板内勾选与撤销 / 全部标记完成 |
| `ProgressScreen` | `ProgressViewModel` | `combine(records, plan, todayFlow, history)` | 重新排期 / 查看历史 |
| `SettingsScreen` | `SettingsViewModel` | `plan` | 改节奏 / 重进向导 / 重排 / 撤销整篇 |
| `DoneScreen` | 复用 `TodayViewModel` | 同 today | 跳进度页 |

统一形状：每个 ViewModel 暴露一个 `StateFlow<XxxUiState>`（不可变 data class），接收密封类 `XxxAction`。ViewModel 里只做「读 Flow → 调 domain 纯函数 → 拼 UiState」，不写业务算术。所有算术在 `domain/`。

`todayFlow` 是关键的一个：它发射当前的北京日期，在 App 跨过北京零点时自动发新值，让所有页面重算（R45）。

## 5. 导航

```kotlin
@Serializable object Setup
@Serializable object Today
@Serializable object Article
@Serializable object Calendar
@Serializable object Progress
@Serializable object Settings
@Serializable object Done
```

- `startDestination`：Plan 未初始化 → `Setup`，否则 → `Today`。这个判断要在首帧前拿到（DataStore 首次读是挂起的），用启动画面 API 保持 splash 到读完为止，避免闪一下 today 又跳 setup。
- 底部导航 4 个 tab，**不做 tab 内历史栈**（交接说明明确要求），根页按返回退出 App。
- `Article` 是 push，`Done` 是替换 `Today` 的内容（不是新路由也可以——但做成独立路由 + `popUpTo` 更清楚，我倾向独立路由，底栏在这个路由隐藏）。
- `DaySheet` 是 `ModalBottomSheet`，不占路由，状态在 `CalendarViewModel` 里。
- 三个确认弹窗同理，用 `AlertDialog` + ViewModel 里的 `dialogState`。

## 6. 北京时间方案

```kotlin
object BeijingClock {
    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    fun today(): LocalDate = LocalDate.now(ZONE)
    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)
    fun Instant.beijingDate(): LocalDate = atZone(ZONE).toLocalDate()
}
```

三条纪律：

1. **全 App 禁止出现 `LocalDate.now()` 的无参调用**，一律走 `BeijingClock`。这条加进代码检查清单，必要时用一条 lint 规则或者 CI 里的 grep 兜住。
2. **跨天自动刷新**：Repository 提供 `todayFlow: Flow<LocalDate>`，实现是"发射当前北京日期 → 计算到下一个北京零点的毫秒数 → delay → 再发"。ViewModel 全部 combine 它。
3. **回前台重算**：`ON_RESUME` 时让 `todayFlow` 重新发一次当前值（手机可能在息屏时跨了天，或者用户改了系统时间）。

测试时用可注入的 `Clock`，让单元测试能把"今天"钉在任意一天。

## 7. 备份导出方案（第一版做）

**零权限**是这里最关键的一点：用系统文件选择器（Storage Access Framework）就不需要任何存储权限，App 的 `AndroidManifest.xml` 里依然一条 `<uses-permission>` 都没有。

- 导出：`ActivityResultContracts.CreateDocument("application/json")` → 拿到 `Uri` → `contentResolver.openOutputStream` 写入 → 默认文件名 `夜读备份-20260806-2214.json`。
- 导入：`ActivityResultContracts.OpenDocument(["application/json"])` → 读入 → 校验 → 二次确认 → 事务内整库替换。
- 序列化用 `kotlinx.serialization`（多一个插件，但比手写 JSON 稳）。
- 备份文件结构：

```json
{
  "formatVersion": 1,
  "exportedAt": "2026-08-06T22:14:00+08:00",
  "plan": { "totalArticles": 42, "startArticle": 13, "completedBeforeStart": 12,
            "planStartDate": "2026-08-06", "daysPerArticle": 2 },
  "records": [ { "id": "13-1", "article": 13, "task": 1,
                 "completedAt": "2026-08-06T21:10:00Z", "plannedDate": "2026-08-06",
                 "source": "CHECKED" } ],
  "history": [ { "at": "...", "type": "CHECK", "article": 13, "task": 1, "note": "" } ]
}
```

- `formatVersion` 现在是 1；将来结构变了靠它决定怎么迁移，读到不认识的版本就拒绝导入而不是猜。
- **恢复是整库替换而不是合并**（R51）。合并听起来更友好，但"同一个小任务两边都完成、时间戳不同"这种冲突没有正确答案，替换的语义至少是确定的。

## 7b. 通知方案 —— 第一版不做（2026-08-06 定）

不申请任何权限、不注册广播接收器、不引入后台组件。将来若要加：`AlarmManager.setExactAndAllowWhileIdle` 每天定点触发（不是 WorkManager）+ `BOOT_COMPLETED` 重建闹钟 + Android 13 的 `POST_NOTIFICATIONS` 权限；当天已全部完成则不发。

## 8. 测试方案

**JVM 单元测试（主战场，domain 层）**，每条对应 `REQUIREMENTS.md` 的规则编号：

| 测试类 | 覆盖规则 | 关键用例 |
|---|---|---|
| `PlanCalendarTest` | R1–R5 | 第 0/1/2 个计划日的篇与相位；1 天/篇与 3 天/篇；超出 42 篇后无计划 |
| `ProgressCalculatorTest` | R6–R10 | 72/252 = 28.57%；第 6 项勾上完整篇 +1；撤销后立即 −1；当前篇跳过已满篇 |
| `CreditCalculatorTest` | R11–R18 | 当天不算欠；提前 6 项 = +2 天；空 2 天 = −6 项；最早缺口正向扫描（含跨篇）；预计完成日 |
| `DayStatusResolverTest` | R26–R30 | 9 种状态逐一；边界：今天完成 2 项 = 部分完成；未来日全做完 = 提前完成；额度由正转负时同一天的状态翻转 |
| `TodayCopyResolverTest` | R31–R38 | 四种标题；四种主按钮；逾期时主按钮指向最早缺口 |
| `RescheduleTest` | R39–R42 | 重排后额度归零、记录时间戳一条不变、计划轴从今天起 |
| `TimezoneTest` | R43–R46 | 设备时区设为 UTC-8 时"今天"仍是北京日期；跨零点重算 |

**Room DAO 测试（androidTest，in-memory）**：写入/删除幂等、IMPORTED 记录的增删、按篇查询。

**Compose UI 测试（androidTest，挑关键路径）**：
1. 勾一项 → 进度数字变化
2. 点已完成项 → 弹窗出现且文案里的回退数字正确
3. 面板补打卡 → 首页缺额减少
4. 向导保存 → 进 today 且数字正确

**手动验收**：按 `REQUIREMENTS.md` 第 9 节的 11 条走一遍真机，重点是"杀进程重开数据不丢"和"改系统时区不影响今天"。

覆盖率目标 80%+，按 domain 层算（UI 层不强求）。

## 9. 风险点

按"会不会让我返工"排序：

| 风险 | 影响 | 应对 |
|---|---|---|
| ~~Q2（休息日）中途改主意~~ | 已于 2026-08-06 定为不做，风险解除 | 算法仍收口在 `PlanCalendar` 接口后面，将来要加不动上层 |
| 首次构建要下载约 1 GB 依赖 | 阶段 0 卡住 | 环境（JDK 17 + SDK android-35）已确认可用，只差这一次下载，开工前跟你确认 |
| 手机安卓版本未知 | 若低于 8.0 要调 minSdk | 暂按 minSdk 26 做；真低了改配置 + 开 desugaring，成本很小 |
| 进度环的两段弧 | 视觉还原度 | 先单独写一个 Preview 页把 0%/28.57%/含额度/100% 四种状态画出来给你看，确认了再接进页面 |
| 中文字体观感与设计稿有差 | 视觉还原度 | 第 1 阶段末尾就出一张真机截图跟设计稿并排比对，早发现早决定要不要子集化 |
| 日历格 9 态的判定顺序 | 逻辑错误不易察觉 | 判定写成一条自上而下的短路链，配 9 个测试用例逐一钉死 |
| 系统字体放大 200% 时布局崩 | 少数场景不可用 | 用 Compose Preview 的 `fontScale` 参数在开发时就看，不等到最后 |
| Compose 重组性能（42 格矩阵 + 月历 35 格） | 卡顿 | 规模很小，正常写不会有问题；仍然给 `LazyVerticalGrid` 的 item 加稳定 key |
| 我在这份文档里写的库版本号可能过时 | 编译失败 | 已在 2026-08-06 实际构建验证过一遍，版本锁在 `libs.versions.toml` |
| AGP 9 是很新的版本，后续阶段接 Room/KSP 时可能还有内置 Kotlin 带来的配置差异 | 阶段 3 卡住 | 阶段 3 一开始就先只加 Room 依赖跑一次空构建，确认 KSP 在 AGP 9 下的接法，再写实体类 |
