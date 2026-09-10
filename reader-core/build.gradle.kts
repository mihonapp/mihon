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
    implementation(libs.twelveMonkeys.bmp)
    implementation(libs.twelveMonkeys.core)
    implementation(libs.twelveMonkeys.jpeg)
    implementation(libs.twelveMonkeys.metadata)
    implementation(libs.twelveMonkeys.tiff)
    implementation(libs.twelveMonkeys.webp)
    implementation(libs.xz)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform { excludeTags("extreme-image") }
}

tasks.register<Test>("extremeImageTest") {
    description = "Decodes extreme-size images under a constrained 384 MiB heap."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("extreme-image") }
    maxHeapSize = "384m"
    maxParallelForks = 1
}
