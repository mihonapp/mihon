import com.android.build.api.variant.LibraryAndroidComponentsExtension
import mihon.gradle.tasks.BuildRustTask

plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "app.komikita.core.nativecore"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

val rustEnabled = providers.gradleProperty("enable-rust").map(String::toBoolean).orElse(true)
if (rustEnabled.get()) {
    val components = extensions.getByType<LibraryAndroidComponentsExtension>()
    val buildRust = tasks.register<BuildRustTask>("buildRust") {
        sourceDirectory.set(layout.projectDirectory.dir("../../native"))
        outputDirectory.set(layout.buildDirectory.dir("generated/rustJniLibs"))
        targetDirectory.set(layout.buildDirectory.dir("rust-target"))
        val customNdk = providers.gradleProperty("rust-ndk-path")
        if (customNdk.isPresent) {
            ndkDirectory.fileValue(file(customNdk.get()))
        } else {
            ndkDirectory.set(components.sdkComponents.ndkDirectory)
        }
    }
    components.onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildRust, BuildRustTask::outputDirectory)
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
}
