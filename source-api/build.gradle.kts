plugins {
    id("plugins.kmp-module-setup")
    kotlin("plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.source"
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(libs.injekt.core)
                api(libs.rxjava)
                api(libs.jsoup)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.common)
                api(libs.preferencektx)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xexpect-actual-classes",
        )
    }
}
