import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(mihonx.plugins.spotless)
    application
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}
val browserJava = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.JETBRAINS)
}
val jcefApi by tasks.registering(Jar::class) {
    archiveFileName.set("jcef-compile-api.jar")
    from(browserJava.map { zipTree(it.metadata.installationPath.file("jmods/jcef.jmod")) }) {
        include("classes/**")
        exclude("classes/META-INF/MANIFEST.MF", "classes/module-info.class")
        eachFile { path = path.removePrefix("classes/") }
        includeEmptyDirs = false
    }
}
application {
    mainClass.set("mihon.webview.MainKt")
    applicationDefaultJvmArgs = listOf("--add-modules=jcef", "--add-exports=java.desktop/sun.awt=ALL-UNNAMED")
}
dependencies {
    compileOnly(files(jcefApi))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.test { useJUnitPlatform() }
// This runtime belongs exclusively to the browser helper; the desktop application's runtime is unchanged.
tasks.register<Copy>("stageBrowserRuntime") {
    doFirst {
        val release = browserJava.get().metadata.installationPath.file("release").asFile.readText()
        check(release.contains("JBRSDK-21.0.10+1-1163.110-jcef")) {
            "Use the validated JBRSDK 21.0.10+1-1163.110-jcef browser runtime"
        }
    }
    from(browserJava.map { it.metadata.installationPath }) {
        exclude("jmods/**", "include/**", "lib/src.zip")
    }
    into(layout.buildDirectory.dir("browser-runtime"))
}

tasks.register<Zip>("browserBundle") {
    dependsOn("installDist", "stageBrowserRuntime")
    archiveFileName.set("mihonw-browser-host-jbr21-win-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("install/desktop-webview-host")) { into("app") }
    from(layout.buildDirectory.dir("browser-runtime")) { into("runtime") }
}
