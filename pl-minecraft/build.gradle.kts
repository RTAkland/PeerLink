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

loom {
    accessWidenerPath = file("src/main/resources/peerlink.accesswidener")
}

dependencies {
    implementation(project(":pl-common"))
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.webrtc.java)
    implementation(variantOf(libs.webrtc.java) { classifier("windows-x86_64") })

    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.language.kotlin)
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }

    from("LICENSE") {
        rename { "${it}_${rootProject.name}" }
    }
}