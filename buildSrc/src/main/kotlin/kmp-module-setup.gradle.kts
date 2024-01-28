plugins {
    id("com.android.library")
    id("tachiyomi.lint")
    id("base-setup")
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
