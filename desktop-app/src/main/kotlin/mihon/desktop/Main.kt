package mihon.desktop

import androidx.compose.ui.window.application
import mihon.desktop.cli.CommandLineException
import mihon.desktop.cli.DesktopCommand
import mihon.desktop.cli.DesktopCommandParser
import mihon.desktop.cli.DesktopCommandRunner
import mihon.desktop.platform.DesktopProfileDirectories
import mihon.desktop.platform.DesktopProfileLock
import mihon.desktop.platform.ProfileInUseException
import mihon.desktop.ui.MihonDesktopApp
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if ("--extension-host" in args) {
        val hostArgs = args.filter { it != "--extension-host" }.toTypedArray()
        mihon.extension.host.main(hostArgs)
        return
    }
    val command = ProcessHandle.current().info().command().orElse(null)
    val executableDirectory = command?.let(Path::of)?.parent
        ?: Path.of(System.getProperty("user.dir"))
    val exitCode = try {
        DesktopCommandParser.parse(args)
        val profile = DesktopProfileDirectories.resolve(args, System.getenv(), executableDirectory)
        DesktopProfileLock.acquire(profile.root).use {
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
        }
    } catch (_: ProfileInUseException) {
        System.out.println("""{"command":"startup","status":"SKIPPED","category":"PROFILE_IN_USE"}""")
        if ("--background-update" in args || "--background-backup" in args) 0 else 75
    } catch (error: CommandLineException) {
        DesktopCommandRunner.writeCommandLineError(System.out, error)
        error.exitCode
    } catch (e: Throwable) {
        e.printStackTrace(System.err)
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
