package mihon.gradle.tasks

import com.android.ide.common.vectordrawable.Svg2Vector
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream

abstract class GenerateVectorDrawablesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val svgFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        val drawableDir = outputDir.get().dir("drawable").asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        svgFiles.forEach { svg ->
            val output = ByteArrayOutputStream()
            val error = Svg2Vector.parseSvgToXml(svg.toPath(), output)
            check(error.isEmpty()) { "${svg.name}: $error" }

            val folder = svg.parentFile.name
            var content = output.toString(Charsets.UTF_8).replace(blackFill, "@android:color/white")
            if (folder.startsWith("autoMirrored")) {
                content = content.replaceFirst("<vector ", "<vector android:autoMirrored=\"true\" ")
            }

            val name = "${folder.replace(upperCase) { "_" + it.value.lowercase() }}_${svg.nameWithoutExtension}"
            drawableDir.resolve("$name.xml").writeText(content)
        }
    }

    private companion object {
        val blackFill = "#FF000000"
        val upperCase = "[A-Z]".toRegex()
    }
}
