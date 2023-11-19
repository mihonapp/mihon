import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.TaskContainerScope

private val emptyResourcesElement = "<resources>\\s*</resources>|<resources/>".toRegex()

fun TaskContainerScope.registerLocalesConfigTask(project: Project): TaskProvider<Task> {
    return with(project) {
        register("generateLocalesConfig") {
            val languages = fileTree("$projectDir/src/commonMain/resources/MR/")
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
                .joinToString(separator = "\n") {
                    "   <locale android:name=\"$it\"/>"
                }

            val content = """
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
$languages
</locale-config>
    """.trimIndent()

            val localeFile = file("$projectDir/src/androidMain/res/xml/locales_config.xml")
            localeFile.parentFile.mkdirs()
            localeFile.writeText(content)
        }
    }
}

