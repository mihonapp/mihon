import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    @Suppress("UnstableApiUsage")
    android {
        namespace = "eu.kanade.tachiyomi.source"
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-proguard.pro")
            }
        }

        // TODO(antsy): Remove when https://youtrack.jetbrains.com/issue/KT-83319 is resolved
        withHostTest { }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    dependencies {
        api(libs.kotlinx.serialization.json)
        api(libs.injekt)
        api(libs.rxJava)
        api(libs.jsoup)

        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.compose.runtime)
    }

    sourceSets {
        androidMain {
            dependencies {
                implementation(projects.core.common)
                api(libs.androidx.preference)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
