plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.rpc)
}

kotlin {
    listOf(
        mingwX64(),
        linuxX64()
    ).forEach { it.binaries { executable { entryPoint = "cn.rtast.peerlink.signaling.main" } } }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":pl-common"))
            implementation(libs.kotlinx.rpc.krpc.ktor.server)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.io)
        }

        linuxX64Main.dependencies {
            implementation(libs.ktor.client.curl)
        }

        mingwX64Main.dependencies {
            implementation(libs.ktor.client.winhttp)
        }
    }
}