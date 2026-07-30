import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.rpc)
}

kotlin {
    jvm { compilerOptions.jvmTarget = JvmTarget.JVM_1_8 }
    mingwX64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.rpc.krpc.ktor.core)
            api(libs.kotlinx.rpc.krpc.serialization.json)
            api(libs.klogging)
        }
    }
}