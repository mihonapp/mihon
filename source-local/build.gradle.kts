plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "tachiyomi.source.local"

    defaultConfig {

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {

    implementation(project(":source-api"))
    implementation(project(":core"))
    implementation(project(":core-metadata"))

    // Move ChapterRecognition to separate module?
    implementation(project(":domain"))

    implementation(kotlinx.bundles.serialization)

    implementation(libs.unifile)
    implementation(libs.junrar)
}
