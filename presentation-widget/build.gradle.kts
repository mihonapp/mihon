plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)

    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
}

android {
    namespace = "tachiyomi.presentation.widget"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.i18n)

    implementation(libs.androidx.glance.appWidget)
    implementation(libs.material)

    implementation(libs.coil.core)

    implementation(libs.metro.runtime)
}
