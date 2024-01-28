import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.android.test")
    id("tachiyomi.lint")
    kotlin("android")
}

android {
    namespace = "tachiyomi.macrobenchmark"

    compileSdk = AndroidConfig.compileSdk
    defaultConfig {
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "BaselineProfile"

        ndk {
            version = AndroidConfig.ndk
        }
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It's signed with a debug key
        // for easy local/CI testing.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks.add("release")
        }
    }

    compileOptions {
        sourceCompatibility = AndroidConfig.javaVersion
        targetCompatibility = AndroidConfig.javaVersion
        isCoreLibraryDesugaringEnabled = true
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

dependencies {
    implementation(androidx.test.ext)
    implementation(androidx.test.espresso.core)
    implementation(androidx.test.uiautomator)
    implementation(androidx.benchmark.macro)
    coreLibraryDesugaring(libs.desugar)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
