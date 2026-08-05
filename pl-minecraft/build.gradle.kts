@file:Suppress("AvoidDuplicateDependencies")

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.rpc)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.shadow)
    alias(libs.plugins.minotaur)
}

base {
    archivesName = "PeerLink-Fabric"
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
    val platformClassifier = getTargetPlatformClassifier()

    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.language.kotlin)

    implementation(project(":pl-common"))
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.webrtc.java.slim)
    implementation(variantOf(libs.webrtc.java.slim) { classifier(platformClassifier) })

    testImplementation(kotlin("test"))

    shadow(project(":pl-common"))
    shadow(libs.kotlinx.rpc.krpc.ktor.client)
    shadow(libs.ktor.client.cio)
    shadow(libs.webrtc.java.slim)

    if (project.hasProperty("allLibrary")) {
        listOf(
            "linux-x86_64", "linux-aarch64",
            "windows-x86_64", "windows-aarch64",
            "macos-x86_64", "macos-aarch64"
        ).forEach { classifier -> shadow(variantOf(libs.webrtc.java.slim) { classifier(classifier) }) }
    } else shadow(variantOf(libs.webrtc.java.slim) { classifier(platformClassifier) })
}

tasks.processResources {
    val version = version
    val projectName = rootProject.name
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.shadow.get())
    dependencies {
        exclude(dependency("org.intellij.lang:annotations:.*"))
        exclude(dependency("org.jetbrains:annotations:.*"))
        exclude(dependency("org.slf4j:.*"))
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-.*"))
        exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-.*"))
        exclude(dependency("org.jetbrains.kotlinx:kotlinx-io-.*"))
    }

    exclude("org/intellij/lang/annotations/**")
    exclude("org/jetbrains/annotations/**")
    exclude("org/slf4j/**")
    exclude("kotlin/**")
    exclude("kotlinx/coroutines/**")
    exclude("kotlinx/io/**")
    exclude("kotlinx/serialization/**")
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/kotlin/**")

    archiveClassifier.set(
        if (project.hasProperty("allLibrary")) "all-platforms"
        else getTargetPlatformClassifier()
    )
}

fun getTargetPlatformClassifier(): String {
    if (project.hasProperty("targetPlatform")) return project.property("targetPlatform").toString()
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    val arch = DefaultNativePlatform.getCurrentArchitecture()
    val osName = when {
        os.isWindows -> "windows"
        os.isLinux -> "linux"
        os.isMacOsX -> "macos"
        else -> error("Unsupported Operating System: ${os.displayName}")
    }
    val archName = when {
        arch.isAmd64 -> "x86_64"
        arch.isArm64 -> "aarch64"
        arch.isArm32 || arch.name.startsWith("armv7") || arch.name.startsWith("armv8_32") -> "aarch32"
        else -> error("Unsupported Architecture: ${arch.name}")
    }
    return "$osName-$archName"
}

modrinth {
    token = System.getenv("MODRINTH_TOKEN")
    projectId = "9VVdLpMT"
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(libs.versions.minecraft.get())
    dependencies {
        required.project("fabric-api")
        required.project("fabric-language-kotlin")
    }

    syncBodyFrom = rootProject.file("README.md").readText()
}

tasks.modrinth {
    dependsOn(tasks.modrinthSyncBody)
}

tasks.test {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}