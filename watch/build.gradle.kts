import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名密钥按优先级解析：
//   1. WW_KEYSTORE 环境变量（CI 把 Secret 解码到这里）
//   2. 仓库根的 keystore/debug.keystore（本地可选放置，已 gitignore）
//   3. ~/.android/debug.keystore（本机默认）
//
// 本地构建和 CI 构建必须用同一把钥匙，否则签名不一致，
// CI 产出的 APK 装不到已经装了本地包的表上。
//
// 注意用 File 而不是 java.io.File：Kotlin DSL 里 `java` 被 Gradle 的
// java 扩展访问器遮蔽，写全限定名反而解析不了。
val signingKeystore: File? = sequenceOf(
    System.getenv("WW_KEYSTORE")?.let { File(it) },
    rootProject.file("keystore/debug.keystore"),
    File(System.getProperty("user.home"), ".android/debug.keystore"),
).filterNotNull().firstOrNull { it.isFile }


android {
    namespace = "com.weatherwatch.watch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.weatherwatch"
        minSdk = 30
        // 刻意停在 31：targetSdk 再往上会触发 Android 12+ 对精确闹钟和
        // 前台服务的限制，sideload 场景里只有坏处。
        targetSdk = 31
        // CI 用 run number 递增；本地默认 1。
        // 装过 CI 包之后要用本地包覆盖，需要同样设 WW_VERSION_CODE。
        versionCode = System.getenv("WW_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1." + (System.getenv("WW_VERSION_CODE") ?: "0")
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("shared") {
                storeFile = signingKeystore
                // Android 调试密钥的固定口令，不是机密
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            // debug 也用同一把钥匙，这样 debug 和 release 可以互相覆盖安装
            signingKeystore?.let { signingConfig = signingConfigs.getByName("shared") }
        }
        release {
            // 18MB 的未混淆 Compose dex 在手表上太重，release 必须开 R8
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 个人 sideload：复用调试密钥，装机时可直接覆盖，不搞正式 keystore
            signingKeystore?.let { signingConfig = signingConfigs.getByName("shared") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // 这个 App 只 sideload 到自己的手表，不上架 Google Play。
        // targetSdk 停在 31 是刻意的（见 defaultConfig 注释），不是疏忽。
        disable += "ExpiredTargetSdkVersion"
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.7.3")
    implementation("androidx.compose.foundation:foundation:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(kotlin("test"))
    // 刻意不引 material / material3：其组件按手机尺寸设计，
    // 在 186dp 宽的表盘上不可用，而且会让 APK 大一圈。
}
