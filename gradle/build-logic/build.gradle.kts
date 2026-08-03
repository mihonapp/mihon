plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samWithReceiver)
    alias(libs.plugins.spotless)
    `java-gradle-plugin`
}

// Configuration should be synced with [/gradle/build-logic/src/main/kotlin/PluginSpotless.kt]
val ktlintVersion = libs.ktlint.bom.get().version
val editorConfigFile = rootProject.file("../../.editorconfig")
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfigFile)
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.kts")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfigFile)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(libs.android.gradle)
    compileOnly(libs.kotlin.compose.compiler.gradle)
    compileOnly(libs.kotlin.gradle)
    implementation(libs.spotless.gradle)
    implementation(libs.tapmoc.gradle)

    // These allow us to reference the dependency catalog inside our compiled plugins
    compileOnly(files(libs::class.java.superclass.protectionDomain.codeSource.location))
    compileOnly(files(mihonx::class.java.superclass.protectionDomain.codeSource.location))
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

gradlePlugin {
    plugins {
        register("android-application") {
            id = mihonx.plugins.android.application.get().pluginId
            implementationClass = "PluginAndroidApplication"
        }
        register("android-base") {
            id = mihonx.plugins.android.base.get().pluginId
            implementationClass = "PluginAndroidBase"
        }
        register("android-library") {
            id = mihonx.plugins.android.library.get().pluginId
            implementationClass = "PluginAndroidLibrary"
        }
        register("android-test") {
            id = mihonx.plugins.android.test.get().pluginId
            implementationClass = "PluginAndroidTest"
        }
        register("compose-android") {
            id = mihonx.plugins.compose.get().pluginId
            implementationClass = "PluginComposeAndroid"
        }
        register("kotlin-multiplatform") {
            id = mihonx.plugins.kotlin.multiplatform.get().pluginId
            implementationClass = "PluginKotlinMultiplatform"
        }
        register("spotless") {
            id = mihonx.plugins.spotless.get().pluginId
            implementationClass = "PluginSpotless"
        }
    }
}
