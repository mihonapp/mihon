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
    implementation(libs.okhttp.core)
    implementation(libs.okio)
    implementation(libs.jsoup)
    implementation(libs.injekt)
    implementation(libs.rxJava)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
