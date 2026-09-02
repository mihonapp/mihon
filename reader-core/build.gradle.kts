plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
}

kotlin { jvmToolchain(mihonx.versions.java.get().toInt()) }

dependencies {
    implementation(libs.commonsCompress)
    implementation(libs.junrar)
    implementation(libs.jna.platform)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.xz)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
