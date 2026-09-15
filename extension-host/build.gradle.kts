plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(mihonx.plugins.spotless)
    application
}

kotlin { jvmToolchain(mihonx.versions.java.get().toInt()) }

application {
    mainClass.set("mihon.extension.host.MainKt")
}

dependencies {
    implementation(project(":extension-sdk"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.zstd)
    implementation(libs.okio)
    implementation(libs.twelveMonkeys.webp)
    implementation(libs.jsoup)
    implementation(libs.injekt)
    implementation(libs.rxJava)
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.ow2.asm:asm:9.9.1")

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Exercise the same main-loop mode selected by the extension-host entry point.
    systemProperty("mihon.extension.host", "true")
}
