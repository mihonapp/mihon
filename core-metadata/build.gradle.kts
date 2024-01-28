plugins {
    id("android-module-setup")
    kotlin("plugin.serialization")
}

android {
    namespace = "tachiyomi.core.metadata"
}

dependencies {
    implementation(projects.sourceApi)

    implementation(kotlinx.bundles.serialization)
}
