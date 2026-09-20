plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "mihon.core.navigation"
}

dependencies {
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(libs.bundles.androidx.navigation)

    api(libs.composeMaterialMotion)
    api(libs.androidx.compose.ui)
}
