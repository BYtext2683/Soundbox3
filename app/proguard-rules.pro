# ---- 基础 ----
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Media3 / ExoPlayer ----
# Media3 各 AAR 自带 consumer rules，这里只补充反射入口。
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keep class androidx.media3.session.MediaSessionService { *; }
-keep class com.soundbox.player.playback.MusicService { *; }

# ExoPlayer 通过反射查找可选扩展解码器，找不到时会静默降级，这里屏蔽警告
-dontnote androidx.media3.**
-dontwarn androidx.media3.**

# ---- Compose ----
# Compose 编译器与运行时自带规则，无需额外配置。

# ---- 应用自身 ----
-keep class com.soundbox.player.App { *; }
-keep class com.soundbox.player.MainActivity { *; }
