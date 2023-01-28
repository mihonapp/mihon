plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "tachiyomi.presentation.core"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {

}
