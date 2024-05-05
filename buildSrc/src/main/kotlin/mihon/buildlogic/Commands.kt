package mihon.buildlogic

import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Git is needed in your system PATH for these commands to work.
// If it's not installed, you can return a random value as a workaround
fun Project.getCommitCount(): String {
    return runCommand("git rev-list --count HEAD")
    // return "1"
}

fun Project.getGitSha(): String {
    return runCommand("git rev-parse --short HEAD")
    // return "1"
}

private val BUILD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

@Suppress("UnusedReceiverParameter")
fun Project.getBuildTime(): String {
    return LocalDateTime.now(ZoneOffset.UTC).format(BUILD_TIME_FORMATTER)
}

private fun Project.runCommand(command: String): String {
    val byteOut = ByteArrayOutputStream()
    exec {
        commandLine = command.split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}
