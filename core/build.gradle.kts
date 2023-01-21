plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.core"
}

dependencies {
    implementation(project(":i18n"))

    api(libs.logcat)

    api(libs.rxjava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okio)

    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    api(kotlinx.serialization.json.okio)

    api(libs.injekt.core)

    api(libs.preferencektx)

    implementation(androidx.corektx)

    // JavaScript engine
    implementation(libs.bundles.js.engine)
}
