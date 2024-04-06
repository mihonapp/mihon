package mihon.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForAndroidx
import org.gradle.accessors.dm.LibrariesForCompose
import org.gradle.accessors.dm.LibrariesForKotlinx
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val Project.androidx get() = the<LibrariesForAndroidx>()
val Project.compose get() = the<LibrariesForCompose>()
val Project.kotlinx get() = the<LibrariesForKotlinx>()
val Project.libs get() = the<LibrariesForLibs>()

internal fun Project.configureAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = AndroidConfig.COMPILE_SDK

        defaultConfig {
            minSdk = AndroidConfig.MIN_SDK
            ndk {
                version = AndroidConfig.NDK
            }
        }

        compileOptions {
            sourceCompatibility = AndroidConfig.JavaVersion
            targetCompatibility = AndroidConfig.JavaVersion
            isCoreLibraryDesugaringEnabled = true
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = AndroidConfig.JavaVersion.toString()
            // freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
            // freeCompilerArgs += "-Xcontext-receivers"

            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            // val warningsAsErrors: String? by project
            // allWarningsAsErrors = warningsAsErrors.toBoolean()
        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.desugar)
    }
}

internal fun Project.configureCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        buildFeatures {
            compose = true
        }

        composeOptions {
            kotlinCompilerExtensionVersion = compose.versions.compiler.get()
        }

        dependencies {
            "implementation"(platform(compose.bom))
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += buildComposeMetricsParameters()

            // Enable experimental compiler opts
            // https://developer.android.com/jetpack/androidx/releases/compose-compiler#1.5.9
            freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:nonSkippingGroupOptimization=true",
            )
        }
    }
}

private fun Project.buildComposeMetricsParameters(): List<String> {
    val rootProjectDir = rootProject.layout.buildDirectory.asFile.get()
    val relativePath = projectDir.relativeTo(rootDir)

    val enableMetrics = project.providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
    val enableReports = project.providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

    return listOfNotNull(
        ("metricsDestination" to "compose-metrics").takeIf { enableMetrics },
        ("reportsDestination" to "compose-reports").takeIf { enableReports },
    ).flatMap { (flag, dirName) ->
        val buildDirPath = rootProjectDir.resolve(dirName).resolve(relativePath).absolutePath
        listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:$flag=$buildDirPath"
        )
    }
}

internal fun Project.configureTest() {
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}
