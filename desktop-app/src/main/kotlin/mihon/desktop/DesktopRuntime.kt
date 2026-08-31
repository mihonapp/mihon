package mihon.desktop

import mihon.desktop.platform.AppDirectories
import mihon.desktop.platform.AppDirectoryResolver
import mihon.desktop.platform.DistributionMode
import mihon.desktop.preferences.DesktopPreferenceStore
import java.nio.file.Path

data class DesktopRuntime(
    val directories: AppDirectories,
    val preferences: DesktopPreferenceStore,
    val smokeTest: Boolean,
)

object DesktopRuntimeFactory {
    fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
    ): DesktopRuntime {
        val explicitRoot = args.firstOrNull { it.startsWith("--data-dir=") }
            ?.substringAfter('=')
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        val mode = if ("--portable" in args) DistributionMode.Portable else DistributionMode.Installed
        val appData = environment["APPDATA"]
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        val directories = AppDirectoryResolver(appData, executableDirectory)
            .resolve(mode, explicitRoot)
            .create()
        return DesktopRuntime(
            directories = directories,
            preferences = DesktopPreferenceStore(directories.root.resolve("preferences.properties")),
            smokeTest = "--smoke-test" in args,
        )
    }
}
