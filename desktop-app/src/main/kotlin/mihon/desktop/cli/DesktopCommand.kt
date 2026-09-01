package mihon.desktop.cli

import java.nio.file.Path

sealed interface DesktopCommand {
    data object LaunchUi : DesktopCommand
    data object FoundationSmoke : DesktopCommand
    data class ImportBackup(val path: Path) : DesktopCommand
    data class ImportLocal(val path: Path) : DesktopCommand
    data object ListLibraryJson : DesktopCommand
}

class CommandLineException(
    message: String,
    val argument: String? = null,
    cause: Throwable? = null,
    val exitCode: Int = 2,
) : IllegalArgumentException(message, cause)

object DesktopCommandParser {
    fun parse(args: Array<String>): DesktopCommand {
        val commands = buildList {
            args.forEach { argument ->
                when {
                    argument == "--portable" -> Unit
                    argument.startsWith("--data-dir=") -> Unit
                    argument == "--smoke-test" -> add(DesktopCommand.FoundationSmoke)
                    argument == "--list-library-json" -> add(DesktopCommand.ListLibraryJson)
                    argument.startsWith("--import-backup=") -> add(
                        DesktopCommand.ImportBackup(parsePath(argument, "--import-backup=")),
                    )
                    argument.startsWith("--import-local=") -> add(
                        DesktopCommand.ImportLocal(parsePath(argument, "--import-local=")),
                    )
                    else -> throw CommandLineException("Unknown or incomplete argument", safeArgument(argument))
                }
            }
        }
        if (commands.size > 1) {
            throw CommandLineException("Only one headless command may be specified")
        }
        return commands.singleOrNull() ?: DesktopCommand.LaunchUi
    }

    private fun parsePath(argument: String, prefix: String): Path {
        val value = argument.removePrefix(prefix)
        if (value.isBlank()) throw CommandLineException("${prefix.removeSuffix("=")} requires a non-blank path", prefix)
        return try {
            Path.of(value)
        } catch (error: RuntimeException) {
            throw CommandLineException("${prefix.removeSuffix("=")} contains an invalid path", prefix, error)
        }
    }

    private fun safeArgument(argument: String): String? =
        argument.takeIf { it.startsWith("--") }?.substringBefore('=')
}
