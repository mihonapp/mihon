import gradle.kotlin.dsl.accessors._624aae704a5c30b505ab3598db099943.coreLibraryDesugaring
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.library")
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
