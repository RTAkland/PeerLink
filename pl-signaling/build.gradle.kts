import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.rpc)
    alias(libs.plugins.shadow)
}

tasks.compileKotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

dependencies {
    implementation(project(":pl-common"))
    implementation(libs.kotlinx.rpc.krpc.ktor.server)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.client.cio)
}