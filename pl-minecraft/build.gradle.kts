import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.rpc)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.shadow)
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
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.language.kotlin)

    implementation(project(":pl-common"))
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.webrtc.java.slim)
    implementation(variantOf(libs.webrtc.java.slim) { classifier("windows-x86_64") })

    shadow(project(":pl-common"))
    shadow(libs.kotlinx.rpc.krpc.ktor.client)
    shadow(libs.ktor.client.cio)
    shadow(libs.webrtc.java.slim)
    shadow(variantOf(libs.webrtc.java.slim) { classifier("windows-x86_64") })
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
}