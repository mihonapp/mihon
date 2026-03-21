package mihon.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateLocalesConfigTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        val locales = project.fileTree("src/commonMain/moko-resources") {
            matching { include("**/strings.xml") }
        }
            .asSequence()
            .filterNot { it.readText().contains(emptyResourcesElement) }
            .map {
                it.parentFile.name
                    .replace("base", "en")
                    .replace("-r", "-")
                    .replace("+", "-")
            }
            .distinct()
            .sorted()
            .joinToString("\n") { "|   <locale android:name=\"$it\"/>" }

        val content = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
        $locales
        |</locale-config>
        """.trimMargin()

        outputDir.get().file("xml/locales_config.xml").asFile.apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }

    companion object {
        private val emptyResourcesElement = "<resources>\\s*</resources>|<resources\\s*/>".toRegex()
    }
}
