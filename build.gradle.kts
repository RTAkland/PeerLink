plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.rpc) apply false
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.shadow) apply false
}

subprojects {
    group = "cn.rtast.peerlink"
    version = providers.gradleProperty("modVersion").get()

    repositories {
        mavenCentral()
        maven("https://repo.maven.rtast.cn/releases")
    }
}