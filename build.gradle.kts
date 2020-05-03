plugins {
    id("com.android.application") version BuildPluginsVersion.AGP apply false
    id("com.android.library") version BuildPluginsVersion.AGP apply false
    kotlin("android") version BuildPluginsVersion.KOTLIN apply false
    id("org.jlleitschuh.gradle.ktlint") version BuildPluginsVersion.KTLINT
    id("com.github.ben-manes.versions") version BuildPluginsVersion.VERSIONS_PLUGIN
}

allprojects {
    repositories {
        google()
        maven { setUrl("https://www.jitpack.io") }
        maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { setUrl("https://dl.bintray.com/ibm-cloud-sdks/ibm-cloud-sdk-repo") }
        maven { setUrl("https://plugins.gradle.org/m2/") }
        jcenter()
    }
}

subprojects {
    apply {
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    ktlint {
        debug.set(false)
        version.set(Versions.KTLINT)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}

buildscript {
    dependencies {
        classpath("com.github.ben-manes:gradle-versions-plugin:0.28.0")
        classpath("com.github.zellius:android-shortcut-gradle-plugin:0.1.2")
        classpath("com.google.gms:google-services:4.3.3")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.2")
        // Realm (EH)
        classpath("io.realm:realm-gradle-plugin:5.13.1")

        // Firebase (EH)
        classpath("io.fabric.tools:gradle:1.31.0")
    }
    repositories {
        google()
        jcenter()
        maven { setUrl("https://maven.fabric.io/public") }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
