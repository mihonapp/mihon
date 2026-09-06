import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
}

val desktopJavaHome = extensions
    .getByType<JavaToolchainService>()
    .launcherFor {
        languageVersion.set(JavaLanguageVersion.of(mihonx.versions.java.get().toInt()))
    }.get()
    .metadata
    .installationPath
    .asFile
    .absolutePath

dependencies {
    implementation(project(":desktop-library-data"))
    implementation(project(":reader-core"))
    implementation(project(":extension-sdk"))
    implementation(project(":extension-host"))
    implementation(libs.jna.platform)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)

    testImplementation(libs.bundles.test)
    testImplementation(libs.commonsCompress)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.kotlinx.serialization.protobuf)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "mihon.desktop.MainKt"
        javaHome = desktopJavaHome

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "MihonW"
            packageVersion = "0.1.0"
            description = "Mihon manga reader for Windows"
            vendor = "Mihon W"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.desktop", "java.logging", "java.prefs", "java.sql")

            windows {
                console = true
                perUserInstall = true
                dirChooser = true
                menuGroup = "Mihon W"
                upgradeUuid = "07E02BEA-9179-4E54-A1AF-CFC185C91398"
            }
        }
    }
}

val packagePortableZip by tasks.registering(Zip::class) {
    dependsOn("createDistributable")
    group = "compose desktop"
    description = "Packages the portable distribution as a standalone ZIP archive"

    archiveBaseName.set("MihonW")
    archiveClassifier.set("windows-x64-portable")
    archiveVersion.set("0.1.0")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))

    from(layout.buildDirectory.dir("compose/binaries/main/app/MihonW")) {
        into("MihonW")
    }

    val markerFile = layout.buildDirectory.file("compose/tmp/portable/.portable")
    doFirst {
        val file = markerFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("")
    }
    from(markerFile) {
        into("MihonW")
    }

    from(rootProject.file("scripts/MihonUpdater.ps1")) {
        into("MihonW")
    }
}
