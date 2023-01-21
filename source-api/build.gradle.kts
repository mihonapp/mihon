plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFile("consumer-proguard.pro")
    }

}

dependencies {

    implementation(project(":core"))

    api(kotlinx.serialization.json)

    api(libs.rxjava)

    api(libs.preferencektx)

    api(libs.jsoup)

    implementation(androidx.corektx)
}
