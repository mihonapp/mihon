package mihon.desktop.extension

import kotlinx.coroutines.runBlocking
import mihon.extension.validator.ExtensionPackageValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class PackagedExtensionHostTest {
    @Test
    fun `packaged hosts register every installed source and expose preferences`(@TempDir directory: Path) =
        runBlocking {
            val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
            val packagesDirectory = System.getenv("MIHON_EXTENSION_SMOKE_DIRECTORY")?.let(::File)
            assumeTrue(executable?.isFile == true, "Supply the actual packaged executable")
            assumeTrue(packagesDirectory?.isDirectory == true, "Supply installed extension packages")
            val packages = requireNotNull(packagesDirectory).walkTopDown()
                .filter { it.isFile && it.extension.equals("mext", ignoreCase = true) }.toList()
            assertTrue(packages.isNotEmpty())
            val manager = WindowsExtensionProcessManager(
                workingDirectory = directory.resolve("installed packages 中文").toFile(),
                customCommand = listOf(requireNotNull(executable).absolutePath, "--extension-host", "--stdio"),
            )
            try {
                val expectedIds = packages.flatMap { file ->
                    val manifest = ExtensionPackageValidator.validatePackage(file)
                    val expected = manifest.sources.map { it.id }.toSet()
                    assertEquals(expected, manager.loadExtension(file).map { it.id }.toSet())
                    expected.forEach { manager.getSourcePreferences(it) }
                    println("PACKAGED_EXTENSION_LOADED ${manifest.id} sources=${expected.size}")
                    expected
                }.toSet()
                assertEquals(expectedIds, manager.getSources().map { it.id }.toSet())
            } finally {
                manager.close()
            }
        }

    @Test
    fun `packaged launcher authenticates without spawning another process and restarts`(@TempDir directory: Path) =
        runBlocking {
            val executable = System.getenv("MIHON_PACKAGED_EXE")?.let(::File)
            assumeTrue(executable?.isFile == true, "Supply the actual packaged executable")
            val manager = WindowsExtensionProcessManager(
                workingDirectory = directory.resolve("packaged host 中文").toFile(),
                customCommand = listOf(requireNotNull(executable).absolutePath, "--extension-host", "--stdio"),
            )
            try {
                repeat(2) {
                    manager.start()
                    assertEquals(HostProcessState.RUNNING, manager.state)
                    assertTrue(manager.ping())
                    assertTrue(manager.getSources().isEmpty())
                    val process = WindowsExtensionProcessManager::class.java.getDeclaredField("process")
                        .apply { isAccessible = true }.get(manager) as Process
                    ProcessHandle.of(process.pid()).orElseThrow().descendants().use {
                        assertEquals(0L, it.count())
                    }
                    if (it == 0) manager.restart()
                }
            } finally {
                manager.close()
            }
        }
}
