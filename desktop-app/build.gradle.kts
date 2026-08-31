import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
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
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(libs.bundles.test)
    testImplementation(compose.desktop.uiTestJUnit4)
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
            modules("java.desktop", "java.logging", "java.prefs")

            windows {
                perUserInstall = true
                dirChooser = true
                menuGroup = "Mihon W"
                upgradeUuid = "07E02BEA-9179-4E54-A1AF-CFC185C91398"
            }
        }
    }
}
