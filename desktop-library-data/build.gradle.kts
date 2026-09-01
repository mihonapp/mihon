plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(mihonx.plugins.spotless)
}

kotlin { jvmToolchain(mihonx.versions.java.get().toInt()) }

dependencies {
    implementation(libs.sqldelight.jdbcDriver)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

sqldelight {
    databases {
        create("DesktopLibraryDatabase") {
            packageName.set("mihon.desktop.library.db")
            dialect(libs.sqldelight.sqliteDialect338)
            schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            verifyMigrations.set(true)
        }
    }
}
