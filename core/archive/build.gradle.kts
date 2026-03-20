plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "mihon.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.archive)
    implementation(libs.unifile)
}
