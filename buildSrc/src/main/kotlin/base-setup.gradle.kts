import gradle.kotlin.dsl.accessors._624aae704a5c30b505ab3598db099943.coreLibraryDesugaring
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("tachiyomi.lint")
}


val libs = the<LibrariesForLibs>()
dependencies {
    coreLibraryDesugaring(libs.desugar)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
