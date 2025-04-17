import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

val xmlFormatExclude = buildList(2) {
    add("**/build/**/*.xml")

    projectDir
        .resolve("src/commonMain/moko-resources")
        .takeIf { it.isDirectory }
        ?.let(::fileTree)
        ?.matching { exclude("/base/**") }
        ?.let(::add)
}
    .toTypedArray()

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.ktlint.core.get().version)
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("**/*.xml")
        targetExclude(*xmlFormatExclude)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
