import com.android.build.api.variant.HasUnitTest

plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

val sqliteVersion = libs.versions.androidx.sqlite.get()

android {
    namespace = "tachiyomi.data"

    sqldelight {
        databases {
            create("Database") {
                packageName.set("tachiyomi.data")
                dialect(libs.sqldelight.sqliteDialect338)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
                generateAsync.set(true)
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        (variant as? HasUnitTest)?.unitTest?.let { unitTest ->
            with(unitTest.runtimeConfiguration.resolutionStrategy.dependencySubstitution) {
                substitute(module("androidx.sqlite:sqlite-bundled:$sqliteVersion"))
                    .using(module("androidx.sqlite:sqlite-bundled-jvm:$sqliteVersion"))
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.metro)

    implementation(libs.metro.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.bundles.sqldelight)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
