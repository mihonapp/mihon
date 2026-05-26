buildscript {
    dependencies {
        classpath(libs.kotlin.gradle)
    }
}

plugins {
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.moko.resources) apply false
    alias(libs.plugins.sqldelight) apply false

    alias(mihonx.plugins.spotless)
}

val buildLogic: IncludedBuild = gradle.includedBuild("build-logic")
tasks {
    listOf("clean", "spotlessApply", "spotlessCheck").forEach { task ->
        named(task) {
            dependsOn(buildLogic.task(":$task"))
        }
    }
}

val jetbrainsComposeVersion = "1.12.0-alpha01"
val guavaVersion = "33.6.0-android"
val mokoGraphicsVersion = "0.10.1"
val windowManagerVersion = "1.6.0-alpha03"
val emoji2Version = "1.6.0"
val collectionVersion = "1.6.0"
subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group.startsWith("org.jetbrains.compose")) {
                useVersion(jetbrainsComposeVersion)
            }
            if (requested.group == "com.google.guava" && requested.name == "guava") {
                useVersion(guavaVersion)
            }
            if (requested.group == "dev.icerock.moko" && requested.name == "graphics") {
                useVersion(mokoGraphicsVersion)
            }
            if (requested.group == "androidx.window") {
                useVersion(windowManagerVersion)
            }
            if (requested.group == "androidx.emoji2") {
                useVersion(emoji2Version)
            }
            if (requested.group == "androidx.collection") {
                useVersion(collectionVersion)
            }
        }
    }
}
