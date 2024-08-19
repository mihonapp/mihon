import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.ktlint.core.get().version)
            .editorConfigOverride(mapOf(
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                "ktlint_standard_class-signature" to "disabled",
                "ktlint_standard_discouraged-comment-location" to "disabled",
                "ktlint_standard_function-expression-body" to "disabled",
                "ktlint_standard_function-signature" to "disabled",
            ))
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
