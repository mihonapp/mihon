buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
        classpath(libs.google.services.gradle)
        classpath(libs.aboutLibraries.gradle)
        classpath(kotlinx.serialization.gradle)
        classpath("com.squareup.sqldelight:gradle-plugin:1.5.3")
    }
}

plugins {
    alias(androidx.plugins.application) apply false
    alias(androidx.plugins.library) apply false
    alias(kotlinx.plugins.android) apply false
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.versionsx)
}

subprojects {
    apply<org.jmailen.gradle.kotlinter.KotlinterPlugin>()

    kotlinter {
        experimentalRules = true

        // Doesn't play well with Android Studio
        disabledRules = arrayOf("experimental:argument-list-wrapping")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
