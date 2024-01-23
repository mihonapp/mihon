
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<LibrariesForLibs>()
dependencies {
    detektPlugins(libs.detekt.rules.formatting)
    detektPlugins(libs.detekt.rules.compose)
}

private val projectSource = file("$rootDir")
private val configFile = files("$rootDir/config/detekt/detekt.yml")
private val baselineFile = file("$rootDir/config/detekt/baseline.xml")
private val kotlinFiles = "**/*.kt"
private val resourceFiles = "**/resources/**"
private val buildFiles = "**/build/**"
private val generatedFiles = "**/generated/**"
private val scriptsFiles = "**/*.kts"

detekt {
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
    ignoreFailures = false
    config.setFrom(configFile)
    baseline = file(baselineFile)
    source.from(projectSource)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    include(kotlinFiles)
    exclude(resourceFiles, buildFiles, generatedFiles, scriptsFiles)
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_17.toString()
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = JavaVersion.VERSION_17.toString()
}
