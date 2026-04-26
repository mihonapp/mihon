plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)

    api(libs.logcat)

    api(libs.rxJava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsOverHttps)
    api(libs.okio)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.archive)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.jsonOkio)

    api(libs.androidx.preference)

    implementation(libs.jsoup)

    // Sort
    implementation(libs.natural.comparator)

    // JavaScript engine
    implementation(libs.quickJs)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
