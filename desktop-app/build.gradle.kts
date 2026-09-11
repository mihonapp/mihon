import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.okhttp.core)
    implementation("com.github.ThexXTURBOXx.dex2jar:dex-translator:v64")
    implementation("com.github.ThexXTURBOXx.dex2jar:dex-tools:v64")
    implementation("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(libs.bundles.test)
    testImplementation(libs.commonsCompress)
    testImplementation(compose.desktop.uiTestJUnit4)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val readerCodecVersion = "7.1.2-31"
val readerCodecArchiveName = "ImageMagick-$readerCodecVersion-portable-Q8-x64.7z"
val readerCodecSha256 = "4eb7914050902c52bf388bae188fdbdb04154ca89d105b2f6200a29cb774241b"
val readerCodecArchive = layout.buildDirectory.file("reader-codec/downloads/$readerCodecArchiveName")
val readerCodecExtractDir = layout.buildDirectory.dir("reader-codec/extracted")
val readerCodecResourcesRoot = layout.buildDirectory.dir("reader-codec/app-resources")
val readerHeicFixture = layout.buildDirectory.file("reader-codec/fixtures/libheif-example.heic")
val readerHeicFixtureSha256 = "7f8b363e4936c0666a25f64f3a92fda10bd8e5453be4592530b65a55dd98f3f2"

val downloadReaderHeicFixture by tasks.registering {
    outputs.file(readerHeicFixture)
    doLast {
        val target = readerHeicFixture.get().asFile
        target.parentFile.mkdirs()
        if (!target.isFile) {
            URI(
                "https://raw.githubusercontent.com/strukturag/libheif/" +
                    "08075aebcc0d9bf7d35f900c36114b1b6e90ed7d/examples/example.heic",
            ).toURL().openStream().use { input -> target.outputStream().use(input::copyTo) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val actual = target.inputStream().use { input -> digest.digest(input.readBytes()) }
            .joinToString("") { "%02x".format(it) }
        check(actual == readerHeicFixtureSha256) {
            "HEIC fixture SHA-256 mismatch: expected $readerHeicFixtureSha256, got $actual"
        }
    }
}

val downloadReaderCodec by tasks.registering {
    outputs.file(readerCodecArchive)
    doLast {
        val target = readerCodecArchive.get().asFile
        target.parentFile.mkdirs()
        if (!target.isFile) {
            val temporary = File(target.parentFile, "${target.name}.part")
            URI(
                "https://github.com/ImageMagick/ImageMagick/releases/download/$readerCodecVersion/$readerCodecArchiveName",
            ).toURL().openStream().use { input -> temporary.outputStream().use(input::copyTo) }
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val actual = target.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        check(actual == readerCodecSha256) {
            "Reader codec SHA-256 mismatch: expected $readerCodecSha256, got $actual"
        }
    }
}

val extractReaderCodec by tasks.registering(Exec::class) {
    dependsOn(downloadReaderCodec)
    inputs.file(readerCodecArchive)
    outputs.dir(readerCodecExtractDir)
    doFirst {
        delete(readerCodecExtractDir)
        readerCodecExtractDir.get().asFile.mkdirs()
    }
    commandLine(
        "tar",
        "-xf",
        readerCodecArchive.get().asFile.absolutePath,
        "-C",
        readerCodecExtractDir.get().asFile.absolutePath,
    )
}

val stageReaderCodec by tasks.registering(Sync::class) {
    dependsOn(extractReaderCodec)
    from(readerCodecExtractDir) {
        include("magick.exe", "*.xml", "*.icc", "LICENSE.txt", "NOTICE.txt")
    }
    from("src/main/resources/codec/THIRD-PARTY-NOTICES.txt")
    into(readerCodecResourcesRoot.map { it.dir("windows/codec") })
}

tasks.test {
    dependsOn(stageReaderCodec, downloadReaderHeicFixture)
    systemProperty(
        "mihon.reader.codec",
        readerCodecResourcesRoot.get().file("windows/codec/magick.exe").asFile.absolutePath,
    )
    systemProperty("mihon.reader.heicFixture", readerHeicFixture.get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "mihon.desktop.MainKt"
        javaHome = desktopJavaHome

        nativeDistributions {
            appResourcesRootDir.set(readerCodecResourcesRoot)
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "MihonW"
            packageVersion = "0.1.3"
            description = "Mihon manga reader for Windows"
            vendor = "Mihon W"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.desktop", "java.logging", "java.prefs", "java.sql", "java.instrument", "jdk.unsupported")

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                console = false
                dirChooser = true
                perUserInstall = true
                menu = true
                shortcut = true
                menuGroup = "Mihon W"
                upgradeUuid = "07E02BEA-9179-4E54-A1AF-CFC185C91398"
            }
        }
    }
}

tasks.configureEach {
    if (
        name in setOf(
            "prepareAppResources",
            "createDistributable",
            "packageExe",
            "packageMsi",
            "packageDistributionForCurrentOS",
        )
    ) {
        dependsOn(stageReaderCodec)
    }
}

val packagePortableZip by tasks.registering(Zip::class) {
    dependsOn("createDistributable")
    group = "compose desktop"
    description = "Packages the portable distribution as a standalone ZIP archive"

    archiveBaseName.set("MihonW")
    archiveClassifier.set("windows-x64-portable")
    archiveVersion.set("0.1.3")
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
