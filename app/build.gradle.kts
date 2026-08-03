import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 仓库内自带的固定签名密钥。存在就用它签名，保证每次云端编译出的 APK
// 签名一致，手机可以直接覆盖安装升级；万一缺失则退回 AGP 的临时 debug 密钥。
val sharedKeystore = rootProject.file("keystore/soundbox.p12")

android {
    namespace = "com.soundbox.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soundbox.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("zh", "en")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("shared") {
            if (sharedKeystore.exists()) {
                storeFile = sharedKeystore
                storeType = "PKCS12"
                storePassword = "soundbox"
                keyAlias = "soundbox"
                keyPassword = "soundbox"
            }
        }
    }

    buildTypes {
        val signing = if (sharedKeystore.exists()) {
            signingConfigs.getByName("shared")
        } else {
            signingConfigs.getByName("debug")
        }

        debug {
            signingConfig = signing
            isMinifyEnabled = false
        }

        release {
            // 关闭 R8 压缩，保证云端编译出的 APK 行为稳定（Compose + Media3 在最严格 R8
            // 模式下有极小概率被误删类）。对个人工具型 App，包体稍大但更稳。
            signingConfig = signing
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.version",
                "kotlin/**",
                "DebugProbesKt.bin"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    // 如需 MIDI (.mid/.midi) 播放支持，取消下面一行的注释即可（会增大约 1MB）
    // implementation("androidx.media3:media3-exoplayer-midi:1.5.1")

    debugImplementation(libs.androidx.ui.tooling)
}
