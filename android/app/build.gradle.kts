import java.util.Properties

plugins {
    // AGP 9 起内置 Kotlin 支持，不再需要单独应用 org.jetbrains.kotlin.android
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    // 截图测试：在这台电脑上把界面渲染成 PNG，不用插手机也不用开模拟器。
    // 加它的原因是很实际的——2026-08-06 那轮改了今日页和进度页，
    // 单测全绿、构建成功、包也出了，但界面到底长什么样谁都没看见，
    // 只能在交接文档里写「还得你装上看一眼」。界面质量不该由用户肉眼兜底。
    alias(libs.plugins.compose.screenshot)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ── 版本与名字的单一真相 ────────────────────────────────────────────────
// 这三个值是全工程唯一的出处：APP 显示名、设置页念的版本号、releases/ 里的文件名
// 全部从这里生成，任何一处都不许再手写。
//
// ⚠️ 为什么要做成这样（别改回去）：
// 2026-08-06 出过一次事故，文件名写着 v0.3、包里却是 0.2。当时只改了数字，
// 没做下面那个出包任务，于是 0.4、0.4.1、0.5、0.5.1、0.5.2 五个包又全是手打的文件名，
// 而 gradle 里始终停在 0.3 —— 用户装上 v0.5.2，设置页一直念「夜读 v0.3 (3)」，
// 他因此怀疑装的是不是旧包。设置页的版本号是「手机上跑的到底是不是当前源码」
// 唯一的回答方式，它一旦不可信，后面所有真机验收都失去基准。
val appVersionName = "0.8"
val appVersionCode = 8
val appDisplayName = "夜读"

// Release 签名只从本机未跟踪的配置读取，密钥和密码永远不进入 Git 历史。
// 公开仓库没有该文件时仍可构建 debug 与 unsigned release；本机保留文件时照常签名出包。
val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !signingProperties.getProperty(it).isNullOrBlank() }

// ⚠️ 工程路径必须保持纯 ASCII（2026-08-07 起目录已从「252原子任务学习进度」改名为 study-board）。
// 别再搬回含中文的路径：Compose 截图测试插件在中文路径下至少有两处独立的 bug——
//   ① 渲染器找 apk-for-local-test.ap_ 时把 UTF-8 字节按 Latin-1 解，报 NoSuchFileException
//      （文件其实就在那儿）
//   ② 写参考图时会凭空造出一个乱码名字的兄弟目录（当时真在 D:\AI_code\APP 下建出来了）
// 当时试过「只把构建产物挪到 ASCII 路径」绕，②仍然复现，所以只能从路径本身根治。
// 顺带记一条：那个绕行方案的构建目录还必须跟工程同盘符，
// 否则插件算相对路径时抛 IllegalArgumentException: different roots。

android {
    namespace = "com.zyj.ritual"
    // 编译用 37（当前 AndroidX 要求），运行目标定 36 = 你手机的 Android 16
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zyj.ritual"
        minSdk = 26
        targetSdk = 36
        // 版本号与显示名都来自文件顶部的单一真相，这里不许写字面量。
        versionCode = appVersionCode
        versionName = appVersionName
        // app_name 由这里生成，strings.xml 里没有也不许再加，否则会重复定义。
        // 这样「桌面图标上的名字」和「APK 文件名」不可能各改各的。
        resValue("string", "app_name", appDisplayName)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 截图测试目前还是实验特性，插件和这个开关都要打开（gradle.properties 里还有一份）
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    buildFeatures {
        compose = true
        // 设置页要显示版本号，用来确认手机上装的是不是当前源码构建出来的包
        buildConfig = true
        // app_name 由 defaultConfig 的 resValue 生成（见文件顶部 appDisplayName）。
        // AGP 9 起这个开关默认是关的，不打开会报「contains custom resource values, but the feature is disabled」。
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Robolectric 要能读到 App 的资源和 manifest，少这一行它起不来
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// ── 出包 ────────────────────────────────────────────────────────────────
// 唯一允许的出包方式：./gradlew packDebugApk / packReleaseApk
// 文件名由上面的 appDisplayName + appVersionName 拼出来，人手碰不到，
// 所以「文件名写 0.5.2、包里却是 0.3」这类事故在结构上就不可能再发生。
// 别再用 assembleRelease 然后自己往 releases/ 里拷贝改名。
listOf("Debug", "Release").forEach { variantCap ->
    val variant = variantCap.lowercase()
    // ⚠️ 名字必须在这里就拼成普通字符串。如果把 appDisplayName / appVersionName 留到
    // 下面的 rename / doLast 闭包里再读，闭包就会捕获整个构建脚本对象，
    // 配置缓存序列化不了，报 "cannot serialize Gradle script object references"。
    val outputName = "$appDisplayName-v$appVersionName-$variant.apk"
    val settingsLine = "$appDisplayName v$appVersionName ($appVersionCode)"
    tasks.register<Copy>("pack${variantCap}Apk") {
        group = "ritual"
        description = "构建 $variant 包，按 gradle 里的版本号命名后放进 ../releases/"
        dependsOn("assemble$variantCap")
        from(layout.buildDirectory.dir("outputs/apk/$variant")) { include("*.apk") }
        into(rootProject.layout.projectDirectory.dir("../releases"))
        rename { outputName }
        doLast {
            logger.lifecycle("出包完成：$outputName")
            logger.lifecycle("装机后设置页应当念：$settingsLine —— 对不上就是装错包了")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // 协程。必须显式钉住版本：传递依赖只带到 1.9.0，
    // 而 kotlinx-coroutines-test 是 1.11.0，两边不一致会让真机测试
    // 直接抛 NoSuchMethodError: runBlockingK（app 的旧 dex 覆盖了测试包的新 API）。
    implementation(libs.kotlinx.coroutines.android)

    // 序列化
    implementation(libs.kotlinx.serialization.json)

    // WebDAV 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 协程测试
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    testImplementation(libs.junit)

    // Robolectric：让 Room 在这台电脑上真跑起来，不用插手机。
    // 只进测试不进 APK。为什么需要它见 libs.versions.toml 里那段注释。
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)

    // 截图测试。src/screenshotTest/ 是独立源集，看不见 test/ 和 main 的测试辅助类，
    // 造数据要在它自己那边写一份。
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
