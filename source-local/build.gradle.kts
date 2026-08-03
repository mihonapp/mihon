plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "tachiyomi.source.local"
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.i18n)

    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.domain)

    implementation(libs.unifile)
    implementation(libs.bundles.serialization)

    implementation(libs.injekt)
    implementation(libs.jsoup)
}
