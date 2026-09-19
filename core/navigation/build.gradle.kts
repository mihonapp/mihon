plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "mihon.core.navigation"
}

dependencies {
    implementation(projects.domain)
    implementation(libs.bundles.androidx.navigation)

    api(libs.androidx.compose.animationGraphics)
    api(libs.androidx.compose.ui)
}
