plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tachiyomi.translation"
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
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    api(libs.okhttp.core)
    api(libs.okio)

    implementation(libs.logcat)

    // Local recognition runtime. The model weights themselves are downloaded on demand.
    api("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
