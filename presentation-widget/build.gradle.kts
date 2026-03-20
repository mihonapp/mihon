plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
}

android {
    namespace = "tachiyomi.presentation.widget"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    api(projects.i18n)

    implementation(libs.androidx.glance.appWidget)
    implementation(libs.material)

    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.kotlinx.datetime)

    implementation(libs.coil.core)

    api(libs.injekt)
}
