plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// core 是纯 Kotlin/JVM，不碰任何 Android API。
// 目的：解析、时区换算、同步策略这些最容易出错的逻辑能在 PC 上跑单测，
// 不用每次都往手表上装包（无线 adb 一轮要好几分钟）。
kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.test {
    useJUnitPlatform()

    // ./gradlew :core:test -Plive=true  会额外跑真打线上 API 的 LiveApiTest
    val live = providers.gradleProperty("live").orNull ?: "false"
    inputs.property("live", live)
    systemProperty("ww.live", live)

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
