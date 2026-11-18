plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

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

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // The app's own driver only runs on a device, so the queries are tested against an in-memory JDBC database
    testImplementation("app.cash.sqldelight:sqlite-driver:${libs.versions.sqldelight.asProvider().get()}")
    testRuntimeOnly(libs.junit.platform.launcher)
}
