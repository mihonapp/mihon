package mihon.buildlogic.tasks

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

private val emptyResourcesElement = "<resources>\\s*</resources>|<resources/>".toRegex()

fun Project.getLocalesConfigTask(): TaskProvider<Task> {
    return tasks.register("generateLocalesConfig") {
        val locales = fileTree("$projectDir/src/commonMain/moko-resources/")
            .matching { include("**/strings.xml") }
            .filterNot { it.readText().contains(emptyResourcesElement) }
            .map {
                it.parentFile.name
                    .replace("base", "en")
                    .replace("-r", "-")
                    .replace("+", "-")
                    .takeIf(String::isNotBlank) ?: "en"
            }
            .sorted()
            .joinToString("\n") { "|   <locale android:name=\"$it\"/>" }

        val content = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
        $locales
        |</locale-config>
        """.trimMargin()

        file("$projectDir/src/androidMain/res/xml/locales_config.xml").apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }
}

