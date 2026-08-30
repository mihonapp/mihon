package mihon.desktop

import androidx.compose.ui.window.application
import mihon.desktop.ui.MihonDesktopApp
import java.nio.file.Path

fun main(args: Array<String>) {
    val command = ProcessHandle.current().info().command().orElse(null)
    val executableDirectory = command?.let(Path::of)?.parent
        ?: Path.of(System.getProperty("user.dir"))
    val runtime = DesktopRuntimeFactory.create(args, System.getenv(), executableDirectory)

    if (runtime.smokeTest) {
        println("MIHON_DESKTOP_SMOKE_OK ${runtime.directories.root}")
        return
    }

    application {
        MihonDesktopApp(runtime)
    }
}
