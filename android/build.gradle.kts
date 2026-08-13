plugins {
    // AGP 9 内置 Kotlin，不要再声明 org.jetbrains.kotlin.android，否则构建直接失败
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
