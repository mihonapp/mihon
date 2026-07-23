package mihon.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class ReplaceShortcutsPlaceholderTask : DefaultTask() {

    @get:Input
    abstract val applicationId: Property<String>

    @get:InputFile
    abstract val shortcutsFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        val content = shortcutsFile.asFile.get()
            .readText()
            .replace($$"${applicationId}", applicationId.get())

        outputDir.get().file("xml/shortcuts.xml").asFile.apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }
}
