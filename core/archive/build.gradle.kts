plugins {
    id("nekotachi.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "nekotachi.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.libarchive)
    implementation(libs.unifile)
}
