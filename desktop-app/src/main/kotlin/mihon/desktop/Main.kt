package mihon.desktop

import androidx.compose.ui.window.application
import mihon.desktop.cli.CommandLineException
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandRunner
import mihon.desktop.ui.MihonDesktopApp
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val command = ProcessHandle.current().info().command().orElse(null)
    val executableDirectory = command?.let(Path::of)?.parent
        ?: Path.of(System.getProperty("user.dir"))
    val exitCode = try {
        val runtime = DesktopRuntimeFactory.create(args, System.getenv(), executableDirectory)
        executeDesktopRuntime(
            runtime = runtime,
            runCommand = { activeRuntime, desktopCommand ->
                DesktopCommandRunner(activeRuntime, System.out).run(desktopCommand)
            },
            launchUi = { activeRuntime ->
                application(exitProcessOnExit = false) {
                    MihonDesktopApp(activeRuntime)
                }
            },
        )
    } catch (error: CommandLineException) {
        DesktopCommandRunner.writeCommandLineError(System.out, error)
        error.exitCode
    } catch (_: Throwable) {
        DesktopCommandRunner.writeStartupFailure(System.out)
        1
    }
    exitProcess(exitCode)
}

internal fun executeDesktopRuntime(
    runtime: DesktopRuntime,
    runCommand: (DesktopRuntime, DesktopCommand) -> Int,
    launchUi: (DesktopRuntime) -> Unit,
): Int = runtime.use { activeRuntime ->
    when (val command = activeRuntime.command) {
        DesktopCommand.LaunchUi -> {
            launchUi(activeRuntime)
            0
        }
        else -> runCommand(activeRuntime, command)
    }
}
