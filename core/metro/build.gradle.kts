plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.detekt)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
}

android {
    namespace = "mihon.core.metro"
}

dependencies {
    implementation(libs.metro.runtime)
}
