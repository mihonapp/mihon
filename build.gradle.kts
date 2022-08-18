buildscript {
    dependencies {
        // Pinning to older version of R8 due to weird forced optimizations in newer versions in
        // version bundled with AGP
        // https://mvnrepository.com/artifact/com.android.tools/r8?repo=google
        classpath("com.android.tools:r8:3.1.66")
        classpath(libs.android.shortcut.gradle)
        classpath(libs.google.services.gradle)
        classpath(libs.aboutLibraries.gradle)
        classpath(kotlinx.serialization.gradle)
        classpath(libs.sqldelight.gradle)
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


        disabledRules = arrayOf(
            "experimental:argument-list-wrapping", // Doesn't play well with Android Studio
            "filename", // Often broken to give a more general name
        )
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
