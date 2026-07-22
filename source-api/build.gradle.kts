plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.source"
}

dependencies {
    implementation(projects.core.common)

    api(libs.kotlinx.serialization.json)
    api(libs.injekt)
    api(libs.rxJava)
    api(libs.jsoup)
    api(libs.androidx.preference)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
