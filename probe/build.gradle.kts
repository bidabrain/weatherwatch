plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.weatherwatch.probe"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.weatherwatch.probe"
        minSdk = 30
        targetSdk = 31
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 探针刻意保持零依赖：只用 android.jar + kotlin-stdlib，
// 这样 assembleDebug 失败一定是工具链问题，不会是依赖解析问题。
dependencies { }
