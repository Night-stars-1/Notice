-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# libxposed 的 service/api 由框架通过 binder 与反射对接，整体保留（体积很小）
-keep class io.github.libxposed.** { *; }

# 崩溃堆栈保留行号，方便看运行日志里的异常
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
