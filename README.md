# Study Board / 夜读

一个离线优先的 Android 学习看板，使用 Kotlin 与 Jetpack Compose 构建。

当前包含两条学习线：

- 阅读文章：42 篇文章，每篇拆成 6 个可勾选任务；
- 背单词：记录新词与复习量，计算计划、预计完成时间和复习里程碑。

项目使用 Room、DataStore 和北京时间统一管理本地状态，并支持 JSON 与坚果云 WebDAV 备份。

## 本地构建

在 `android/` 目录运行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Release 签名不进入仓库。需要本地打包时，将 `keystore.properties.example` 复制为
`keystore.properties`，填写本地 keystore 信息，再运行：

```powershell
.\gradlew.bat packReleaseApk
```

## 数据与隐私

个人学习记录、手机导出的备份、历史 APK、签名密钥和本机配置均被明确排除在版本控制之外。

