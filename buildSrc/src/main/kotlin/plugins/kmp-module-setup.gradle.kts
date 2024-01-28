package plugins

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.android.library")
    id("tachiyomi.lint")
    kotlin("multiplatform")
}

android {
    compileSdk = AndroidConfig.compileSdk
    defaultConfig {
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            version = AndroidConfig.ndk
        }
    }

    compileOptions {
        sourceCompatibility = AndroidConfig.javaVersion
        targetCompatibility = AndroidConfig.javaVersion
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = AndroidConfig.javaVersion.toString()
            }
        }
    }
}

val libs = the<LibrariesForLibs>()
dependencies {
    coreLibraryDesugaring(libs.desugar)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
