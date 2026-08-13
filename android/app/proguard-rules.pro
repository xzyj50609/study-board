# Kotlin serialization：BackupFormat 及其序列化模型需要保留 serializer 入口。
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class **$serializer { *; }
-keepclassmembers class com.zyj.ritual.data.backup.** { *; }
-keep @kotlinx.serialization.Serializable class ** { *; }

# Room：保留数据库、Entity、DAO 的注解和生成实现。
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class **_Impl { *; }
-keep class com.zyj.ritual.data.local.** { *; }

# Compose/Material 通过正常调用链引用，不需要全局 keep；保留 Kotlin metadata 供序列化和反射诊断。
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.serialization.**
-dontwarn androidx.room.**
