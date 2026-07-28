import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.rpc)
    alias(libs.plugins.fabric.loom)
}

tasks.compileKotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_25
}

val javaVersion = "25"

tasks.compileJava {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(project(":pl-common"))
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.webrtc.java)
    implementation(variantOf(libs.webrtc.java) { classifier("windows-x86_64") })

    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.language.kotlin)
}

tasks.processResources {
    inputs.property("version", version)
    inputs.property("minecraft_version", libs.versions.minecraft)
    inputs.property("loader_version", libs.versions.fabricLoader)
    inputs.property("java_version", javaVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
        expand("minecraft_version" to libs.versions.minecraft)
        expand("loader_version" to libs.versions.fabricLoader)
        expand("java_version" to javaVersion)
    }

    from("LICENSE") {
        rename { "${it}_${rootProject.name}" }
    }
}