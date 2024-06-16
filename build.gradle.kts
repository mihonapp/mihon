buildscript {
    // https://issuetracker.google.com/344363457
    // TODO: Remove when AGP's bundled R8 is updated
    repositories {
        maven("https://storage.googleapis.com/r8-releases/raw")
    }
    dependencies {
        classpath("com.android.tools:r8:8.5.21")
        classpath(libs.android.shortcut.gradle)
        classpath(libs.google.services.gradle)
        classpath(libs.aboutLibraries.gradle)
        classpath(libs.sqldelight.gradle)
        classpath(libs.moko.gradle)
    }
}

plugins {
    alias(kotlinx.plugins.serialization) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
