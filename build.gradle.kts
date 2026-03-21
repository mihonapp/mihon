buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
    }
}

plugins {
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.moko.resources) apply false
    alias(libs.plugins.sqldelight) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
