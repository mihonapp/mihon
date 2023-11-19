plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":source-api"))
                api(project(":i18n"))

                implementation(libs.unifile)
                implementation(libs.junrar)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(project(":core-metadata"))

                // Move ChapterRecognition to separate module?
                implementation(project(":domain"))

                implementation(kotlinx.bundles.serialization)
            }
        }
    }
}

android {
    namespace = "tachiyomi.source.local"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
